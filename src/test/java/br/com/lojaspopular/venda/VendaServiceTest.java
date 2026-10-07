package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.catalog.enums.ModalidadeProduto;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.enums.StatusSolicitacaoDesconto;
import br.com.lojaspopular.domain.comercial.repository.CondicaoPagamentoRepository;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/** Fluxo da Etapa 1: venda → desconto → confirmação com reserva → cancelamento. */
@SpringBootTest
@ActiveProfiles("test")
class VendaServiceTest {

  @Autowired VendaService vendas;
  @Autowired EstoqueService estoque;
  @Autowired ConfiguracaoComercialService config;
  @Autowired UserRepository users;
  @Autowired ClienteRepository clientes;
  @Autowired ProdutoRepository produtos;
  @Autowired CondicaoPagamentoRepository condicoes;

  User admin;
  User gerente;
  User vendedor;
  User outroVendedor;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-t@loja.com", Role.ADMIN);
    gerente = usuario("gerente-t@loja.com", Role.GERENTE);
    vendedor = usuario("vendedor-t@loja.com", Role.VENDEDOR);
    outroVendedor = usuario("vendedor2-t@loja.com", Role.VENDEDOR);

    // Estado conhecido da configuração comercial a cada teste
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE));
    garantirCondicao(FormaPagamento.PIX, 1, "0.00");
    garantirCondicao(FormaPagamento.CARTAO, 3, "10.00");
  }

  // ------------------------------------------------------------------ registro

  @Test
  void registraVendaComPrecoDoServidorEFreteZero() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 2, FormaPagamento.CARTAO, 3, null));

    // preço base 1000,00 com +10% no cartão em 3x => 1100,00 por unidade
    assertThat(v.itens().get(0).precoUnitario()).isEqualByComparingTo("1100.00");
    assertThat(v.subtotal()).isEqualByComparingTo("2200.00");
    assertThat(v.frete()).isEqualByComparingTo("0.00");
    assertThat(v.total()).isEqualByComparingTo("2200.00");
    assertThat(v.statusComercial()).isEqualTo(StatusComercial.RASCUNHO);
    assertThat(v.vendedor().id()).isEqualTo(vendedor.getId());
    assertThat(v.reservas()).isEmpty();
  }

  @Test
  void configuracaoPendenteBloqueiaRegistro() {
    var p = produto(5);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), null, EnumSet.of(Role.ADMIN));
    como(vendedor);
    var c = cliente();
    assertThatThrownBy(() -> vendas.registrar(req(c, p, 1, FormaPagamento.PIX, 1, null)))
        .isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D04");
  }

  @Test
  void condicaoNaoCadastradaNaoEhOferecida() {
    var p = produto(5);
    como(vendedor);
    var c = cliente();
    assertThatThrownBy(() -> vendas.registrar(req(c, p, 1, FormaPagamento.CARTAO, 12, null)))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não cadastrada");
  }

  @Test
  void vendedorNaoRegistraEmNomeDeOutro() {
    var p = produto(5);
    como(vendedor);
    var c = cliente();
    var r = req(c, p, 1, FormaPagamento.PIX, 1, null, outroVendedor.getId(), null);
    assertThatThrownBy(() -> vendas.registrar(r)).isInstanceOf(NegocioException.class);
  }

  @Test
  void registroRepetidoComMesmaChaveNaoDuplicaPedido() {
    var p = produto(5);
    var c = cliente();
    como(vendedor);
    String chave = UUID.randomUUID().toString();
    var a = vendas.registrar(req(c, p, 1, FormaPagamento.PIX, 1, null, null, chave));
    var b = vendas.registrar(req(c, p, 1, FormaPagamento.PIX, 1, null, null, chave));
    assertThat(b.id()).isEqualTo(a.id());
  }

  // ------------------------------------------------------------------ reserva

  @Test
  void confirmarCriaReservaEReduzDisponivel() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 2, FormaPagamento.PIX, 1, null));
    var conf = vendas.confirmar(v.id(), UUID.randomUUID().toString());

    assertThat(conf.statusComercial()).isEqualTo(StatusComercial.CONFIRMADA);
    assertThat(conf.reservas()).hasSize(1);
    assertThat(conf.reservas().get(0).status()).isEqualTo(StatusReserva.ATIVA);
    var saldo = saldoDe(p);
    assertThat(saldo.fisico()).isEqualTo(5);
    assertThat(saldo.reservado()).isEqualTo(2);
    assertThat(saldo.disponivel()).isEqualTo(3);
  }

  @Test
  void confirmacaoRepetidaNaoDuplicaReserva() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 2, FormaPagamento.PIX, 1, null));
    String chave = UUID.randomUUID().toString();
    vendas.confirmar(v.id(), chave);
    var repetida = vendas.confirmar(v.id(), chave);

    assertThat(repetida.reservas()).hasSize(1);
    assertThat(saldoDe(p).reservado()).isEqualTo(2);
    // outra chave sobre venda já confirmada não é aceita
    assertThatThrownBy(() -> vendas.confirmar(v.id(), UUID.randomUUID().toString()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("já foi confirmada");
  }

  @Test
  void estoqueInsuficienteNaoGravaNada() {
    var p = produto(1);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 2, FormaPagamento.PIX, 1, null));
    assertThatThrownBy(() -> vendas.confirmar(v.id(), UUID.randomUUID().toString()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("Estoque insuficiente");
    assertThat(vendas.obter(v.id()).statusComercial()).isEqualTo(StatusComercial.RASCUNHO);
    assertThat(saldoDe(p).reservado()).isZero();
  }

  @Test
  void disputaPelaUltimaUnidadeTemUmUnicoVencedor() throws Exception {
    var p = produto(1);
    como(vendedor);
    var a = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, null));
    var b = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, null));

    CountDownLatch largada = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Callable<Boolean>> tarefas = new ArrayList<>();
    for (Long id : List.of(a.id(), b.id())) {
      tarefas.add(() -> {
        como(vendedor);
        largada.await();
        try {
          vendas.confirmar(id, UUID.randomUUID().toString());
          return true;
        } catch (NegocioException e) {
          return false;
        }
      });
    }
    List<Future<Boolean>> futuros = new ArrayList<>();
    for (var t : tarefas) {
      futuros.add(pool.submit(t));
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
    var saldo = saldoDe(p);
    assertThat(saldo.reservado()).isEqualTo(1);
    assertThat(saldo.disponivel()).isZero();
  }

  // ------------------------------------------------------------------ desconto

  @Test
  void descontoAteOLimiteSegueSemAprovacao() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, "100.00")); // 10% = limite
    assertThat(v.statusComercial()).isEqualTo(StatusComercial.RASCUNHO);
    assertThat(v.descontos()).isEmpty();
    assertThat(v.total()).isEqualByComparingTo("900.00");
  }

  @Test
  void descontoAcimaDoLimiteAguardaAprovacaoENaoConfirma() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, "150.00")); // 15%
    assertThat(v.statusComercial()).isEqualTo(StatusComercial.AGUARDANDO_APROVACAO);
    assertThat(v.descontos()).hasSize(1);
    assertThat(v.descontos().get(0).status()).isEqualTo(StatusSolicitacaoDesconto.PENDENTE);
    assertThatThrownBy(() -> vendas.confirmar(v.id(), UUID.randomUUID().toString()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("aprovação");
    // vendedor não decide desconto
    assertThatThrownBy(() -> vendas.decidirDesconto(v.id(), true, null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void gerenteAprovaEVendaPodeSerConfirmada() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, "150.00"));
    como(gerente);
    var aprovada = vendas.decidirDesconto(v.id(), true, "Cliente antigo");
    assertThat(aprovada.statusComercial()).isEqualTo(StatusComercial.RASCUNHO);
    assertThat(aprovada.descontos().get(0).status()).isEqualTo(StatusSolicitacaoDesconto.APROVADA);

    como(vendedor);
    var conf = vendas.confirmar(v.id(), UUID.randomUUID().toString());
    assertThat(conf.statusComercial()).isEqualTo(StatusComercial.CONFIRMADA);
    assertThat(conf.total()).isEqualByComparingTo("850.00");
  }

  @Test
  void alterarValoresInvalidaAprovacaoAnterior() {
    var p = produto(5);
    var c = cliente();
    como(vendedor);
    var v = vendas.registrar(req(c, p, 1, FormaPagamento.PIX, 1, "150.00"));
    como(gerente);
    vendas.decidirDesconto(v.id(), true, null);

    como(vendedor);
    var alterada = vendas.atualizar(v.id(), req(c, p, 1, FormaPagamento.PIX, 1, "200.00"));
    assertThat(alterada.statusComercial()).isEqualTo(StatusComercial.AGUARDANDO_APROVACAO);
    assertThat(alterada.descontos()).extracting(d -> d.status())
        .contains(StatusSolicitacaoDesconto.INVALIDADA, StatusSolicitacaoDesconto.PENDENTE);
    assertThatThrownBy(() -> vendas.confirmar(v.id(), UUID.randomUUID().toString()))
        .isInstanceOf(NegocioException.class);
  }

  @Test
  void solicitanteGerenteNaoAprovaOProprioDesconto() {
    var p = produto(5);
    como(gerente);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, "300.00"));
    assertThat(v.statusComercial()).isEqualTo(StatusComercial.AGUARDANDO_APROVACAO);
    assertThatThrownBy(() -> vendas.decidirDesconto(v.id(), true, null))
        .isInstanceOf(NegocioException.class).hasMessageContaining("próprio desconto");
    como(admin);
    assertThat(vendas.decidirDesconto(v.id(), true, null).statusComercial()).isEqualTo(StatusComercial.RASCUNHO);
  }

  @Test
  void semLimiteConfiguradoNaoHaConcessaoDeDesconto() {
    var p = produto(5);
    var c = cliente();
    como(admin);
    config.atualizar(null, Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN));
    como(vendedor);
    assertThatThrownBy(() -> vendas.registrar(req(c, p, 1, FormaPagamento.PIX, 1, "1.00")))
        .isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D03");
    // sem desconto a venda segue normalmente
    assertThat(vendas.registrar(req(c, p, 1, FormaPagamento.PIX, 1, null)).statusComercial())
        .isEqualTo(StatusComercial.RASCUNHO);
  }

  // ------------------------------------------------------------------ cancelamento

  @Test
  void cancelarLiberaReservaEPreservaHistorico() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 2, FormaPagamento.PIX, 1, null));
    vendas.confirmar(v.id(), UUID.randomUUID().toString());
    assertThat(saldoDe(p).reservado()).isEqualTo(2);

    como(gerente);
    var cancelada = vendas.cancelar(v.id(), "Cliente desistiu");
    assertThat(cancelada.statusComercial()).isEqualTo(StatusComercial.CANCELADA);
    assertThat(cancelada.reservas().get(0).status()).isEqualTo(StatusReserva.LIBERADA);
    assertThat(saldoDe(p).reservado()).isZero();
    assertThat(saldoDe(p).disponivel()).isEqualTo(5);
    assertThat(cancelada.historico()).extracting(h -> h.tipo()).contains("VENDA_CANCELADA", "RESERVA_LIBERADA");
    // cancelar de novo é inofensivo
    assertThat(vendas.cancelar(v.id(), "outra vez").statusComercial()).isEqualTo(StatusComercial.CANCELADA);
  }

  @Test
  void cancelamentoExigePerfilConfiguradoEAutorizado() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, null));
    vendas.confirmar(v.id(), UUID.randomUUID().toString());

    // perfil do vendedor não está entre os autorizados (ADMIN, GERENTE)
    assertThatThrownBy(() -> vendas.cancelar(v.id(), "teste")).isInstanceOf(AccessDeniedException.class);
    assertThat(vendas.obter(v.id()).acoes().podeCancelar()).isFalse();

    // sem a regra definida (D07) a ação fica bloqueada para todos
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, null);
    assertThatThrownBy(() -> vendas.cancelar(v.id(), "teste")).isInstanceOf(ConfiguracaoPendenteException.class);
    assertThat(vendas.obter(v.id()).acoes().bloqueios()).containsKey("cancelar");
  }

  @Test
  void cancelamentoExigeMotivo() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, null));
    como(gerente);
    assertThatThrownBy(() -> vendas.cancelar(v.id(), " ")).isInstanceOf(NegocioException.class);
  }

  // ------------------------------------------------------------------ preço histórico

  @Test
  void novaTabelaNaoAlteraPrecoDeVendaExistenteECatalogoMudadoExigeRecalculo() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.CARTAO, 3, null));
    assertThat(v.itens().get(0).precoUnitario()).isEqualByComparingTo("1100.00");
    var confirmada = vendas.confirmar(v.id(), UUID.randomUUID().toString());

    como(gerente);
    var cond = condicoes.findByFormaAndParcelas(FormaPagamento.CARTAO, 3).orElseThrow();
    config.atualizarCondicao(cond.getId(), new BigDecimal("25.00"), true);

    como(vendedor);
    var relida = vendas.obter(confirmada.id());
    assertThat(relida.itens().get(0).precoUnitario()).isEqualByComparingTo("1100.00");
    assertThat(relida.ajusteCondicaoPercentual()).isEqualByComparingTo("10.00");
    assertThat(relida.total()).isEqualByComparingTo("1100.00");

    // rascunho criado com a tabela antiga não confirma sem recalcular
    como(gerente);
    config.atualizarCondicao(cond.getId(), new BigDecimal("10.00"), true);
    como(vendedor);
    var rasc = vendas.registrar(req(cliente(), p, 1, FormaPagamento.CARTAO, 3, null));
    como(gerente);
    config.atualizarCondicao(cond.getId(), new BigDecimal("20.00"), true);
    como(vendedor);
    assertThatThrownBy(() -> vendas.confirmar(rasc.id(), UUID.randomUUID().toString()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("Recalcular");
    var recalculada = vendas.recalcular(rasc.id());
    assertThat(recalculada.itens().get(0).precoUnitario()).isEqualByComparingTo("1200.00");
    assertThat(vendas.confirmar(rasc.id(), UUID.randomUUID().toString()).statusComercial())
        .isEqualTo(StatusComercial.CONFIRMADA);
  }

  // ------------------------------------------------------------------ escopo

  @Test
  void vendedorSoEnxergaSuasVendas() {
    var p = produto(5);
    como(vendedor);
    var v = vendas.registrar(req(cliente(), p, 1, FormaPagamento.PIX, 1, null));

    como(outroVendedor);
    assertThatThrownBy(() -> vendas.obter(v.id())).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> vendas.cancelar(v.id(), "x")).isInstanceOf(NotFoundException.class);
    assertThat(vendas.listar(null, "", 0, 50).conteudo()).extracting(r -> r.id()).doesNotContain(v.id());

    como(gerente);
    assertThat(vendas.obter(v.id()).id()).isEqualTo(v.id());
    assertThat(vendas.listar(null, "", 0, 50).conteudo()).extracting(r -> r.id()).contains(v.id());
  }

  @Test
  void encomendaNaoReservaEstoque() {
    var p = produto(0);
    p.setModalidade(ModalidadeProduto.ENCOMENDA);
    p = produtos.save(p);
    como(vendedor);
    var c = cliente();
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 1, ModalidadeItem.ENCOMENDA);
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null, List.of(item)));
    var conf = vendas.confirmar(v.id(), UUID.randomUUID().toString());
    assertThat(conf.statusComercial()).isEqualTo(StatusComercial.CONFIRMADA);
    assertThat(conf.reservas()).isEmpty();
  }

  @Test
  void modalidadeNaoAceitaPeloProdutoERejeitada() {
    var p = produto(5); // PRONTA_ENTREGA
    como(vendedor);
    var c = cliente();
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 1, ModalidadeItem.ENCOMENDA);
    assertThatThrownBy(() -> vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA,
        TipoEntrega.RETIRADA, null, FormaPagamento.PIX, 1, null, null, null, null, List.of(item))))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não é vendido em encomenda");
  }

  // ------------------------------------------------------------------ helpers

  private EstoqueService.Saldo saldoDe(Produto p) {
    Long variacaoId = p.getVariacoes().get(0).getId();
    return estoque.listarSaldos().stream().filter(s -> variacaoId.equals(s.variacaoId())).findFirst().orElseThrow();
  }

  private User usuario(String email, Role role) {
    return users.findByEmail(email).orElseGet(() -> users.save(User.builder().email(email).nome(email)
        .passwordHash("x").roles(Set.of(role)).enabled(true).createdAt(java.time.Instant.now()).build()));
  }

  private void garantirCondicao(FormaPagamento forma, int parcelas, String ajuste) {
    var c = condicoes.findByFormaAndParcelas(forma, parcelas);
    if (c.isPresent()) {
      config.atualizarCondicao(c.get().getId(), new BigDecimal(ajuste), true);
    } else {
      config.criarCondicao(forma, parcelas, new BigDecimal(ajuste), true);
    }
  }

  private void como(User u) {
    var autoridades = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, autoridades));
  }

  private Cliente cliente() {
    return clientes.save(Cliente.builder().nome("Cliente " + UUID.randomUUID().toString().substring(0, 8)).build());
  }

  /** Produto de R$ 1.000,00 com uma variação que tem o saldo informado. */
  private Produto produto(int estoqueVariacao) {
    String sufixo = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Guarda-roupa " + sufixo).preco(new BigDecimal("1000.00"))
        .estoque(estoqueVariacao).sku("P-" + sufixo).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("Casal").sku("V-" + sufixo)
        .adicionalPreco(BigDecimal.ZERO).estoque(estoqueVariacao).build());
    return produtos.save(p);
  }

  private VendaRequest req(Cliente c, Produto p, int qtd, FormaPagamento forma, int parcelas, String desconto) {
    return req(c, p, qtd, forma, parcelas, desconto, null, null);
  }

  private VendaRequest req(Cliente c, Produto p, int qtd, FormaPagamento forma, int parcelas, String desconto,
      Long vendedorId, String chave) {
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), qtd, ModalidadeItem.PRONTA_ENTREGA);
    return new VendaRequest(c.getId(), vendedorId, CanalVenda.LOJA, TipoEntrega.RETIRADA, null, forma, parcelas,
        desconto == null ? null : new BigDecimal(desconto), null, null, chave, List.of(item));
  }
}
