package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import br.com.lojaspopular.application.arquivo.ArquivoService;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.encomenda.EncomendaService;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.expedicao.ExpedicaoService;
import br.com.lojaspopular.application.posvenda.PosVendaService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.catalog.enums.ModalidadeProduto;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.repository.CondicaoPagamentoRepository;
import br.com.lojaspopular.domain.encomenda.enums.StatusEncomenda;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.estoque.enums.TipoMovimentacao;
import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.expedicao.enums.StatusEntregaRegistro;
import br.com.lojaspopular.domain.expedicao.enums.StatusMontagemRegistro;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusMontagem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.posvenda.enums.CondicaoFisica;
import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AbrirOcorrenciaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AtualizarEncomendaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.TrocaRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/** Etapa 2: encomenda, inventário, saída com baixa, entrega, montagem e pós-venda. */
@SpringBootTest
@ActiveProfiles("test")
class AtendimentoServiceTest {

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

  @Autowired VendaService vendas;
  @Autowired ExpedicaoService expedicao;
  @Autowired EncomendaService encomendas;
  @Autowired PosVendaService posVenda;
  @Autowired EstoqueService estoque;
  @Autowired ArquivoService arquivos;
  @Autowired ConfiguracaoComercialService config;
  @Autowired UserRepository users;
  @Autowired ClienteRepository clientes;
  @Autowired ProdutoRepository produtos;
  @Autowired PedidoRepository pedidos;
  @Autowired CondicaoPagamentoRepository condicoes;

  User admin;
  User gerente;
  User vendedor;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-a@loja.com", Role.ADMIN);
    gerente = usuario("gerente-a@loja.com", Role.GERENTE);
    vendedor = usuario("vendedor-a@loja.com", Role.VENDEDOR);
    como(admin);
    // Estado conhecido: D05 definido como "não exige pagamento" (valor de TESTE, não recomendação)
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), false);
    if (condicoes.findByFormaAndParcelas(FormaPagamento.PIX, 1).isEmpty()) {
      config.criarCondicao(FormaPagamento.PIX, 1, BigDecimal.ZERO, true);
    } else {
      config.atualizarCondicao(condicoes.findByFormaAndParcelas(FormaPagamento.PIX, 1).get().getId(), BigDecimal.ZERO, true);
    }
  }

  // ------------------------------------------------------------------ D05 e saída

  @Test
  void saidaFicaBloqueadaEnquantoD05EstiverPendente() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, "Equipe 1", null);

    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), null);
    como(gerente);
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave()))
        .isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D05");
    var d = vendas.obter(v.id());
    assertThat(d.acoes().podeRegistrarSaida()).isFalse();
    assertThat(d.acoes().bloqueios().get("saida")).contains("D05");
    assertThat(saldo(p).fisico()).isEqualTo(5);
  }

  @Test
  void d05VerdadeiroExigePagamentoQuitado() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), true);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.TARDE, null, null);
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("pagamento quitado");

    var ped = pedidos.findById(v.id()).orElseThrow();
    ped.setStatusPagamento(StatusPagamento.PAGO);
    pedidos.save(ped);
    expedicao.registrarSaida(v.id(), chave());
    assertThat(vendas.obter(v.id()).statusEntrega()).isEqualTo(StatusEntrega.SAIU);
  }

  @Test
  void saidaBaixaEstoqueUmaUnicaVezEConsomeReserva() {
    var p = produto(5);
    var v = confirmada(p, 2);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, "Equipe 1", "Chamar antes");
    assertThat(vendas.obter(v.id()).statusEntrega()).isEqualTo(StatusEntrega.AGENDADA);
    assertThat(saldo(p).disponivel()).isEqualTo(3);

    String k = chave();
    expedicao.registrarSaida(v.id(), k);
    var d = vendas.obter(v.id());
    assertThat(d.statusEntrega()).isEqualTo(StatusEntrega.SAIU);
    assertThat(d.reservas().get(0).status()).isEqualTo(StatusReserva.CONSUMIDA);
    var s = saldo(p);
    assertThat(s.fisico()).isEqualTo(3);
    assertThat(s.reservado()).isZero();
    assertThat(s.disponivel()).isEqualTo(3);
    assertThat(d.movimentacoes()).hasSize(1);
    assertThat(d.movimentacoes().get(0).tipo()).isEqualTo(TipoMovimentacao.SAIDA_VENDA);
    assertThat(d.movimentacoes().get(0).quantidade()).isEqualTo(-2);
    assertThat(d.movimentacoes().get(0).saldoAnterior()).isEqualTo(5);
    assertThat(d.movimentacoes().get(0).saldoPosterior()).isEqualTo(3);

    // repetição com a mesma chave não baixa de novo; outra chave é recusada
    expedicao.registrarSaida(v.id(), k);
    assertThat(saldo(p).fisico()).isEqualTo(3);
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave())).isInstanceOf(NegocioException.class);
    assertThat(saldo(p).fisico()).isEqualTo(3);
  }

  @Test
  void naoHaEntregaParcial_saidaExigeTodosOsItensReservados() {
    var pronta = produto(5);
    var encomenda = produtoEncomenda();
    como(vendedor);
    var c = cliente();
    var itens = List.of(
        new ItemRequest(pronta.getId(), pronta.getVariacoes().get(0).getId(), 1, ModalidadeItem.PRONTA_ENTREGA),
        new ItemRequest(encomenda.getId(), encomenda.getVariacoes().get(0).getId(), 2, ModalidadeItem.ENCOMENDA));
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null, itens));
    vendas.confirmar(v.id(), chave());

    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.DIA_INTEIRO, null, null);
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não há entrega parcial")
        .hasMessageContaining("encomenda ainda não recebida");
    // nada foi baixado do item pronto
    assertThat(saldo(pronta).fisico()).isEqualTo(5);
    assertThat(vendas.obter(v.id()).acoes().bloqueios().get("saida")).contains("sem reserva");

    // chega a encomenda: agora o pedido completo pode sair
    var enc = encomendas.listar(null).stream().filter(e -> e.pedidoId().equals(v.id())).findFirst().orElseThrow();
    encomendas.receber(enc.id(), 2, chave());
    expedicao.registrarSaida(v.id(), chave());
    assertThat(saldo(pronta).fisico()).isEqualTo(4);
    assertThat(saldoDe(encomenda).fisico()).isZero();
  }

  @Test
  void saldoNegativoEhImpedidoESaidaNaoGravaNada() {
    var p = produto(3);
    var v = confirmada(p, 3);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, null, null);

    // dano de dados simulado: o físico cai abaixo do reservado por fora do sistema
    jdbcSetEstoque(p.getVariacoes().get(0).getId(), 1);

    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("Saldo físico insuficiente");
    var d = vendas.obter(v.id());
    assertThat(d.reservas().get(0).status()).isEqualTo(StatusReserva.ATIVA);
    assertThat(d.statusEntrega()).isEqualTo(StatusEntrega.AGENDADA);
    assertThat(d.movimentacoes()).isEmpty();
  }

  @Test
  void vendedorNaoRegistraSaida() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(vendedor);
    assertThatThrownBy(() -> expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, null, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave())).isInstanceOf(AccessDeniedException.class);
  }

  // ------------------------------------------------------------------ entrega

  @Test
  void entregaCompleta_comprovacaoObrigatoriaEMontagemDepoisDaEntrega() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(gerente);
    assertThatThrownBy(() -> expedicao.agendarEntrega(v.id(), LocalDate.now().minusDays(1), PeriodoAgenda.MANHA, null, null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("passado");
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, "Equipe 2", null);
    assertThatThrownBy(() -> expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.TARDE, null, null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("reagendamento");
    expedicao.agendarMontagem(v.id(), amanha(), PeriodoAgenda.TARDE, "Montador João", null);

    assertThatThrownBy(() -> expedicao.concluirEntrega(v.id(), "Maria", null, null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("já saiu");
    expedicao.registrarSaida(v.id(), chave());

    assertThatThrownBy(() -> expedicao.concluirEntrega(v.id(), " ", null, null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("comprovação");
    // montagem só depois da entrega
    assertThatThrownBy(() -> expedicao.concluirMontagem(v.id(), "ok", null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("depois da entrega");

    var foto = new MockMultipartFile("arquivo", "comprovante.png", "image/png", PNG);
    expedicao.concluirEntrega(v.id(), "Maria da Silva", "Recebido na portaria", foto);
    var d = vendas.obter(v.id());
    assertThat(d.statusEntrega()).isEqualTo(StatusEntrega.ENTREGUE);
    assertThat(d.entrega().status()).isEqualTo(StatusEntregaRegistro.ENTREGUE);
    assertThat(d.entrega().recebedorNome()).isEqualTo("Maria da Silva");
    assertThat(d.entrega().comprovanteArquivo()).startsWith("privado/entregas/" + v.id() + "/");
    assertThat(d.entrega().frete()).isEqualTo("Gratuito");
    assertThat(d.entrega().eventos()).extracting(e -> e.tipo().name())
        .containsExactly("AGENDADA", "SAIDA", "ENTREGUE");
    assertThat(d.frete()).isEqualByComparingTo("0");
    assertThat(arquivos.ler(d.entrega().comprovanteArquivo()).exists()).isTrue();

    // montagem: evidência obrigatória; serviço incluso, sem cobrança
    assertThat(d.montagem().cobranca()).contains("sem cobrança adicional");
    assertThatThrownBy(() -> expedicao.concluirMontagem(v.id(), null, null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("evidência");
    expedicao.concluirMontagem(v.id(), "Guarda-roupa montado e nivelado", null);
    d = vendas.obter(v.id());
    assertThat(d.statusMontagem()).isEqualTo(StatusMontagem.CONCLUIDA);
    assertThat(d.montagem().status()).isEqualTo(StatusMontagemRegistro.CONCLUIDA);
    assertThat(d.total()).isEqualByComparingTo("1000.00"); // nada foi cobrado a mais
  }

  @Test
  void tentativaFrustradaMantemPendenciaEReagendamentoNaoBaixaDeNovo() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, "Equipe 1", null);
    expedicao.registrarSaida(v.id(), chave());
    assertThat(saldo(p).fisico()).isEqualTo(4);

    assertThatThrownBy(() -> expedicao.registrarTentativaFrustrada(v.id(), " ")).isInstanceOf(NegocioException.class);
    expedicao.registrarTentativaFrustrada(v.id(), "Cliente ausente");
    var d = vendas.obter(v.id());
    assertThat(d.statusEntrega()).isEqualTo(StatusEntrega.TENTATIVA_FRUSTRADA);
    assertThat(d.entrega().tentativasFrustradas()).isEqualTo(1);
    assertThat(d.acoes().podeReagendarEntrega()).isTrue();
    assertThat(d.acoes().podeCancelar()).isFalse(); // a mercadoria já saiu da loja
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("reagende");

    assertThatThrownBy(() -> expedicao.reagendarEntrega(v.id(), amanha().plusDays(1), PeriodoAgenda.TARDE, null, null))
        .isInstanceOf(NegocioException.class);
    expedicao.reagendarEntrega(v.id(), amanha().plusDays(1), PeriodoAgenda.TARDE, "Equipe 2", "Cliente pediu outro dia");
    expedicao.registrarSaida(v.id(), chave());
    assertThat(saldo(p).fisico()).isEqualTo(4); // sem nova baixa
    expedicao.concluirEntrega(v.id(), "Maria", null, null);
    var fim = vendas.obter(v.id());
    assertThat(fim.entrega().eventos()).extracting(e -> e.tipo().name())
        .containsExactly("AGENDADA", "SAIDA", "TENTATIVA_FRUSTRADA", "REAGENDADA", "SAIDA", "ENTREGUE");
    assertThat(fim.movimentacoes()).hasSize(1);
  }

  @Test
  void cancelarDepoisDaSaidaEhBloqueadoEAntesEncerraAgenda() {
    var p = produto(5);
    var antes = confirmada(p, 1);
    como(gerente);
    expedicao.agendarEntrega(antes.id(), amanha(), PeriodoAgenda.MANHA, null, null);
    expedicao.agendarMontagem(antes.id(), amanha(), PeriodoAgenda.TARDE, "João", null);
    var cancelada = vendas.cancelar(antes.id(), "Desistência");
    assertThat(cancelada.entrega().status()).isEqualTo(StatusEntregaRegistro.CANCELADA);
    assertThat(cancelada.montagem().status()).isEqualTo(StatusMontagemRegistro.NAO_NECESSARIA);
    assertThat(saldo(p).reservado()).isZero();

    var depois = confirmada(p, 1);
    como(gerente);
    expedicao.agendarEntrega(depois.id(), amanha(), PeriodoAgenda.MANHA, null, null);
    expedicao.registrarSaida(depois.id(), chave());
    assertThatThrownBy(() -> vendas.cancelar(depois.id(), "tarde demais"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("já saiu");
  }

  @Test
  void montagemPodeSerMarcadaComoNaoNecessariaComMotivo() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(gerente);
    assertThatThrownBy(() -> expedicao.dispensarMontagem(v.id(), " ")).isInstanceOf(NegocioException.class);
    expedicao.dispensarMontagem(v.id(), "Produto já vem montado");
    var d = vendas.obter(v.id());
    assertThat(d.statusMontagem()).isEqualTo(StatusMontagem.NAO_NECESSARIA);
    assertThatThrownBy(() -> expedicao.agendarMontagem(v.id(), amanha(), PeriodoAgenda.MANHA, null, null))
        .isInstanceOf(NegocioException.class);
  }

  @Test
  void agendaListaEntregasEMontagensDoPeriodo() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, "Equipe 1", null);
    expedicao.agendarMontagem(v.id(), amanha().plusDays(1), PeriodoAgenda.TARDE, "João", null);
    var itens = expedicao.agenda(LocalDate.now(), LocalDate.now().plusDays(7)).stream()
        .filter(i -> i.pedidoId().equals(v.id())).toList();
    assertThat(itens).extracting(i -> i.tipo()).containsExactly("RETIRADA", "MONTAGEM");
    assertThatThrownBy(() -> expedicao.agenda(LocalDate.now(), LocalDate.now().minusDays(1)))
        .isInstanceOf(NegocioException.class);
  }

  // ------------------------------------------------------------------ encomenda

  @Test
  void encomendaCicloCompleto_recebimentoGeraEntradaEReservaPosterior() {
    var p = produtoEncomenda();
    como(vendedor);
    var c = cliente();
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 2, ModalidadeItem.ENCOMENDA);
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null, List.of(item)));
    var conf = vendas.confirmar(v.id(), chave());
    assertThat(conf.reservas()).isEmpty();
    assertThat(conf.encomendas()).hasSize(1);
    assertThat(conf.encomendas().get(0).status()).isEqualTo(StatusEncomenda.AGUARDANDO_PEDIDO);
    assertThat(conf.encomendas().get(0).prazoPadrao()).contains("D08");

    como(gerente);
    Long encId = conf.encomendas().get(0).id();
    var atualizada = encomendas.atualizar(encId, new AtualizarEncomendaRequest("Fábrica X", "PED-77",
        LocalDate.now().plusDays(10), "Previsão informada pelo fornecedor"));
    assertThat(atualizada.status()).isEqualTo(StatusEncomenda.PEDIDO_REALIZADO);
    assertThat(atualizada.previsaoChegada()).isEqualTo(LocalDate.now().plusDays(10));

    assertThatThrownBy(() -> encomendas.receber(encId, 2, " ")).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> encomendas.receber(encId, 0, chave())).isInstanceOf(NegocioException.class);

    // Recebimento PARCIAL do FORNECEDOR é aceito: entra no estoque e já fica reservado ao cliente...
    String k1 = chave();
    var parcial = encomendas.receber(encId, 1, k1);
    assertThat(parcial.status()).isEqualTo(StatusEncomenda.PARCIALMENTE_RECEBIDA);
    assertThat(parcial.quantidadeRecebida()).isEqualTo(1);
    assertThat(parcial.quantidadeFaltante()).isEqualTo(1);
    assertThat(parcial.quantidadeReservada()).isEqualTo(1);
    var s1 = saldoDe(p);
    assertThat(s1.fisico()).isEqualTo(1);
    assertThat(s1.reservado()).isEqualTo(1);
    assertThat(s1.disponivel()).isZero();   // a unidade chegada não pode ser vendida a outro cliente
    // repetição do mesmo recebimento: sem nova entrada
    encomendas.receber(encId, 1, k1);
    assertThat(saldoDe(p).fisico()).isEqualTo(1);

    // ... mas a ENTREGA ao CLIENTE continua indivisível: sem o restante, a saída é recusada
    expedicao.agendarEntrega(v.id(), LocalDate.now().plusDays(1), PeriodoAgenda.MANHA, null, null);
    assertThatThrownBy(() -> expedicao.registrarSaida(v.id(), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não há entrega parcial");
    assertThat(saldoDe(p).fisico()).isEqualTo(1);

    // o restante chega: a encomenda completa e a venda pode sair inteira
    var rec = encomendas.receber(encId, 1, chave());
    assertThat(rec.status()).isEqualTo(StatusEncomenda.RECEBIDA);
    assertThat(rec.quantidadeRecebida()).isEqualTo(2);
    assertThat(rec.reserva()).isEqualTo(StatusReserva.ATIVA);
    assertThat(rec.recebimentos()).hasSize(2);
    var s = saldoDe(p);
    assertThat(s.fisico()).isEqualTo(2);
    assertThat(s.reservado()).isEqualTo(2);
    assertThat(s.disponivel()).isZero();
    var d = vendas.obter(v.id());
    assertThat(d.movimentacoes()).extracting(m -> m.tipo())
        .containsExactly(TipoMovimentacao.ENTRADA_ENCOMENDA, TipoMovimentacao.ENTRADA_ENCOMENDA);
    expedicao.registrarSaida(v.id(), chave());
    assertThat(vendas.obter(v.id()).statusEntrega()).isEqualTo(StatusEntrega.SAIU);
    assertThat(saldoDe(p).fisico()).isZero();

    // encomenda já completa não recebe mais; chave reutilizada em outra encomenda é recusada
    assertThatThrownBy(() -> encomendas.receber(encId, 1, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("por completo");
    assertThatThrownBy(() -> encomendas.atualizar(encId, new AtualizarEncomendaRequest("Y", null, null, null)))
        .isInstanceOf(NegocioException.class);
  }

  @Test
  void recebimentoAlemDoVendidoFicaComoEstoqueLivre() {
    var p = produtoEncomenda();
    como(vendedor);
    var c = cliente();
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null,
        List.of(new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 2, ModalidadeItem.ENCOMENDA))));
    var conf = vendas.confirmar(v.id(), chave());
    como(gerente);
    var rec = encomendas.receber(conf.encomendas().get(0).id(), 3, chave());
    assertThat(rec.status()).isEqualTo(StatusEncomenda.RECEBIDA);
    assertThat(rec.quantidadeReservada()).isEqualTo(2);   // só o necessário fica reservado ao cliente
    var s = saldoDe(p);
    assertThat(s.fisico()).isEqualTo(3);
    assertThat(s.reservado()).isEqualTo(2);
    assertThat(s.disponivel()).isEqualTo(1);
  }

  @Test
  void cancelarVendaAntesDoRecebimentoEncerraAEncomenda() {
    var p = produtoEncomenda();
    como(vendedor);
    var c = cliente();
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null,
        List.of(new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 1, ModalidadeItem.ENCOMENDA))));
    vendas.confirmar(v.id(), chave());
    como(gerente);
    var cancelada = vendas.cancelar(v.id(), "Cliente desistiu");
    assertThat(cancelada.encomendas().get(0).status()).isEqualTo(StatusEncomenda.CANCELADA);
    var enc = cancelada.encomendas().get(0);
    assertThatThrownBy(() -> encomendas.receber(enc.id(), 1, chave())).isInstanceOf(NegocioException.class);
  }

  @Test
  void recebimentoExigeSaldoInformadoEOrientaAContagem() {
    var p = produtoEncomenda();
    // saldo da variação desconhecido (nulo): o sistema não presume zero
    var variacao = p.getVariacoes().get(0);
    jdbcSetEstoque(variacao.getId(), null);
    como(vendedor);
    var c = cliente();
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null,
        List.of(new ItemRequest(p.getId(), variacao.getId(), 1, ModalidadeItem.ENCOMENDA))));
    var conf = vendas.confirmar(v.id(), chave());
    como(gerente);
    assertThatThrownBy(() -> encomendas.receber(conf.encomendas().get(0).id(), 1, chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("contagem de inventário");
  }

  // ------------------------------------------------------------------ inventário

  @Test
  void inventarioAjustaComMotivoHistoricoEIdempotencia() {
    var p = produto(5);
    como(gerente);
    String k = chave();
    assertThatThrownBy(() -> estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 8, " ", k))
        .isInstanceOf(NegocioException.class).hasMessageContaining("motivo");
    assertThatThrownBy(() -> estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 8, "Contagem", null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("Idempotency-Key");
    assertThatThrownBy(() -> estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 5, "Contagem", chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("nada a ajustar");

    var mov = estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 8, "Contagem de outubro: sobraram 3", k);
    assertThat(mov.tipo()).isEqualTo(TipoMovimentacao.AJUSTE_INVENTARIO);
    assertThat(mov.quantidade()).isEqualTo(3);
    assertThat(mov.saldoAnterior()).isEqualTo(5);
    assertThat(mov.saldoPosterior()).isEqualTo(8);
    assertThat(mov.usuario()).isEqualTo(gerente.getNome());
    assertThat(saldo(p).fisico()).isEqualTo(8);
    // agregado do produto acompanha a soma das variações
    assertThat(produtos.findById(p.getId()).orElseThrow().getEstoque()).isEqualTo(8);

    // mesma chave: devolve o mesmo ajuste, sem aplicar de novo
    var repetido = estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 8, "Contagem de outubro: sobraram 3", k);
    assertThat(repetido.id()).isEqualTo(mov.id());
    assertThat(saldo(p).fisico()).isEqualTo(8);
    assertThat(estoque.listarMovimentacoes(p.getId(), p.getVariacoes().get(0).getId(), null, 0, 10).conteudo())
        .hasSize(1);
  }

  @Test
  void inventarioConsideraReservasExistentes() {
    var p = produto(5);
    var v = confirmada(p, 3);
    como(gerente);
    assertThatThrownBy(() -> estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 2, "Quebra", chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("reservado");
    var ok = estoque.ajustarContagem(p.getId(), p.getVariacoes().get(0).getId(), 3, "Quebra de 2 un.", chave());
    assertThat(ok.quantidade()).isEqualTo(-2);
    assertThat(saldo(p).disponivel()).isZero();
    assertThat(vendas.obter(v.id()).reservas().get(0).status()).isEqualTo(StatusReserva.ATIVA);
  }

  @Test
  void primeiraContagemDefineSaldoNulo() {
    var p = produtoEncomenda();
    var variacao = p.getVariacoes().get(0);
    jdbcSetEstoque(variacao.getId(), null);
    como(gerente);
    var mov = estoque.ajustarContagem(p.getId(), variacao.getId(), 0, "Primeira contagem: nada em estoque", chave());
    assertThat(mov.saldoAnterior()).isNull();
    assertThat(mov.saldoPosterior()).isZero();
    assertThat(saldoDe(p).fisico()).isZero();
  }

  // ------------------------------------------------------------------ pós-venda

  @Test
  void posVendaSoDepoisDaEntrega() {
    var p = produto(5);
    var v = confirmada(p, 1);
    como(gerente);
    var itemId = v.itens().get(0).id();
    assertThatThrownBy(() -> posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.ASSISTENCIA,
        "Porta desalinhada", itemId, null, null)))
        .isInstanceOf(NegocioException.class).hasMessageContaining("já entregues");
  }

  @Test
  void devolucaoAptaVoltaAoEstoqueENaoAptaNao() {
    var p = produto(5);
    var v = entregue(p, 2);
    var itemId = v.itens().get(0).id();
    como(gerente);
    assertThat(saldo(p).fisico()).isEqualTo(3);

    assertThatThrownBy(() -> posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.DEVOLUCAO,
        "Cliente devolveu", itemId, 3, null))).isInstanceOf(NegocioException.class).hasMessageContaining("acima do vendido");
    assertThatThrownBy(() -> posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.DEVOLUCAO,
        "Sem item", null, null, null))).isInstanceOf(NegocioException.class);

    var o = posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.DEVOLUCAO, "Arrependimento", itemId, 1, null));
    assertThat(o.status()).isEqualTo(StatusOcorrencia.ABERTA);
    assertThat(o.bloqueios()).containsKey("solucaoFinanceira");
    assertThatThrownBy(() -> posVenda.resolver(o.id(), "Resolvido")).isInstanceOf(NegocioException.class)
        .hasMessageContaining("Receba e avalie");
    assertThat(saldo(p).fisico()).isEqualTo(3); // nada volta antes da avaliação física

    String k = chave();
    var recebida = posVenda.receberDevolucao(o.id(), CondicaoFisica.APTA_REVENDA, "Sem avarias", k);
    assertThat(recebida.status()).isEqualTo(StatusOcorrencia.DEVOLUCAO_RECEBIDA);
    assertThat(recebida.estoqueReposto()).isTrue();
    assertThat(saldo(p).fisico()).isEqualTo(4);
    posVenda.receberDevolucao(o.id(), CondicaoFisica.APTA_REVENDA, "Sem avarias", k);
    assertThat(saldo(p).fisico()).isEqualTo(4);
    var resolvida = posVenda.resolver(o.id(), "Devolução aceita; restituição a definir");
    assertThat(resolvida.status()).isEqualTo(StatusOcorrencia.RESOLVIDA);
    assertThat(resolvida.bloqueios().get("solucaoFinanceira")).contains("D09");

    // segunda devolução: o item restante é 1; avaliada como NÃO apta, não volta ao saldo
    var o2 = posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.DEVOLUCAO, "Avariado", itemId, 1, null));
    assertThatThrownBy(() -> posVenda.receberDevolucao(o2.id(), CondicaoFisica.NAO_APTA, " ", chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("avaliação");
    var r2 = posVenda.receberDevolucao(o2.id(), CondicaoFisica.NAO_APTA, "Estrutura quebrada no transporte", chave());
    assertThat(r2.estoqueReposto()).isFalse();
    assertThat(saldo(p).fisico()).isEqualTo(4);
    var movs = estoque.movimentacoesDoPedido(v.id());
    assertThat(movs).extracting(m -> m.tipo())
        .containsExactly(TipoMovimentacao.SAIDA_VENDA, TipoMovimentacao.ENTRADA_DEVOLUCAO);
    // cota esgotada
    assertThatThrownBy(() -> posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.DEVOLUCAO, "Mais uma",
        itemId, 1, null))).isInstanceOf(NegocioException.class).hasMessageContaining("acima do vendido");
  }

  @Test
  void trocaCalculaDiferencaSemMovimentarValores() {
    var p = produto(5);          // R$ 1.000,00
    var maior = produto(5);
    maior.setPreco(new BigDecimal("1300.00"));
    maior = produtos.save(maior);
    var v = entregue(p, 1);
    como(gerente);
    var o = posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.TROCA, "Quer o modelo maior",
        v.itens().get(0).id(), 1, new TrocaRequest(maior.getId(), maior.getVariacoes().get(0).getId(), 1)));
    assertThat(o.diferencaCalculada()).isEqualByComparingTo("300.00");
    assertThat(o.trocaItem()).contains("Guarda-roupa");
    assertThat(o.bloqueios().get("solucaoFinanceira")).contains("D09");
    // o pedido original não foi alterado financeiramente
    var d = vendas.obter(v.id());
    assertThat(d.total()).isEqualByComparingTo("1000.00");
    assertThat(d.ocorrencias()).hasSize(1);
  }

  @Test
  void assistenciaTemEvidenciaEResolucaoSemDevolucaoFisica() {
    var p = produto(5);
    var v = entregue(p, 1);
    como(gerente);
    var o = posVenda.abrir(v.id(), new AbrirOcorrenciaRequest(TipoOcorrencia.ASSISTENCIA, "Gaveta emperrando", null,
        null, null));
    assertThatThrownBy(() -> posVenda.receberDevolucao(o.id(), CondicaoFisica.APTA_REVENDA, null, chave()))
        .isInstanceOf(NegocioException.class);
    var comEvidencia = posVenda.anexarEvidencia(o.id(), new MockMultipartFile("arquivo", "gaveta.png", "image/png", PNG),
        "Foto da gaveta");
    assertThat(comEvidencia.evidencias()).hasSize(1);
    assertThat(comEvidencia.evidencias().get(0).arquivo()).startsWith("privado/ocorrencias/");
    var r = posVenda.resolver(o.id(), "Trilho substituído pela equipe");
    assertThat(r.status()).isEqualTo(StatusOcorrencia.RESOLVIDA);
    assertThatThrownBy(() -> posVenda.cancelar(o.id(), "tarde")).isInstanceOf(NegocioException.class);
  }

  // ------------------------------------------------------------------ arquivos

  @Test
  void arquivosPrivadosValidamTipoConteudoETamanhoENaoPermitemPathTraversal() {
    assertThatThrownBy(() -> arquivos.salvar(new MockMultipartFile("a", "x.exe", "application/octet-stream", PNG), "teste"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("Tipo de arquivo");
    assertThatThrownBy(() -> arquivos.salvar(new MockMultipartFile("a", "fake.png", "image/png",
        "<html>nao sou imagem</html>".getBytes()), "teste"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não corresponde");
    assertThatThrownBy(() -> arquivos.salvar(new MockMultipartFile("a", "grande.png", "image/png",
        new byte[(int) ArquivoService.TAMANHO_MAXIMO + 1]), "teste"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("5 MB");
    String caminho = arquivos.salvar(new MockMultipartFile("a", "../../ok.png", "image/png", PNG), "../../teste");
    assertThat(caminho).startsWith("privado/").doesNotContain("..");
    assertThat(arquivos.ler(caminho).exists()).isTrue();
    assertThatThrownBy(() -> arquivos.ler("privado/../../application.yml")).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> arquivos.ler("produtos/qualquer.png")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void saidasSimultaneasDoMesmoPedidoBaixamUmaUnicaVez() throws Exception {
    var p = produto(5);
    var v = confirmada(p, 2);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, null, null);

    java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    List<java.util.concurrent.Future<Boolean>> futuros = new java.util.ArrayList<>();
    for (int i = 0; i < 2; i++) {
      String k = chave();
      futuros.add(pool.submit(() -> {
        como(gerente);
        largada.await();
        try {
          expedicao.registrarSaida(v.id(), k);
          return true;
        } catch (NegocioException e) {
          return false;
        }
      }));
    }
    largada.countDown();
    int sucessos = 0;
    for (var f : futuros) {
      if (f.get()) {
        sucessos++;
      }
    }
    pool.shutdown();

    assertThat(sucessos).isEqualTo(1);
    assertThat(saldo(p).fisico()).isEqualTo(3); // 5 - 2, uma única baixa
    assertThat(vendas.obter(v.id()).movimentacoes()).hasSize(1);
  }

  // ------------------------------------------------------------------ helpers

  private LocalDate amanha() {
    return LocalDate.now().plusDays(1);
  }

  private String chave() {
    return UUID.randomUUID().toString();
  }

  /** Venda de pronta entrega registrada pelo vendedor e confirmada (reserva ativa). */
  private VendaDetalheResponse confirmada(Produto p, int qtd) {
    como(vendedor);
    var c = cliente();
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), qtd, ModalidadeItem.PRONTA_ENTREGA);
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null, List.of(item)));
    return vendas.confirmar(v.id(), chave());
  }

  /** Venda confirmada, com saída registrada e entrega concluída. */
  private VendaDetalheResponse entregue(Produto p, int qtd) {
    var v = confirmada(p, qtd);
    como(gerente);
    expedicao.agendarEntrega(v.id(), amanha(), PeriodoAgenda.MANHA, "Equipe 1", null);
    expedicao.registrarSaida(v.id(), chave());
    expedicao.concluirEntrega(v.id(), "Cliente", null, null);
    return vendas.obter(v.id());
  }

  private EstoqueService.Saldo saldo(Produto p) {
    return saldoDe(p);
  }

  private EstoqueService.Saldo saldoDe(Produto p) {
    Long variacaoId = p.getVariacoes().get(0).getId();
    return estoque.listarSaldos().stream().filter(s -> variacaoId.equals(s.variacaoId())).findFirst().orElseThrow();
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  private void jdbcSetEstoque(Long variacaoId, Integer valor) {
    jdbc.update("update produto_variacoes set estoque = ? where id = ?", valor, variacaoId);
  }

  private User usuario(String email, Role role) {
    return users.findByEmail(email).orElseGet(() -> users.save(User.builder().email(email).nome(email)
        .passwordHash("x").roles(Set.of(role)).enabled(true).createdAt(Instant.now()).build()));
  }

  private void como(User u) {
    var autoridades = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, autoridades));
  }

  private Cliente cliente() {
    return clientes.save(Cliente.builder().nome("Cliente " + UUID.randomUUID().toString().substring(0, 8)).build());
  }

  private Produto produto(int estoqueVariacao) {
    String sufixo = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Guarda-roupa " + sufixo).preco(new BigDecimal("1000.00"))
        .estoque(estoqueVariacao).sku("P-" + sufixo).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("Casal").sku("V-" + sufixo)
        .adicionalPreco(BigDecimal.ZERO).estoque(estoqueVariacao).build());
    return produtos.save(p);
  }

  /** Produto vendido só sob encomenda, com saldo físico zero. */
  private Produto produtoEncomenda() {
    var p = produto(0);
    p.setModalidade(ModalidadeProduto.ENCOMENDA);
    return produtos.save(p);
  }
}
