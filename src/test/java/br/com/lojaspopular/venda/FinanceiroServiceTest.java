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
import br.com.lojaspopular.application.expedicao.ExpedicaoService;
import br.com.lojaspopular.application.financeiro.CaixaService;
import br.com.lojaspopular.application.financeiro.CartaoService;
import br.com.lojaspopular.application.financeiro.ComissaoService;
import br.com.lojaspopular.application.financeiro.ContaFinanceiraService;
import br.com.lojaspopular.application.financeiro.FechamentoService;
import br.com.lojaspopular.application.financeiro.MetaService;
import br.com.lojaspopular.application.financeiro.Relogio;
import br.com.lojaspopular.application.financeiro.RecebimentoService;
import br.com.lojaspopular.application.financeiro.RestituicaoService;
import br.com.lojaspopular.application.posvenda.PosVendaService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.repository.CondicaoPagamentoRepository;
import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.financeiro.enums.AquisicaoComissao;
import br.com.lojaspopular.domain.financeiro.enums.CompetenciaReceita;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebimento;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
import br.com.lojaspopular.domain.financeiro.enums.TipoComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.TaxaCartaoRepository;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.posvenda.enums.CondicaoFisica;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AbrirOcorrenciaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.TrocaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ConfigFinanceiraRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RegistrarRecebimentoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TipoMovimentoCaixa;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/**
 * Etapa 3: recebimentos (cliente x operadora x entrada efetiva), caixa, contas, cartão, comissões, metas,
 * restituições (D09), fechamento e bloqueio de período.
 *
 * <p>Os valores de configuração usados aqui (percentual, momentos, perfis, políticas) são SOMENTE de teste.
 */
@SpringBootTest
@ActiveProfiles("test")
class FinanceiroServiceTest {

  @Autowired VendaService vendas;
  @Autowired ExpedicaoService expedicao;
  @Autowired PosVendaService posVenda;
  @Autowired RecebimentoService recebimentos;
  @Autowired CaixaService caixa;
  @Autowired CartaoService cartao;
  @Autowired ContaFinanceiraService contas;
  @Autowired ComissaoService comissoes;
  @Autowired MetaService metas;
  @Autowired RestituicaoService restituicoes;
  @Autowired FechamentoService fechamento;
  @Autowired ConfiguracaoComercialService config;
  @Autowired Relogio relogio;
  @Autowired UserRepository users;
  @Autowired ClienteRepository clientes;
  @Autowired ProdutoRepository produtos;
  @Autowired CondicaoPagamentoRepository condicoes;
  @Autowired TaxaCartaoRepository taxas;
  @Autowired LancamentoFinanceiroRepository lancamentos;

  User admin;
  User gerente;
  User vendedor;
  int mesTeste = 0;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-f@loja.com", Role.ADMIN);
    gerente = usuario("gerente-f@loja.com", Role.GERENTE);
    vendedor = usuario("vendedor-f-" + UUID.randomUUID().toString().substring(0, 8) + "@loja.com", Role.VENDEDOR);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), false);
    config.atualizarPermissoes(new br.com.lojaspopular.web.financeiro.PermissaoDtos.PermissoesFinanceirasRequest(
        EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE)));
    garantirCondicao(FormaPagamento.PIX, 1, "0.00");
    garantirCondicao(FormaPagamento.DINHEIRO, 1, "0.00");
    garantirCondicao(FormaPagamento.CARTAO, 3, "10.00");
    garantirCondicao(FormaPagamento.CARTAO, 1, "10.00");
    // Regras financeiras de TESTE (nenhuma é recomendação de negócio)
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    var existente = taxas.findByOperadoraAndParcelas("OPTESTE", 3);
    cartao.salvarTaxa(existente.map(t -> t.getId()).orElse(null),
        new TaxaCartaoRequest("OPTESTE", 3, new BigDecimal("4.0000"), 30, 30, true));
    fecharCaixaSeAberto();
  }

  // ================================================================== recebimento x caixa x banco

  @Test
  void dinheiroExigeCaixaAbertoEEntraNoCaixaFisico() {
    var v = venda(FormaPagamento.DINHEIRO, 1, 1);
    como(gerente);
    assertThatThrownBy(() -> receber(v, "1000.00", null)).isInstanceOf(NegocioException.class)
        .hasMessageContaining("abra o caixa");

    caixa.abrir(new BigDecimal("100.00"));
    receber(v, "1000.00", null);
    var s = caixa.atual();
    assertThat(s.entradas()).isEqualByComparingTo("1000.00");
    assertThat(s.saldoEsperado()).isEqualByComparingTo("1100.00");
    var l = lancamentos.findByPedidoIdOrderByIdAsc(v);
    assertThat(l).hasSize(1);
    assertThat(l.get(0).getConta()).isEqualTo(ContaLivro.CAIXA);
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PAGO);

    // fechamento diário: diferença exige motivo e fica registrada
    assertThatThrownBy(() -> caixa.fechar(new BigDecimal("1090.00"), null)).isInstanceOf(NegocioException.class)
        .hasMessageContaining("motivo");
    var fechado = caixa.fechar(new BigDecimal("1090.00"), "Troco dado a maior");
    assertThat(fechado.status()).isEqualTo(StatusSessaoCaixa.FECHADA);
    assertThat(fechado.diferenca()).isEqualByComparingTo("-10.00");
    assertThat(fechado.motivoDiferenca()).isEqualTo("Troco dado a maior");
    assertThat(caixa.atual()).isNull();
  }

  @Test
  void pixNaoAumentaOCaixaFisico() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    caixa.abrir(BigDecimal.ZERO);
    receber(v, "1000.00", null);
    var l = lancamentos.findByPedidoIdOrderByIdAsc(v);
    assertThat(l).hasSize(1);
    assertThat(l.get(0).getConta()).isEqualTo(ContaLivro.BANCO);
    assertThat(caixa.atual().entradas()).isEqualByComparingTo("0");
    assertThat(caixa.atual().saldoEsperado()).isEqualByComparingTo("0.00");
  }

  @Test
  void cartaoGeraRecebiveisSemEntradaNoBancoAteALiquidacao() {
    var v = venda(FormaPagamento.CARTAO, 3, 1);   // 1000 + 10% = 1100
    como(gerente);
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1100.00"), null, "NSU1", "OPTESTE", null),
        chave());
    assertThat(r.recebiveis()).hasSize(3);
    // bruto 366,66 + 366,66 + 366,68 = 1100,00 (a última absorve a diferença); taxa 4% sobre cada parcela
    assertThat(r.recebiveis()).extracting(x -> x.valorBruto().toPlainString()).containsExactly("366.66", "366.66", "366.68");
    assertThat(r.recebiveis()).extracting(x -> x.valorTaxa().toPlainString()).containsExactly("14.67", "14.67", "14.67");
    assertThat(r.recebiveis()).extracting(x -> x.valorLiquido().toPlainString()).containsExactly("351.99", "351.99", "352.01");
    LocalDate base = relogio.hoje();
    assertThat(r.recebiveis()).extracting(x -> x.dataPrevista()).containsExactly(base.plusDays(30), base.plusDays(60), base.plusDays(90));
    // a venda está paga pelo cliente, mas NENHUM dinheiro entrou ainda no banco nem no caixa
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PAGO);
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).isEmpty();

    // liquidação: entrada efetiva no banco, uma única vez por parcela
    Long p1 = r.recebiveis().get(0).id();
    String k = chave();
    var liq = cartao.liquidar(p1, null, null, k);
    assertThat(liq.status()).isEqualTo(StatusRecebivel.LIQUIDADO);
    var depois = lancamentos.findByPedidoIdOrderByIdAsc(v);
    assertThat(depois).hasSize(1);
    assertThat(depois.get(0).getConta()).isEqualTo(ContaLivro.BANCO);
    assertThat(depois.get(0).getValor()).isEqualByComparingTo("351.99");
    // repetição não duplica; outra chave é recusada
    cartao.liquidar(p1, null, null, k);
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);
    assertThatThrownBy(() -> cartao.liquidar(p1, null, null, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("já foi liquidada");

    // estorno da liquidação: lançamento oposto; a parcela volta a ser prevista; repetição idempotente
    String ke = chave();
    cartao.estornarLiquidacao(p1, "Liquidação lançada por engano", ke);
    cartao.estornarLiquidacao(p1, "Liquidação lançada por engano", ke);
    var apos = lancamentos.findByPedidoIdOrderByIdAsc(v);
    assertThat(apos).hasSize(2);
    assertThat(apos.get(1).getTipo()).isEqualTo(TipoLancamento.SAIDA);
    assertThat(apos.get(1).getEstorna().getId()).isEqualTo(apos.get(0).getId());
    assertThat(cartao.listar(StatusRecebivel.PREVISTO, "OPTESTE", null, null)).extracting(x -> x.id()).contains(p1);
  }

  @Test
  void liquidacaoDivergenteRegistraDiferencaSemInventarValor() {
    var v = venda(FormaPagamento.CARTAO, 3, 1);
    como(gerente);
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1100.00"), null, null, "OPTESTE", null), chave());
    var liq = cartao.liquidar(r.recebiveis().get(0).id(), null, new BigDecimal("340.00"), chave());
    assertThat(liq.diferencaLiquidacao()).isEqualByComparingTo("-11.99");
    assertThatThrownBy(() -> cartao.liquidar(r.recebiveis().get(1).id(), null, new BigDecimal("999.00"), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("bruto");
  }

  @Test
  void cartaoSemTaxaCadastradaEhRegistradoEmPlanoManual() {
    var v = venda(FormaPagamento.CARTAO, 3, 1);
    como(gerente);
    assertThatThrownBy(() -> recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("500.00"), null, null,
        "OPTESTE", null), chave())).isInstanceOf(NegocioException.class).hasMessageContaining("valor total");
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PENDENTE);
    // operadora sem taxa cadastrada (ou sem operadora): não bloqueia; vira plano manual, sem taxa inventada
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1100.00"), null, null, "OUTRA-OP", null), chave());
    assertThat(r.planoManual()).isTrue();
    assertThat(r.recebiveis()).hasSize(1);
    assertThat(r.recebiveis().get(0).valorTaxa()).isEqualByComparingTo("0");
    assertThat(r.recebiveis().get(0).valorLiquido()).isEqualByComparingTo("1100.00");
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PAGO);
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).isEmpty();   // entra no banco só quando alguém informar a liquidação
    // liquidação manual com o valor realmente depositado: a diferença fica registrada
    var liq = cartao.liquidar(r.recebiveis().get(0).id(), null, new BigDecimal("1050.00"), chave());
    assertThat(liq.diferencaLiquidacao()).isEqualByComparingTo("-50.00");
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);
    como(admin);
    assertThat(fechamento.previa(Relogio.texto(relogio.hoje())).resultado().faltantes()).anyMatch(f -> f.contains("plano manual"));
  }

  @Test
  void formaEhEscolhidaNoPagamentoSeNaoMudaOPreco() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    caixa.abrir(BigDecimal.ZERO);
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null, null, null,
        FormaPagamento.DINHEIRO, null, null), chave());
    assertThat(r.forma()).isEqualTo(FormaPagamento.DINHEIRO);
    assertThat(vendas.obter(v).pagamento().forma()).isEqualTo(FormaPagamento.DINHEIRO);   // a venda passa a refletir o real
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v).get(0).getConta()).isEqualTo(ContaLivro.CAIXA);
  }

  @Test
  void trocarParaFormaQueMudariaOPrecoEhRecusada_eFormaFicaTravadaDepoisDoPrimeiroRecebimento() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    assertThatThrownBy(() -> recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null,
        "OPTESTE", null, FormaPagamento.CARTAO, 3, br.com.lojaspopular.domain.financeiro.enums.TipoCartao.CREDITO), chave()))
        .isInstanceOf(NegocioException.class).hasMessageContaining("mudaria o preço");
    receber(v, "400.00", null);
    assertThatThrownBy(() -> recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("600.00"), null, null,
        null, null, FormaPagamento.DINHEIRO, null, null), chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("única forma");
  }

  @Test
  void cartaoExigeCreditoOuDebito_debitoEhSempreAVista() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(admin);
    config.atualizarCondicao(condicoes.findByFormaAndParcelas(FormaPagamento.CARTAO, 1).orElseThrow().getId(), BigDecimal.ZERO, true);
    como(gerente);
    assertThatThrownBy(() -> recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null,
        "OPTESTE", null, FormaPagamento.CARTAO, 1, null), chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("crédito ou débito");
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null, "OPTESTE", null,
        FormaPagamento.CARTAO, 3, br.com.lojaspopular.domain.financeiro.enums.TipoCartao.DEBITO), chave());   // 3 ignorado: débito = 1x
    assertThat(r.tipoCartao()).isEqualTo(br.com.lojaspopular.domain.financeiro.enums.TipoCartao.DEBITO);
    assertThat(r.parcelas()).isEqualTo(1);
    assertThat(vendas.obter(v).pagamento().forma()).isEqualTo(FormaPagamento.CARTAO);
    como(admin);
    garantirCondicao(FormaPagamento.CARTAO, 1, "10.00");
  }

  @Test
  void umaFormaPorVenda_valorNaoExcedeOSaldo_eStatusParcial() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    assertThatThrownBy(() -> receber(v, "1000.01", null)).isInstanceOf(NegocioException.class).hasMessageContaining("excede");
    receber(v, "400.00", null);
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PARCIAL);
    assertThat(vendas.obter(v).pagamento().saldo()).isEqualByComparingTo("600.00");
    receber(v, "600.00", null);
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PAGO);
    assertThatThrownBy(() -> receber(v, "1.00", null)).isInstanceOf(NegocioException.class).hasMessageContaining("excede");
    // a forma é sempre a da venda: nenhum campo permite misturar formas
    assertThat(vendas.obter(v).pagamento().recebimentos()).allMatch(x -> x.forma() == FormaPagamento.PIX);
  }

  @Test
  void recebimentoRepetidoNaoDuplica() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    String k = chave();
    var a = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null, null, null), k);
    var b = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null, null, null), k);
    assertThat(b.id()).isEqualTo(a.id());
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);
    assertThat(vendas.obter(v).pagamento().recebido()).isEqualByComparingTo("1000.00");
    var outra = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    assertThatThrownBy(() -> recebimentos.registrar(outra, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null,
        null, null, null), k)).isInstanceOf(NegocioException.class).hasMessageContaining("outra venda");
  }

  @Test
  void estornoDeRecebimentoPreservaTrilhaESaldoEEhIdempotente() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    var r = receber(v, "1000.00", null);
    String k = chave();
    assertThatThrownBy(() -> recebimentos.estornar(r.id(), " ", k)).isInstanceOf(NegocioException.class);
    recebimentos.estornar(r.id(), "Pix lançado na venda errada", k);
    recebimentos.estornar(r.id(), "Pix lançado na venda errada", k);   // repetição idempotente
    var l = lancamentos.findByPedidoIdOrderByIdAsc(v);
    assertThat(l).hasSize(2);
    assertThat(l.get(0).getTipo()).isEqualTo(TipoLancamento.ENTRADA);
    assertThat(l.get(1).getTipo()).isEqualTo(TipoLancamento.SAIDA);
    assertThat(l.get(1).getEstorna().getId()).isEqualTo(l.get(0).getId());
    var d = vendas.obter(v);
    assertThat(d.statusPagamento()).isEqualTo(StatusPagamento.PENDENTE);
    assertThat(d.pagamento().recebimentos().get(0).status()).isEqualTo(StatusRecebimento.ESTORNADO);
    assertThatThrownBy(() -> recebimentos.estornar(r.id(), "de novo", chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("já foi estornado");
    // pode receber de novo depois do estorno
    receber(v, "1000.00", null);
    assertThat(vendas.obter(v).statusPagamento()).isEqualTo(StatusPagamento.PAGO);
  }

  @Test
  void estornoDeDinheiroDevolveASaidaAoCaixaAtual() {
    var v = venda(FormaPagamento.DINHEIRO, 1, 1);
    como(gerente);
    caixa.abrir(BigDecimal.ZERO);
    var r = receber(v, "1000.00", null);
    recebimentos.estornar(r.id(), "Cliente desistiu do pagamento", chave());
    assertThat(caixa.atual().saldoEsperado()).isEqualByComparingTo("0.00");
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(2);
  }

  @Test
  void saidaCondicionadaAQuitacaoQuandoD05ExigePagamento() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), true);
    como(gerente);
    expedicao.agendarEntrega(v, LocalDate.now().plusDays(1), PeriodoAgenda.MANHA, null, null);
    assertThatThrownBy(() -> expedicao.registrarSaida(v, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("pagamento quitado");
    receber(v, "400.00", null);
    assertThatThrownBy(() -> expedicao.registrarSaida(v, chave())).isInstanceOf(NegocioException.class);   // parcial não basta
    receber(v, "600.00", null);
    expedicao.registrarSaida(v, chave());
    assertThat(vendas.obter(v).statusEntrega().name()).isEqualTo("SAIU");
  }

  @Test
  void vendaComRecebimentoNaoPodeSerCanceladaPorEsteCaminho() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    receber(v, "100.00", null);
    assertThatThrownBy(() -> vendas.cancelar(v, "Desistência")).isInstanceOf(NegocioException.class)
        .hasMessageContaining("recebimentos");
    assertThat(vendas.obter(v).acoes().podeCancelar()).isFalse();
  }

  @Test
  void vendedorNaoOperaOFinanceiro() {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(vendedor);
    assertThatThrownBy(() -> receber(v, "10.00", null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> caixa.abrir(BigDecimal.ZERO)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> contas.criar(contaReq(TipoConta.PAGAR, "100.00", relogio.hoje()))).isInstanceOf(AccessDeniedException.class);
  }

  // ================================================================== concorrência

  @Test
  void recebimentosSimultaneosDaMesmaChaveGeramUmSoLancamento() throws Exception {
    var v = venda(FormaPagamento.PIX, 1, 1);
    String k = chave();
    var resultados = executarEmParalelo(2, () -> {
      como(gerente);
      return recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null, null, null), k).id();
    });
    assertThat(resultados.stream().distinct().count()).isEqualTo(1);
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);
  }

  @Test
  void recebimentosSimultaneosComChavesDiferentesNaoExcedemOSaldo() throws Exception {
    var v = venda(FormaPagamento.PIX, 1, 1);
    int[] ok = {0};
    var resultados = executarEmParalelo(2, () -> {
      como(gerente);
      try {
        recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1000.00"), null, null, null, null), chave());
        return 1L;
      } catch (NegocioException e) {
        return 0L;
      }
    });
    assertThat(resultados.stream().mapToLong(x -> x).sum()).isEqualTo(1);
    assertThat(vendas.obter(v).pagamento().recebido()).isEqualByComparingTo("1000.00");
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);
  }

  @Test
  void estornosSimultaneosDoMesmoRecebimentoEstornamUmaVez() throws Exception {
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    var r = receber(v, "1000.00", null);
    var resultados = executarEmParalelo(2, () -> {
      como(gerente);
      try {
        recebimentos.estornar(r.id(), "Estorno simultâneo", chave());
        return 1L;
      } catch (NegocioException e) {
        return 0L;
      }
    });
    assertThat(resultados.stream().mapToLong(x -> x).sum()).isEqualTo(1);
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(2);
  }

  @Test
  void baixasSimultaneasDaMesmaContaPagamUmaVez() throws Exception {
    como(gerente);
    var c = contas.criar(contaReq(TipoConta.PAGAR, "300.00", relogio.hoje()));
    var resultados = executarEmParalelo(2, () -> {
      como(gerente);
      try {
        contas.baixar(c.id(), ContaLivro.BANCO, null, chave());
        return 1L;
      } catch (NegocioException e) {
        return 0L;
      }
    });
    assertThat(resultados.stream().mapToLong(x -> x).sum()).isEqualTo(1);
    assertThat(lancamentos.findAll().stream().filter(l -> l.getContaFinanceira() != null
        && l.getContaFinanceira().getId().equals(c.id()))).hasSize(1);
  }

  // ================================================================== contas a pagar / receber

  @Test
  void contaPagaUmaVezEEstornoPreservaTrilha() {
    como(gerente);
    var c = contas.criar(contaReq(TipoConta.PAGAR, "250.00", relogio.hoje().plusDays(5)));
    assertThat(c.situacao()).isEqualTo(SituacaoConta.ABERTA);
    String k = chave();
    var paga = contas.baixar(c.id(), ContaLivro.BANCO, null, k);
    contas.baixar(c.id(), ContaLivro.BANCO, null, k);   // repetição idempotente
    assertThat(paga.situacao()).isEqualTo(SituacaoConta.PAGA);
    assertThatThrownBy(() -> contas.baixar(c.id(), ContaLivro.BANCO, null, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("já foi baixada");
    var movs = lancamentos.findAll().stream().filter(l -> l.getContaFinanceira() != null && l.getContaFinanceira().getId().equals(c.id())).toList();
    assertThat(movs).hasSize(1);
    assertThat(movs.get(0).getTipo()).isEqualTo(TipoLancamento.SAIDA);

    String ke = chave();
    var estornada = contas.estornar(c.id(), "Pagamento feito em duplicidade", ke);
    contas.estornar(c.id(), "Pagamento feito em duplicidade", ke);
    assertThat(estornada.situacao()).isEqualTo(SituacaoConta.ABERTA);
    assertThat(estornada.eventos()).extracting(e -> e.tipo().name()).containsExactly("CRIADA", "PAGA", "ESTORNADA");
    var apos = lancamentos.findAll().stream().filter(l -> l.getContaFinanceira() != null && l.getContaFinanceira().getId().equals(c.id())).toList();
    assertThat(apos).hasSize(2);
    assertThat(apos.stream().map(l -> l.getTipo() == TipoLancamento.ENTRADA ? l.getValor() : l.getValor().negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");   // saldo correto: pagou e estornou
    // paga de novo e só então pode ser cancelada se estiver em aberto
    contas.baixar(c.id(), ContaLivro.BANCO, null, chave());
    assertThatThrownBy(() -> contas.cancelar(c.id(), "x")).isInstanceOf(NegocioException.class);
  }

  @Test
  void contaAReceberEntraNoCaixaESaidaNaoPodeDeixarCaixaNegativo() {
    como(gerente);
    caixa.abrir(new BigDecimal("50.00"));
    var pagar = contas.criar(contaReq(TipoConta.PAGAR, "80.00", relogio.hoje()));
    assertThatThrownBy(() -> contas.baixar(pagar.id(), ContaLivro.CAIXA, null, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("Saldo insuficiente");
    var receber = contas.criar(contaReq(TipoConta.RECEBER, "100.00", relogio.hoje()));
    contas.baixar(receber.id(), ContaLivro.CAIXA, null, chave());
    contas.baixar(pagar.id(), ContaLivro.CAIXA, null, chave());
    assertThat(caixa.atual().saldoEsperado()).isEqualByComparingTo("70.00");
    assertThatThrownBy(() -> caixa.movimentar(TipoMovimentoCaixa.RETIRADA, new BigDecimal("500.00"), "Banco", chave()))
        .isInstanceOf(NegocioException.class);
  }

  // ================================================================== comissões

  @Test
  void semD01NadaEhCalculado_nemZeroNemEstimativa() {
    financeira(null, null, null, EnumSet.of(Role.ADMIN), EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(admin);
    var resumo = comissoes.listar(vendedor.getId(), null);
    assertThat(resumo.percentualDefinido()).isFalse();
    assertThat(resumo.itens()).noneMatch(i -> i.pedidoId().equals(v));
    assertThat(resumo.avisos()).anyMatch(a -> a.contains("D01"));
    assertThatThrownBy(() -> comissoes.gerarPrevisoes()).isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D01");
  }

  @Test
  void semD02ComissaoPermaneceNaPrevisaoEOPagamentoFicaBloqueado() {
    financeira(new BigDecimal("5.00"), null, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    receber(v, "1000.00", null);
    como(admin);
    var item = comissoes.listar(vendedor.getId(), null).itens().stream().filter(i -> i.pedidoId().equals(v)).findFirst().orElseThrow();
    assertThat(item.status()).isEqualTo(StatusComissao.PREVISTA);
    assertThat(item.valor()).isEqualByComparingTo("50.00");
    assertThatThrownBy(() -> comissoes.gerarPagamento(vendedor.getId(), relogio.hoje().plusDays(10)))
        .isInstanceOf(NegocioException.class).hasMessageContaining("Não há comissões devidas");
    assertThat(comissoes.listar(vendedor.getId(), null).avisos()).anyMatch(a -> a.contains("D02"));
  }

  @Test
  void comissaoPrevistaDevidaPagaComRegraHistoricaPorVenda() {
    como(admin);
    var v = venda(FormaPagamento.PIX, 1, 1);   // D01 = 5%, D02 = quitação
    como(admin);
    var prev = comissoes.listar(vendedor.getId(), null).itens().stream().filter(i -> i.pedidoId().equals(v)).findFirst().orElseThrow();
    assertThat(prev.status()).isEqualTo(StatusComissao.PREVISTA);
    assertThat(prev.percentual()).isEqualByComparingTo("5.00");
    assertThat(prev.base()).isEqualByComparingTo("1000.00");

    como(gerente);
    receber(v, "500.00", null);   // pagamento parcial ainda não adquire a comissão
    como(admin);
    assertThat(item(v).status()).isEqualTo(StatusComissao.PREVISTA);
    como(gerente);
    receber(v, "500.00", null);
    como(admin);
    var devida = item(v);
    assertThat(devida.status()).isEqualTo(StatusComissao.DEVIDA);
    assertThat(devida.competencia()).isEqualTo(relogio.hoje().withDayOfMonth(1));

    // mudar o percentual depois NÃO altera a regra histórica da venda já registrada
    financeira(new BigDecimal("8.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    assertThat(item(v).percentual()).isEqualByComparingTo("5.00");
    assertThat(item(v).valor()).isEqualByComparingTo("50.00");
    var v2 = venda(FormaPagamento.PIX, 1, 1);
    como(admin);
    assertThat(item(v2).percentual()).isEqualByComparingTo("8.00");

    // pagar = conta a pagar com vencimento informado (a data não é presumida)
    var conta = comissoes.gerarPagamento(vendedor.getId(), relogio.hoje().plusDays(7));
    assertThat(conta.valor()).isEqualByComparingTo("50.00");   // só a devida; v2 ainda é previsão
    assertThat(item(v).status()).isEqualTo(StatusComissao.EM_CONTA);
    como(gerente);
    contas.baixar(conta.id(), ContaLivro.BANCO, null, chave());
    como(admin);
    assertThat(item(v).status()).isEqualTo(StatusComissao.PAGA);
    como(gerente);
    contas.estornar(conta.id(), "Pagamento devolvido pelo banco", chave());
    como(admin);
    assertThat(item(v).status()).isEqualTo(StatusComissao.EM_CONTA);
  }

  @Test
  void cancelamentoRevertemAComissaoAindaNaoPaga() {
    como(admin);
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    vendas.cancelar(v, "Desistência");
    como(admin);
    var itens = comissoes.listar(vendedor.getId(), null).itens().stream().filter(i -> i.pedidoId().equals(v)).toList();
    assertThat(itens).extracting(i -> i.tipo()).containsExactlyInAnyOrder(TipoComissao.PREVISAO, TipoComissao.REVERSAO);
    assertThat(itens.stream().filter(i -> i.tipo() == TipoComissao.PREVISAO).findFirst().orElseThrow().status())
        .isEqualTo(StatusComissao.REVERTIDA);
    var rev = itens.stream().filter(i -> i.tipo() == TipoComissao.REVERSAO).findFirst().orElseThrow();
    assertThat(rev.valor()).isEqualByComparingTo("-50.00");
    assertThat(rev.status()).isEqualTo(StatusComissao.COMPENSADA);
  }

  // ================================================================== restituição (D09) e devolução

  @Test
  void restituicaoSoExisteConformeD09EDevolucaoFisicaTemControleProprio() {
    var v = vendaEntregue(FormaPagamento.PIX, 2);   // 2 x 1000 pagos via Pix
    como(gerente);
    long oc = abrirDevolucao(v, 1);

    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), null, true, true, false);
    como(gerente);
    assertThatThrownBy(() -> restituicoes.solicitar(oc, new BigDecimal("100.00"), "Devolução"))
        .isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D09");
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), false, true, true, false);
    como(gerente);
    assertThatThrownBy(() -> restituicoes.solicitar(oc, new BigDecimal("100.00"), "Devolução"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não permite");
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    como(gerente);

    // sem a devolução física recebida e avaliada não há restituição
    assertThatThrownBy(() -> restituicoes.solicitar(oc, new BigDecimal("100.00"), "Devolução"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("devolução física");
    posVenda.receberDevolucao(oc, CondicaoFisica.APTA_REVENDA, null, chave());
    assertThat(restituicoes.daOcorrencia(oc)).isEmpty();   // o recebimento físico não restitui nada sozinho
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);   // só a entrada do Pix
  }

  @Test
  void restituicaoPixSolicitaAutorizaEfetivaUmaVezELimitesSaoRespeitados() {
    var v = vendaEntregue(FormaPagamento.PIX, 2);
    como(gerente);
    long oc = abrirDevolucao(v, 1);
    posVenda.receberDevolucao(oc, CondicaoFisica.APTA_REVENDA, null, chave());

    assertThatThrownBy(() -> restituicoes.solicitar(oc, new BigDecimal("1000.01"), "Acima do item"))
        .isInstanceOf(NegocioException.class).hasMessageContaining("limite");
    var r = restituicoes.solicitar(oc, new BigDecimal("1000.00"), "Devolução de 1 un.");
    assertThat(r.status()).isEqualTo(StatusRestituicao.SOLICITADA);
    assertThatThrownBy(() -> restituicoes.efetivar(r.id(), null, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("autorizada");
    // o solicitante (gerente) não autoriza a própria restituição; o proprietário autoriza
    assertThatThrownBy(() -> restituicoes.autorizar(r.id())).isInstanceOf(NegocioException.class).hasMessageContaining("própria");
    como(admin);
    assertThat(restituicoes.autorizar(r.id()).status()).isEqualTo(StatusRestituicao.AUTORIZADA);
    como(gerente);
    String k = chave();
    var ef = restituicoes.efetivar(r.id(), null, k);
    restituicoes.efetivar(r.id(), null, k);   // repetição idempotente
    assertThat(ef.status()).isEqualTo(StatusRestituicao.EFETIVADA);
    var l = lancamentos.findByPedidoIdOrderByIdAsc(v);
    assertThat(l).hasSize(2);
    assertThat(l.get(1).getTipo()).isEqualTo(TipoLancamento.SAIDA);
    assertThat(l.get(1).getConta()).isEqualTo(ContaLivro.BANCO);
    assertThatThrownBy(() -> restituicoes.efetivar(r.id(), null, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("já foi efetivada");
    // a mesma ocorrência não restitui além do item
    assertThatThrownBy(() -> restituicoes.solicitar(oc, new BigDecimal("1.00"), "Mais")).isInstanceOf(NegocioException.class);
    // não é possível estornar um recebimento cujo valor já foi restituído além do restante
    var rec = vendas.obter(v).pagamento().recebimentos().get(0);
    assertThatThrownBy(() -> recebimentos.estornar(rec.id(), "Erro", chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("restituídos");
  }

  @Test
  void restituicaoSemPerfisDeAutorizacaoD07FicaBloqueada() {
    var v = vendaEntregue(FormaPagamento.PIX, 1);
    como(gerente);
    long oc = abrirDevolucao(v, 1);
    posVenda.receberDevolucao(oc, CondicaoFisica.APTA_REVENDA, null, chave());
    var r = restituicoes.solicitar(oc, new BigDecimal("500.00"), "Parcial");
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        Set.of(), true, true, true, false);
    como(admin);
    assertThatThrownBy(() -> restituicoes.autorizar(r.id())).isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D07");
  }

  @Test
  void restituicaoEmDinheiroSaiDoCaixaEExigeCaixaAberto() {
    como(gerente);
    caixa.abrir(BigDecimal.ZERO);
    var v = vendaEntregue(FormaPagamento.DINHEIRO, 1);
    como(gerente);
    long oc = abrirDevolucao(v, 1);
    posVenda.receberDevolucao(oc, CondicaoFisica.APTA_REVENDA, null, chave());
    var r = restituicoes.solicitar(oc, new BigDecimal("300.00"), "Parcial");
    como(admin);
    restituicoes.autorizar(r.id());
    como(gerente);
    caixa.fechar(caixa.atual().saldoEsperado(), null);
    assertThatThrownBy(() -> restituicoes.efetivar(r.id(), null, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("abra o caixa");
    caixa.abrir(new BigDecimal("500.00"));
    restituicoes.efetivar(r.id(), null, chave());
    assertThat(caixa.atual().saldoEsperado()).isEqualByComparingTo("200.00");
  }

  @Test
  void restituicaoDeCartaoReduzOsRecebiveisPrevistosSemMovimentarBanco() {
    var v = vendaEntregue(FormaPagamento.CARTAO, 3, 1);   // total 1100 em 3x
    como(gerente);
    long oc = abrirDevolucao(v, 1);
    posVenda.receberDevolucao(oc, CondicaoFisica.APTA_REVENDA, null, chave());
    var r = restituicoes.solicitar(oc, new BigDecimal("400.00"), "Devolução parcial");
    como(admin);
    restituicoes.autorizar(r.id());
    como(gerente);
    restituicoes.efetivar(r.id(), null, chave());
    var parcelas = vendas.obter(v).pagamento().recebimentos().get(0).recebiveis();
    // reduz a última parcela primeiro (366,68) e depois a anterior
    assertThat(parcelas.stream().map(x -> x.valorBruto()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("700.00");
    assertThat(parcelas.get(2).status()).isEqualTo(StatusRecebivel.CANCELADO);
    assertThat(lancamentos.findByPedidoIdOrderByIdAsc(v)).isEmpty();   // nada saiu do banco: nada tinha entrado
  }

  @Test
  void cobrancaDaDiferencaDeTrocaSoConformeD09() {
    var v = vendaEntregue(FormaPagamento.PIX, 1);
    como(gerente);
    var maior = produtoCaro("1300.00");
    var itemId = vendas.obter(v).itens().get(0).id();
    var o = posVenda.abrir(v, new AbrirOcorrenciaRequest(TipoOcorrencia.TROCA, "Modelo maior", itemId, 1,
        new TrocaRequest(maior.getId(), maior.getVariacoes().get(0).getId(), 1)));
    assertThat(o.diferencaCalculada()).isEqualByComparingTo("300.00");
    assertThatThrownBy(() -> restituicoes.cobrarDiferenca(o.id(), relogio.hoje().plusDays(3))).isInstanceOf(NegocioException.class)
        .hasMessageContaining("devolução física");
    posVenda.receberDevolucao(o.id(), CondicaoFisica.APTA_REVENDA, null, chave());

    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, null, true, false);
    como(gerente);
    assertThatThrownBy(() -> restituicoes.cobrarDiferenca(o.id(), relogio.hoje().plusDays(3)))
        .isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D09");
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    como(gerente);
    var conta = restituicoes.cobrarDiferenca(o.id(), relogio.hoje().plusDays(3));
    assertThat(conta.tipo()).isEqualTo(TipoConta.RECEBER);
    assertThat(conta.valor()).isEqualByComparingTo("300.00");
    assertThatThrownBy(() -> restituicoes.cobrarDiferenca(o.id(), relogio.hoje().plusDays(3))).isInstanceOf(NegocioException.class)
        .hasMessageContaining("já foi cobrada");
    // o pedido original não muda: a diferença é uma conta própria
    assertThat(vendas.obter(v).total()).isEqualByComparingTo("1000.00");
    contas.baixar(conta.id(), ContaLivro.BANCO, null, chave());
  }

  @Test
  void reversaoDeComissaoDepoisDoPagamentoCompensaNoProximoPagamento() {
    como(admin);
    var a = vendaEntregue(FormaPagamento.PIX, 2);   // comissão 5% de 2000 = 100, devida após a quitação
    como(admin);
    var contaA = comissoes.gerarPagamento(vendedor.getId(), relogio.hoje().plusDays(5));
    assertThat(contaA.valor()).isEqualByComparingTo("100.00");
    como(gerente);
    contas.baixar(contaA.id(), ContaLivro.BANCO, null, chave());
    assertThat(itemDe(a).status()).isEqualTo(StatusComissao.PAGA);

    long oc = abrirDevolucao(a, 1);
    posVenda.receberDevolucao(oc, CondicaoFisica.APTA_REVENDA, null, chave());
    var r = restituicoes.solicitar(oc, new BigDecimal("1000.00"), "Devolução de 1 un.");
    como(admin);
    restituicoes.autorizar(r.id());
    como(gerente);
    restituicoes.efetivar(r.id(), null, chave());
    como(admin);
    var rev = comissoes.listar(vendedor.getId(), null).itens().stream()
        .filter(i -> i.pedidoId().equals(a) && i.tipo() == TipoComissao.REVERSAO).findFirst().orElseThrow();
    assertThat(rev.valor()).isEqualByComparingTo("-50.00");   // 5% de 1000 restituídos, ligada à venda original
    assertThat(rev.status()).isEqualTo(StatusComissao.LANCADA);
    assertThat(rev.reverteId()).isNotNull();
    assertThat(itemDe(a).status()).isEqualTo(StatusComissao.PAGA);   // o pagamento anterior não é reescrito

    // sem comissão nova devida, o saldo líquido é negativo e permanece para compensação
    assertThatThrownBy(() -> comissoes.gerarPagamento(vendedor.getId(), relogio.hoje().plusDays(5)))
        .isInstanceOf(NegocioException.class);
    var b = venda(FormaPagamento.PIX, 1, 2);   // nova comissão devida: 100,00
    como(gerente);
    receber(b, "2000.00", null);
    como(admin);
    var contaB = comissoes.gerarPagamento(vendedor.getId(), relogio.hoje().plusDays(5));
    assertThat(contaB.valor()).isEqualByComparingTo("50.00");   // 100,00 devidos - 50,00 de reversão compensada
    assertThat(comissoes.listar(vendedor.getId(), null).itens().stream()
        .filter(i -> i.tipo() == TipoComissao.REVERSAO).findFirst().orElseThrow().status()).isEqualTo(StatusComissao.EM_CONTA);
  }

  // ================================================================== metas

  @Test
  void metaMensalComAtingimentoProvisorioEnquantoD09Pendente() {
    como(admin);
    String mes = Relogio.texto(relogio.hoje());
    var v1 = venda(FormaPagamento.PIX, 1, 2);   // 2.000 vendidos
    como(gerente);
    metas.definir(vendedor.getId(), mes, new BigDecimal("4000.00"));
    var m = metas.listar(mes).stream().filter(x -> x.vendedorId().equals(vendedor.getId())).findFirst().orElseThrow();
    assertThat(m.meta()).isEqualByComparingTo("4000.00");
    assertThat(m.vendido()).isGreaterThanOrEqualTo(new BigDecimal("2000.00"));
    // cancelamentos não contam
    var c = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    var antes = metas.listar(mes).stream().filter(x -> x.vendedorId().equals(vendedor.getId())).findFirst().orElseThrow().vendido();
    vendas.cancelar(c, "Desistência");
    var depois = metas.listar(mes).stream().filter(x -> x.vendedorId().equals(vendedor.getId())).findFirst().orElseThrow().vendido();
    assertThat(antes.subtract(depois)).isEqualByComparingTo("1000.00");

    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, null, false);
    como(gerente);
    var prov = metas.listar(mes).stream().filter(x -> x.vendedorId().equals(vendedor.getId())).findFirst().orElseThrow();
    assertThat(prov.provisorio()).isTrue();
    assertThat(prov.politicaDevolucoes()).isEqualTo("PENDENTE_D09");
    assertThat(prov.observacao()).contains("D09");
    como(vendedor);
    assertThat(metas.minha(mes).meta()).isEqualByComparingTo("4000.00");
    assertThatThrownBy(() -> metas.definir(vendedor.getId(), mes, BigDecimal.TEN)).isInstanceOf(AccessDeniedException.class);
  }

  // ================================================================== fechamento mensal e bloqueio de período

  @Test
  void previaNaoApresentaLucroDefinitivoQuandoFaltamCustosECriterios() {
    como(admin);
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, null, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    como(admin);
    venda(FormaPagamento.PIX, 1, 1);
    var p = fechamento.previa(Relogio.texto(relogio.hoje()));
    assertThat(p.resultado().definitivo()).isFalse();
    assertThat(p.resultado().lucroApurado()).isNull();
    assertThat(p.resultado().itensSemCusto()).isGreaterThan(0);
    assertThat(p.resultado().criterioCompetencia()).isEqualTo("PROVISORIO_CONFIRMACAO");
    assertThat(p.resultado().faltantes()).anyMatch(f -> f.contains("D06")).anyMatch(f -> f.contains("custo"));
    assertThat(p.podeAprovar()).isFalse();   // mês corrente: ainda não terminou
    assertThat(p.bloqueiosAprovacao()).anyMatch(b -> b.contains("ainda não terminou"));
  }

  @Test
  void fechamentoBloqueiaLancamentosRetroativosEReaberturaExigeJustificativaEPerfil() {
    String mes = proximoMesPassado();
    LocalDate dia = Relogio.mes(mes).plusDays(14);
    var v = venda(FormaPagamento.PIX, 1, 1);
    como(gerente);
    receber(v, "300.00", dia);   // lançamento retroativo em mês ainda aberto
    var previa = fechamento.previa(mes);
    assertThat(previa.caixa().entradasBanco()).isEqualByComparingTo("300.00");
    assertThat(previa.caixa().entradasCaixa()).isEqualByComparingTo("0");   // caixa e banco separados

    // D10 pendente: a prévia existe, a aprovação não
    como(admin);
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, null);
    como(admin);
    assertThatThrownBy(() -> fechamento.aprovar(mes)).isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D10");
    como(gerente);
    assertThatThrownBy(() -> fechamento.aprovar(mes)).isInstanceOf(AccessDeniedException.class);

    como(admin);
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    como(admin);
    var aprovado = fechamento.aprovar(mes);
    assertThat(aprovado.fechado()).isTrue();
    assertThat(aprovado.versao()).isEqualTo(1);
    assertThatThrownBy(() -> fechamento.aprovar(mes)).isInstanceOf(NegocioException.class).hasMessageContaining("já está fechado");

    // período fechado: nenhum serviço aceita lançamento com data nele
    como(gerente);
    assertThatThrownBy(() -> receber(v, "100.00", dia)).isInstanceOf(NegocioException.class).hasMessageContaining("fechado");
    assertThatThrownBy(() -> contas.criar(contaReq(TipoConta.PAGAR, "10.00", dia))).isInstanceOf(NegocioException.class)
        .hasMessageContaining("fechado");
    var cOk = contas.criar(contaReq(TipoConta.PAGAR, "10.00", relogio.hoje()));
    assertThatThrownBy(() -> contas.baixar(cOk.id(), ContaLivro.BANCO, dia, chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("fechado");
    // lançar com a data atual continua possível: eventos posteriores entram no período atual
    receber(v, "100.00", null);

    // reabertura: D07 com perfis só do proprietário + justificativa obrigatória
    assertThatThrownBy(() -> fechamento.reabrir(mes, "Correção")).isInstanceOf(AccessDeniedException.class);
    como(admin);
    assertThatThrownBy(() -> fechamento.reabrir(mes, " ")).isInstanceOf(NegocioException.class).hasMessageContaining("justificativa");
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, Set.of(),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    como(admin);
    assertThatThrownBy(() -> fechamento.reabrir(mes, "Correção")).isInstanceOf(ConfiguracaoPendenteException.class).hasMessageContaining("D07");
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
    como(admin);
    var reaberto = fechamento.reabrir(mes, "Pix lançado na data errada");
    assertThat(reaberto.fechado()).isFalse();
    assertThat(reaberto.versoes().get(0).justificativaReabertura()).isEqualTo("Pix lançado na data errada");
    como(gerente);
    receber(v, "100.00", dia);   // período aberto de novo
    como(admin);
    var v2 = fechamento.aprovar(mes);
    assertThat(v2.versao()).isEqualTo(2);
    assertThat(v2.versoes()).hasSize(2);
  }

  @Test
  void aprovacaoComPendenciaBloqueanteEMesEncerradoExigeRegraD10() {
    String mes = proximoMesPassado();
    como(gerente);
    contas.criar(new ContaRequest(TipoConta.PAGAR, "Aluguel em atraso", "Teste", relogio.hoje(), new BigDecimal("100.00"),
        Relogio.mes(mes).plusDays(5), null));
    como(admin);
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, true);   // D10 = exigir ausência de pendências
    como(admin);
    var p = fechamento.previa(mes);
    assertThat(p.pendencias()).anyMatch(x -> x.codigo().equals("CONTA_VENCIDA") && x.quantidade() > 0 && x.bloqueante());
    assertThat(p.podeAprovar()).isFalse();
    assertThatThrownBy(() -> fechamento.aprovar(mes)).isInstanceOf(NegocioException.class).hasMessageContaining("pendência");
  }

  @Test
  void taxaDeCartaoEhDespesaPrevistaDaVendaESuaLiquidacaoFinanceiraApareceAparte() {
    como(admin);
    String mes = Relogio.texto(relogio.hoje());
    var antes = fechamento.previa(mes).resultado();
    var v = venda(FormaPagamento.CARTAO, 3, 1);
    como(admin);
    var semPagamento = fechamento.previa(mes).resultado();
    assertThat(semPagamento.faltantes()).anyMatch(f -> f.contains("sem recebimento registrado"));
    assertThat(semPagamento.taxasCartao()).isEqualByComparingTo(antes.taxasCartao());   // taxa ainda desconhecida: nada inventado

    como(gerente);
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(new BigDecimal("1100.00"), null, null, "OPTESTE", null), chave());
    como(admin);
    var prev = fechamento.previa(mes).resultado();
    assertThat(prev.taxasCartao().subtract(antes.taxasCartao())).isEqualByComparingTo("44.01");           // prevista (3 x 14,67)
    assertThat(prev.taxasCartaoLiquidadas()).isEqualByComparingTo(antes.taxasCartaoLiquidadas());       // nada liquidado ainda
    assertThat(prev.criterios()).anyMatch(c -> c.contains("PREVISTA") && c.contains("liquidação financeira"));

    como(gerente);
    cartao.liquidar(r.recebiveis().get(0).id(), null, null, chave());
    como(admin);
    var liq = fechamento.previa(mes).resultado();
    assertThat(liq.taxasCartao().subtract(antes.taxasCartao())).isEqualByComparingTo("44.01");           // o resultado não muda
    assertThat(liq.taxasCartaoLiquidadas().subtract(antes.taxasCartaoLiquidadas())).isEqualByComparingTo("14.67");
    assertThat(liq.taxasCartaoEmAberto().subtract(antes.taxasCartaoEmAberto())).isEqualByComparingTo("29.34");
    assertThat(liq.resultadoParcial()).isEqualByComparingTo(prev.resultadoParcial());                    // liquidar não altera o resultado
  }

  @Test
  void resultadoNaoEhDefinitivoEExplicaOsCriteriosUsados() {
    como(admin);
    var p = fechamento.previa(Relogio.texto(relogio.hoje())).resultado();
    assertThat(p.definitivo()).isFalse();
    assertThat(p.criterios()).hasSize(3);
    assertThat(p.criterios().get(0)).startsWith("Receita:");
  }

  // ================================================================== config financeira

  @Test
  void regrasFinanceirasSaoDoProprietarioEPendenciasSaoListadas() {
    como(gerente);
    assertThatThrownBy(() -> config.atualizarFinanceiro(new ConfigFinanceiraRequest(null, null, null, null, null, null, null,
        null, null))).isInstanceOf(AccessDeniedException.class);
    como(admin);
    config.atualizarFinanceiro(new ConfigFinanceiraRequest(null, null, null, null, null, null, null, null, null));
    var codigos = config.pendencias().stream().filter(p -> "FINANCEIRO".equals(p.area())).map(p -> p.codigo()).distinct().toList();
    assertThat(codigos).contains("D01", "D02", "D06", "D07", "D09", "D10");
    assertThatThrownBy(() -> config.atualizarFinanceiro(new ConfigFinanceiraRequest(new BigDecimal("101"), null, null, null, null,
        null, null, null, null))).isInstanceOf(NegocioException.class);
    // GERENTE e ADMIN são os únicos perfis aceitáveis para autorizações financeiras
    assertThatThrownBy(() -> config.atualizarFinanceiro(new ConfigFinanceiraRequest(null, null, null, EnumSet.of(Role.VENDEDOR), null,
        null, null, null, null))).isInstanceOf(NegocioException.class);
    // restaura o estado de teste para os demais
    financeira(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO, CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN),
        EnumSet.of(Role.ADMIN, Role.GERENTE), true, true, true, false);
  }

  // ================================================================== helpers

  private void financeira(BigDecimal pct, AquisicaoComissao aq, CompetenciaReceita comp, Set<Role> reabertura, Set<Role> restituicao,
      Boolean permiteRest, Boolean permiteCobr, Boolean metaDesconta, Boolean fechExige) {
    User atual = autenticado();
    como(admin);
    config.atualizarFinanceiro(new ConfigFinanceiraRequest(pct, aq, comp, reabertura, restituicao, permiteRest, permiteCobr,
        metaDesconta, fechExige));
    if (atual != null) {
      como(atual);
    }
  }

  private User autenticado() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a == null ? null : users.findByEmail(a.getName()).orElse(null);
  }

  private String proximoMesPassado() {
    // um mês passado (2 a 60 meses atrás) sorteado por teste, para que o bloqueio de um não afete o outro
    long atras = 2 + (UUID.randomUUID().getMostSignificantBits() & 0x7FFFFFFFL) % 59;
    return Relogio.texto(Relogio.primeiroDia(relogio.hoje()).minusMonths(atras));
  }

  private br.com.lojaspopular.web.financeiro.FinanceiroDtos.RecebimentoView receber(Long pedido, String valor, LocalDate data) {
    return recebimentos.registrar(pedido, new RegistrarRecebimentoRequest(new BigDecimal(valor), data, null, null, null), chave());
  }

  private ContaRequest contaReq(TipoConta tipo, String valor, LocalDate competencia) {
    return new ContaRequest(tipo, "Conta de teste " + UUID.randomUUID().toString().substring(0, 6), "Teste", competencia,
        new BigDecimal(valor), relogio.hoje().plusDays(10), null);
  }

  private br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoView item(Long pedido) {
    return itemDe(pedido);
  }

  private br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoView itemDe(Long pedido) {
    return comissoes.listar(vendedor.getId(), null).itens().stream()
        .filter(i -> i.pedidoId().equals(pedido) && i.tipo() == TipoComissao.PREVISAO).findFirst().orElseThrow();
  }

  private void fecharCaixaSeAberto() {
    como(gerente);
    var atual = caixa.atual();
    if (atual != null) {
      caixa.fechar(atual.saldoEsperado(), null);
    }
  }

  /** Executa a tarefa em N threads ao mesmo tempo e devolve os resultados. */
  private List<Long> executarEmParalelo(int n, java.util.concurrent.Callable<Long> tarefa) throws Exception {
    CountDownLatch largada = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(n);
    List<Future<Long>> fs = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) {
      fs.add(pool.submit(() -> {
        largada.await();
        return tarefa.call();
      }));
    }
    largada.countDown();
    List<Long> out = new java.util.ArrayList<>();
    for (var f : fs) {
      out.add(f.get());
    }
    pool.shutdown();
    return out;
  }

  private Long venda(FormaPagamento forma, int parcelas, int qtd) {
    como(vendedor);
    var c = cliente();
    var p = produto(50);
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), qtd, ModalidadeItem.PRONTA_ENTREGA);
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null, forma, parcelas,
        null, null, null, null, List.of(item)));
    return vendas.confirmar(v.id(), chave()).id();
  }

  /** Venda confirmada, paga integralmente (forma da venda) e entregue; D05 definido como "não exige" nos testes. */
  private Long vendaEntregue(FormaPagamento forma, int qtd) {
    return vendaEntregue(forma, 1, qtd);
  }

  private Long vendaEntregue(FormaPagamento forma, int parcelas, int qtd) {
    Long v = venda(forma, parcelas, qtd);
    como(gerente);
    var total = vendas.obter(v).total();
    if (forma == FormaPagamento.CARTAO) {
      recebimentos.registrar(v, new RegistrarRecebimentoRequest(total, null, null, "OPTESTE", null), chave());
    } else {
      if (forma == FormaPagamento.DINHEIRO && caixa.atual() == null) {
        caixa.abrir(BigDecimal.ZERO);
      }
      recebimentos.registrar(v, new RegistrarRecebimentoRequest(total, null, null, null, null), chave());
    }
    expedicao.agendarEntrega(v, LocalDate.now().plusDays(1), PeriodoAgenda.MANHA, null, null);
    expedicao.registrarSaida(v, chave());
    expedicao.concluirEntrega(v, "Cliente", null, null);
    return v;
  }

  private long abrirDevolucao(Long pedido, int qtd) {
    var itemId = vendas.obter(pedido).itens().get(0).id();
    return posVenda.abrir(pedido, new AbrirOcorrenciaRequest(TipoOcorrencia.DEVOLUCAO, "Devolução de teste", itemId, qtd, null)).id();
  }

  private String chave() {
    return UUID.randomUUID().toString();
  }

  private void garantirCondicao(FormaPagamento forma, int parcelas, String ajuste) {
    var c = condicoes.findByFormaAndParcelas(forma, parcelas);
    if (c.isPresent()) {
      config.atualizarCondicao(c.get().getId(), new BigDecimal(ajuste), true);
    } else {
      config.criarCondicao(forma, parcelas, new BigDecimal(ajuste), true);
    }
  }

  private User usuario(String email, Role role) {
    return users.findByEmail(email).orElseGet(() -> users.save(User.builder().email(email).nome(email).passwordHash("x")
        .roles(Set.of(role)).enabled(true).createdAt(Instant.now()).build()));
  }

  private void como(User u) {
    var autoridades = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, autoridades));
  }

  private Cliente cliente() {
    return clientes.save(Cliente.builder().nome("Cliente " + UUID.randomUUID().toString().substring(0, 8)).build());
  }

  private Produto produto(int estoque) {
    String sufixo = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Guarda-roupa " + sufixo).preco(new BigDecimal("1000.00")).estoque(estoque)
        .sku("P-" + sufixo).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("Casal").sku("V-" + sufixo).adicionalPreco(BigDecimal.ZERO)
        .estoque(estoque).build());
    return produtos.save(p);
  }

  private Produto produtoCaro(String preco) {
    Produto p = produto(10);
    p.setPreco(new BigDecimal(preco));
    return produtos.save(p);
  }
}
