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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.financeiro.CustoService;
import br.com.lojaspopular.application.financeiro.FechamentoService;
import br.com.lojaspopular.application.financeiro.RecebimentoService;
import br.com.lojaspopular.application.financeiro.Relogio;
import br.com.lojaspopular.application.financeiro.RelatorioService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.repository.CondicaoPagamentoRepository;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.repository.ItemPedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RegistrarRecebimentoRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/** Custos (cadastro e custo histórico do item) e relatórios gerenciais. */
@SpringBootTest
@ActiveProfiles("test")
class CustoRelatorioTest {

  @Autowired CustoService custos;
  @Autowired RelatorioService relatorios;
  @Autowired FechamentoService fechamento;
  @Autowired VendaService vendas;
  @Autowired RecebimentoService recebimentos;
  @Autowired ConfiguracaoComercialService config;
  @Autowired Relogio relogio;
  @Autowired UserRepository users;
  @Autowired ClienteRepository clientes;
  @Autowired ProdutoRepository produtos;
  @Autowired CondicaoPagamentoRepository condicoes;
  @Autowired ItemPedidoRepository itens;

  User admin;
  User gerente;
  User vendedor;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-c@loja.com", Role.ADMIN);
    gerente = usuario("gerente-c@loja.com", Role.GERENTE);
    vendedor = usuario("vendedor-c-" + UUID.randomUUID().toString().substring(0, 8) + "@loja.com", Role.VENDEDOR);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), false);
    var c = condicoes.findByFormaAndParcelas(FormaPagamento.PIX, 1);
    if (c.isPresent()) {
      config.atualizarCondicao(c.get().getId(), BigDecimal.ZERO, true);
    } else {
      config.criarCondicao(FormaPagamento.PIX, 1, BigDecimal.ZERO, true);
    }
  }

  @Test
  void semCustoCadastradoOItemFicaNuloENuncaZero() {
    var p = produto();
    var v = venda(p, 2);
    var item = itens.findById(vendas.obter(v).itens().get(0).id()).orElseThrow();
    assertThat(item.getCustoUnitario()).isNull();
    assertThat(item.getCustoOrigem()).isNull();
    como(gerente);
    assertThat(custos.itensSemCusto()).anyMatch(i -> i.pedidoId().equals(v));
  }

  @Test
  void custoDoCatalogoEhCongeladoNaConfirmacaoEMudancaPosteriorNaoAlteraVendaAntiga() {
    var p = produto();
    como(admin);
    custos.registrar(p.getId(), null, new BigDecimal("600.00"), null, "Nota de compra 1");
    var v1 = venda(p, 1);
    como(admin);
    custos.registrar(p.getId(), null, new BigDecimal("700.00"), null, "Reajuste do fornecedor");
    var v2 = venda(p, 1);
    assertThat(custoDoItem(v1)).isEqualByComparingTo("600.00");   // histórico preservado
    assertThat(custoDoItem(v2)).isEqualByComparingTo("700.00");
    assertThat(itens.findById(vendas.obter(v1).itens().get(0).id()).orElseThrow().getCustoOrigem()).isEqualTo("CATALOGO");
    como(gerente);
    assertThat(custos.historico(p.getId())).hasSize(2);
  }

  @Test
  void custoDaVariacaoTemPrioridadeEVariacaoSemCustoHerdaDoProduto() {
    var p = produto();
    como(admin);
    custos.registrar(p.getId(), null, new BigDecimal("500.00"), null, null);
    custos.registrar(p.getId(), p.getVariacoes().get(0).getId(), new BigDecimal("450.00"), null, null);
    como(gerente);
    var linhas = custos.listar().stream().filter(l -> l.produtoId().equals(p.getId())).toList();
    var daVariacao = linhas.stream().filter(l -> l.variacaoId() != null).findFirst().orElseThrow();
    assertThat(daVariacao.custo()).isEqualByComparingTo("450.00");
    assertThat(daVariacao.herdadoDoProduto()).isFalse();
    assertThat(venda(p, 1)).isNotNull();
  }

  @Test
  void custoEhDoProprietarioValidadoEInformarItemUmaVezComMotivo() {
    var p = produto();
    como(gerente);
    assertThatThrownBy(() -> custos.registrar(p.getId(), null, BigDecimal.TEN, null, null)).isInstanceOf(AccessDeniedException.class);
    como(admin);
    assertThatThrownBy(() -> custos.registrar(p.getId(), null, new BigDecimal("-1"), null, null)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> custos.registrar(p.getId(), 999999L, BigDecimal.TEN, null, null)).isInstanceOf(NegocioException.class);

    var v = venda(p, 1);
    Long itemId = vendas.obter(v).itens().get(0).id();
    como(admin);
    assertThatThrownBy(() -> custos.informarCustoDoItem(itemId, new BigDecimal("300"), " ")).isInstanceOf(NegocioException.class);
    custos.informarCustoDoItem(itemId, new BigDecimal("300"), "Conforme nota de compra");
    assertThat(itens.findById(itemId).orElseThrow().getCustoOrigem()).isEqualTo("MANUAL");
    assertThatThrownBy(() -> custos.informarCustoDoItem(itemId, new BigDecimal("1"), "de novo")).isInstanceOf(NegocioException.class)
        .hasMessageContaining("não pode ser alterado");
    como(gerente);
    assertThatThrownBy(() -> custos.informarCustoDoItem(itemId, new BigDecimal("1"), "x")).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void margemSoApareceQuandoTodosOsItensTemCustoENuncaViraLucro() {
    var comCusto = produto();
    var semCusto = produto();
    como(admin);
    custos.registrar(comCusto.getId(), null, new BigDecimal("600.00"), null, null);
    venda(comCusto, 1);
    venda(semCusto, 1);
    como(gerente);
    var r = relatorios.vendas(relogio.hoje(), relogio.hoje(), "VENDEDOR");
    assertThat(r.aviso()).contains("não são dinheiro recebido");
    var linha = r.linhas().stream().filter(l -> l.chave().contains("vendedor-c")).findFirst().orElseThrow();
    assertThat(linha.vendas()).isEqualTo(2);
    assertThat(linha.itensSemCusto()).isEqualTo(1);
    assertThat(linha.margemCompleta()).isFalse();
    assertThat(linha.margemBrutaItens()).isNull();   // margem incompleta não é apresentada
    assertThat(r.total().margemBrutaItens()).isNull();
  }

  @Test
  void vendasRealizadasNaoSaoDinheiroRecebido() {
    var p = produto();
    como(admin);
    custos.registrar(p.getId(), null, new BigDecimal("600.00"), null, null);
    var antes = relatorios.recebimentos(relogio.hoje(), relogio.hoje());
    var v = venda(p, 1);
    como(gerente);
    var vend = relatorios.vendas(relogio.hoje(), relogio.hoje(), "CANAL");
    assertThat(vend.total().totalVendido()).isGreaterThanOrEqualTo(new BigDecimal("1000.00"));
    var semRecebimento = relatorios.recebimentos(relogio.hoje(), relogio.hoje());
    assertThat(semRecebimento.entradaEfetivaPorConta().get("BANCO")).isEqualByComparingTo(antes.entradaEfetivaPorConta().get("BANCO"));

    recebimentos.registrar(v, new RegistrarRecebimentoRequest(vendas.obter(v).total(), null, null, null, null), UUID.randomUUID().toString());
    var depois = relatorios.recebimentos(relogio.hoje(), relogio.hoje());
    assertThat(depois.entradaEfetivaPorConta().get("BANCO").subtract(antes.entradaEfetivaPorConta().get("BANCO")))
        .isEqualByComparingTo(vendas.obter(v).total());
    assertThat(depois.pagamentosDoCliente()).anyMatch(l -> l.chave().equals("PIX") && l.registrado().signum() > 0);
    assertThat(depois.entradaEfetivaPorConta().get("CAIXA")).isEqualByComparingTo(antes.entradaEfetivaPorConta().get("CAIXA"));
  }

  @Test
  void fechamentoListaItensSemCustoENuncaTrataComoZero() {
    var p = produto();
    venda(p, 1);
    como(admin);
    var previa = fechamento.previa(Relogio.texto(relogio.hoje()));
    assertThat(previa.resultado().definitivo()).isFalse();
    assertThat(previa.resultado().lucroApurado()).isNull();
    assertThat(previa.resultado().itensSemCusto()).isGreaterThan(0);
    assertThat(previa.resultado().faltantes()).anyMatch(f -> f.contains("custo"));
  }

  @Test
  void relatoriosOperacionaisEPermissoes() {
    como(gerente);
    assertThat(relatorios.contasPendentes().grupos()).hasSize(2);
    assertThat(relatorios.estoque().saldos()).isNotNull();
    assertThat(relatorios.entregas(null, null).pedidosPorStatusEntrega()).isNotNull();
    assertThat(relatorios.comissoes(null, null).linhas()).isNotNull();
    assertThat(relatorios.metas(Relogio.texto(relogio.hoje())).metas()).isNotNull();
    assertThatThrownBy(() -> relatorios.vendas(relogio.hoje(), relogio.hoje().minusDays(1), null)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> relatorios.vendas(null, null, "XYZ")).isInstanceOf(NegocioException.class);
    como(vendedor);
    assertThatThrownBy(() -> relatorios.vendas(null, null, null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> relatorios.estoque()).isInstanceOf(AccessDeniedException.class);
  }

  // ------------------------------------------------------------------ helpers

  private BigDecimal custoDoItem(Long pedidoId) {
    return itens.findById(vendas.obter(pedidoId).itens().get(0).id()).orElseThrow().getCustoUnitario();
  }

  private Long venda(Produto p, int qtd) {
    como(vendedor);
    var c = clientes.save(Cliente.builder().nome("Cliente " + UUID.randomUUID().toString().substring(0, 8)).build());
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), qtd, ModalidadeItem.PRONTA_ENTREGA);
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null, FormaPagamento.PIX, 1,
        null, null, null, null, List.of(item)));
    var id = vendas.confirmar(v.id(), UUID.randomUUID().toString()).id();
    como(gerente);
    return id;
  }

  private Produto produto() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Armario " + s).preco(new BigDecimal("1000.00")).estoque(50).sku("P-" + s).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("Casal").sku("V-" + s).adicionalPreco(BigDecimal.ZERO).estoque(50).build());
    return produtos.save(p);
  }

  private User usuario(String email, Role role) {
    return users.findByEmail(email).orElseGet(() -> users.save(User.builder().email(email).nome(email).passwordHash("x")
        .roles(Set.of(role)).enabled(true).createdAt(Instant.now()).build()));
  }

  private void como(User u) {
    var auth = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, auth));
  }
}
