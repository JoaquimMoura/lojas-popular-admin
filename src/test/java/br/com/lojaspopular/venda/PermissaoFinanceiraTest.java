package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.financeiro.CaixaService;
import br.com.lojaspopular.application.financeiro.CartaoService;
import br.com.lojaspopular.application.financeiro.ComissaoService;
import br.com.lojaspopular.application.financeiro.ContaFinanceiraService;
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
import br.com.lojaspopular.domain.financeiro.enums.AquisicaoComissao;
import br.com.lojaspopular.domain.financeiro.enums.CompetenciaReceita;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.security.JwtUtil;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ConfigFinanceiraRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RegistrarRecebimentoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoRequest;
import br.com.lojaspopular.web.financeiro.PermissaoDtos.PermissoesFinanceirasRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/** D12: sem decisão, o gerente não opera o financeiro; cada operação é concedida separadamente. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissaoFinanceiraTest {

  @Autowired MockMvc mvc;
  @Autowired JwtUtil jwt;
  @Autowired ConfiguracaoComercialService config;
  @Autowired RecebimentoService recebimentos;
  @Autowired CaixaService caixa;
  @Autowired CartaoService cartao;
  @Autowired ContaFinanceiraService contas;
  @Autowired ComissaoService comissoes;
  @Autowired RelatorioService relatorios;
  @Autowired VendaService vendas;
  @Autowired Relogio relogio;
  @Autowired UserRepository users;
  @Autowired ClienteRepository clientes;
  @Autowired ProdutoRepository produtos;
  @Autowired CondicaoPagamentoRepository condicoes;

  User admin;
  User gerente;
  User vendedor;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-p@loja.com", Role.ADMIN);
    gerente = usuario("gerente-p@loja.com", Role.GERENTE);
    vendedor = usuario("vendedor-p@loja.com", Role.VENDEDOR);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), false);
    var c = condicoes.findByFormaAndParcelas(FormaPagamento.PIX, 1);
    if (c.isPresent()) {
      config.atualizarCondicao(c.get().getId(), BigDecimal.ZERO, true);
    } else {
      config.criarCondicao(FormaPagamento.PIX, 1, BigDecimal.ZERO, true);
    }
    permissoes(null, null, null, null, null);   // estado inicial: nada decidido
    var aberto = caixa.atual();
    if (aberto != null) {
      caixa.fechar(aberto.saldoEsperado(), null);
    }
  }

  @Test
  void semDecisaoOGerenteNaoOperaNadaDoFinanceiro_eOProprietarioSim() {
    Long v = venda();
    como(gerente);
    String msg = "D12";
    assertThatThrownBy(() -> recebimentos.registrar(v, new RegistrarRecebimentoRequest(BigDecimal.TEN, null, null, null, null),
        chave())).isInstanceOf(AccessDeniedException.class).hasMessageContaining(msg);
    assertThatThrownBy(() -> caixa.abrir(BigDecimal.ZERO)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> contas.criar(conta())).isInstanceOf(AccessDeniedException.class).hasMessageContaining(msg);
    assertThatThrownBy(() -> relatorios.estoque()).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> cartao.liquidar(1L, null, null, chave())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> recebimentos.estornar(1L, "x", chave())).isInstanceOf(AccessDeniedException.class);
    assertThat(vendas.obter(v).acoes().podeRegistrarRecebimento()).isFalse();   // a tela já esconde a ação

    como(admin);
    assertThat(config.pendencias()).anyMatch(p -> p.codigo().equals("D12"));
    recebimentos.registrar(v, new RegistrarRecebimentoRequest(BigDecimal.TEN, null, null, null, null), chave());
    assertThat(vendas.obter(v).acoes().podeRegistrarRecebimento()).isTrue();
  }

  @Test
  void cadaOperacaoEhConcedidaSeparadamente() {
    Long v = venda();
    permissoes(null, EnumSet.of(Role.GERENTE), null, null, null);   // só receber
    como(gerente);
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(BigDecimal.TEN, null, null, null, null), chave());
    assertThatThrownBy(() -> recebimentos.estornar(r.id(), "x", chave())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> contas.criar(conta())).isInstanceOf(AccessDeniedException.class);       // pagar
    assertThatThrownBy(() -> relatorios.estoque()).isInstanceOf(AccessDeniedException.class);       // consultar

    permissoes(EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), null, EnumSet.of(Role.GERENTE), null);
    como(gerente);
    assertThat(relatorios.estoque()).isNotNull();
    recebimentos.estornar(r.id(), "Pix lançado por engano", chave());
    assertThatThrownBy(() -> contas.criar(conta())).isInstanceOf(AccessDeniedException.class);       // pagar segue negado

    permissoes(EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE),
        EnumSet.of(Role.ADMIN));   // restituir decidido como "só o proprietário"
    como(gerente);
    var c = contas.criar(conta());
    contas.baixar(c.id(), ContaLivro.BANCO, null, chave());
    assertThatThrownBy(() -> comissoesSolicitarRestituicao()).isInstanceOf(AccessDeniedException.class);
    como(admin);
    assertThat(config.pendencias()).noneMatch(p -> p.codigo().equals("D12"));
  }

  @Test
  void baixarContaDependeDoTipo_pagarXReceber() {
    permissoes(EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), null, null, null);   // receber sim, pagar não
    como(admin);
    var pagar = contas.criar(conta());
    var receber = contas.criar(new ContaRequest(TipoConta.RECEBER, "Receita avulsa", "Teste", relogio.hoje(), BigDecimal.TEN,
        relogio.hoje().plusDays(3), null));
    como(gerente);
    assertThatThrownBy(() -> contas.baixar(pagar.id(), ContaLivro.BANCO, null, chave())).isInstanceOf(AccessDeniedException.class);
    contas.baixar(receber.id(), ContaLivro.BANCO, null, chave());
  }

  @Test
  void taxasDeCartaoSaoSempreSoDoProprietario() {
    permissoes(EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE),
        EnumSet.of(Role.GERENTE));
    como(gerente);
    assertThatThrownBy(() -> cartao.salvarTaxa(null, new TaxaCartaoRequest("OP-P", 2, new BigDecimal("3.0000"), 30, 30, true)))
        .isInstanceOf(AccessDeniedException.class);
    como(admin);
    cartao.salvarTaxa(null, new TaxaCartaoRequest("OP-P-" + UUID.randomUUID().toString().substring(0, 5), 2, new BigDecimal("3.0000"), 30, 30, true));
  }

  @Test
  void naoEstornaPagamentoDeVendaCujaComissaoPorQuitacaoJaFoiPaga() {
    como(admin);
    permissoes(EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE), EnumSet.of(Role.GERENTE));
    config.atualizarFinanceiro(new ConfigFinanceiraRequest(new BigDecimal("5.00"), AquisicaoComissao.QUITACAO,
        CompetenciaReceita.CONFIRMACAO, EnumSet.of(Role.ADMIN), EnumSet.of(Role.ADMIN), true, true, true, false));
    Long v = venda();
    como(gerente);
    var r = recebimentos.registrar(v, new RegistrarRecebimentoRequest(vendas.obter(v).total(), null, null, null, null), chave());
    como(admin);
    var conta = comissoes.gerarPagamento(vendedor.getId(), relogio.hoje().plusDays(5));
    como(gerente);
    assertThatThrownBy(() -> recebimentos.estornar(r.id(), "Estorno", chave())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("comissão desta venda");
    contas.baixar(conta.id(), ContaLivro.BANCO, null, chave());
    assertThatThrownBy(() -> recebimentos.estornar(r.id(), "Estorno", chave())).isInstanceOf(NegocioException.class);   // paga
    contas.estornar(conta.id(), "Cancelar pagamento da comissão", chave());
    contas.cancelar(conta.id(), "Comissão será refeita");
    recebimentos.estornar(r.id(), "Estorno", chave());   // agora é permitido
  }

  @Test
  void httpConsultaFinanceiraExigeConsultar_eVendedorFicaComAsProprias() throws Exception {
    String tg = token(gerente);
    mvc.perform(get("/api/v1/financeiro/caixa/atual").header("Authorization", "Bearer " + tg)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/financeiro/permissoes").header("Authorization", "Bearer " + tg)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/financeiro/caixa/abrir").header("Authorization", "Bearer " + tg).contentType(MediaType.APPLICATION_JSON)
        .content("{\"saldoInicial\":0}")).andExpect(status().isForbidden());
    permissoes(EnumSet.of(Role.GERENTE), null, null, null, null);
    mvc.perform(get("/api/v1/financeiro/caixa/atual").header("Authorization", "Bearer " + tg)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/financeiro/comissoes/minhas").header("Authorization", "Bearer " + token(vendedor))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/financeiro/caixa/atual").header("Authorization", "Bearer " + token(vendedor))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/config/comercial/permissoes-financeiras").header("Authorization", "Bearer " + tg)).andExpect(status().isOk());
  }

  @Test
  void soOProprietarioDefineAsPermissoes() {
    como(gerente);
    assertThatThrownBy(() -> config.atualizarPermissoes(new PermissoesFinanceirasRequest(Set.of(Role.GERENTE), null, null, null, null)))
        .isInstanceOf(AccessDeniedException.class);
    como(admin);
    assertThatThrownBy(() -> config.atualizarPermissoes(new PermissoesFinanceirasRequest(Set.of(Role.VENDEDOR), null, null, null, null)))
        .isInstanceOf(NegocioException.class);
  }

  // ------------------------------------------------------------------ helpers

  private void comissoesSolicitarRestituicao() {
    restituicaoService.solicitar(999999L, BigDecimal.TEN, "x");   // a permissão RESTITUIR vem antes de qualquer outra regra
  }

  @Autowired br.com.lojaspopular.application.financeiro.RestituicaoService restituicaoService;

  private void permissoes(Set<Role> consultar, Set<Role> receber, Set<Role> pagar, Set<Role> estornar, Set<Role> restituir) {
    var atual = SecurityContextHolder.getContext().getAuthentication();
    como(admin);
    config.atualizarPermissoes(new PermissoesFinanceirasRequest(consultar, receber, pagar, estornar, restituir));
    SecurityContextHolder.getContext().setAuthentication(atual);
  }

  private ContaRequest conta() {
    return new ContaRequest(TipoConta.PAGAR, "Conta " + UUID.randomUUID().toString().substring(0, 6), "Teste", relogio.hoje(),
        new BigDecimal("10.00"), relogio.hoje().plusDays(5), null);
  }

  private Long venda() {
    como(vendedor);
    var c = clientes.save(Cliente.builder().nome("Cliente " + UUID.randomUUID().toString().substring(0, 8)).build());
    String s = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Mesa " + s).preco(new BigDecimal("1000.00")).estoque(20).sku("P-" + s).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("U").sku("V-" + s).adicionalPreco(BigDecimal.ZERO).estoque(20).build());
    p = produtos.save(p);
    var v = vendas.registrar(new VendaRequest(c.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null, FormaPagamento.PIX, 1, null,
        null, null, null, List.of(new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 1, ModalidadeItem.PRONTA_ENTREGA))));
    var id = vendas.confirmar(v.id(), chave()).id();
    como(admin);
    return id;
  }

  private String chave() {
    return UUID.randomUUID().toString();
  }

  private User usuario(String email, Role role) {
    return users.findByEmail(email).orElseGet(() -> users.save(User.builder().email(email).nome(email).passwordHash("x")
        .roles(Set.of(role)).enabled(true).createdAt(Instant.now()).build()));
  }

  private String token(User u) {
    return jwt.generateAccessToken(u.getEmail(), Map.of("roles", u.getRoles().stream().map(Enum::name).toList()));
  }

  private void como(User u) {
    var auth = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, auth));
  }
}
