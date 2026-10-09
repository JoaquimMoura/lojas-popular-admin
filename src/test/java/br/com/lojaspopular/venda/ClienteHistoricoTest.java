package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.lojaspopular.application.cliente.ClienteHistoricoService;
import br.com.lojaspopular.application.cliente.ClienteService;
import br.com.lojaspopular.application.cliente.ClienteService.DadosCliente;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.financeiro.RelatorioService;
import br.com.lojaspopular.application.financeiro.Relogio;
import br.com.lojaspopular.application.venda.VendaClienteService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
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
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoClienteEventoRepository;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.security.JwtUtil;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/** Vínculo cliente x venda, histórico de compras, escopo, integridade e vínculo posterior. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClienteHistoricoTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;
  @Autowired JwtUtil jwt;
  @Autowired ClienteService clienteService;
  @Autowired ClienteHistoricoService historico;
  @Autowired VendaClienteService vendaCliente;
  @Autowired VendaService vendas;
  @Autowired RelatorioService relatorios;
  @Autowired ConfiguracaoComercialService config;
  @Autowired Relogio relogio;
  @Autowired UserRepository users;
  @Autowired ClienteRepository clientes;
  @Autowired ProdutoRepository produtos;
  @Autowired PedidoRepository pedidos;
  @Autowired PedidoClienteEventoRepository eventos;
  @Autowired CondicaoPagamentoRepository condicoes;

  User admin;
  User gerente;
  User vendedorA;
  User vendedorB;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-h@loja.com", Role.ADMIN);
    gerente = usuario("gerente-h@loja.com", Role.GERENTE);
    vendedorA = usuario("vend-a-" + UUID.randomUUID().toString().substring(0, 6) + "@loja.com", Role.VENDEDOR);
    vendedorB = usuario("vend-b-" + UUID.randomUUID().toString().substring(0, 6) + "@loja.com", Role.VENDEDOR);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), false);
    var c = condicoes.findByFormaAndParcelas(FormaPagamento.PIX, 1);
    if (c.isPresent()) {
      config.atualizarCondicao(c.get().getId(), BigDecimal.ZERO, true);
    } else {
      config.criarCondicao(FormaPagamento.PIX, 1, BigDecimal.ZERO, true);
    }
    config.atualizarClientes(null, null);   // D13 pendente: escopo atual e troca de cliente bloqueada
  }

  @Test
  void cadastrarClienteEVenderGuardaOVinculoEACopiaDosDados_alterarCadastroNaoMudaOHistorico() {
    como(vendedorA);
    var cli = novoCliente("Maria Compradora", "11987654321");
    Long v = venda(vendedorA, cli, 1);
    var det = vendas.obter(v);
    assertThat(det.cliente().id()).isEqualTo(cli.getId());
    assertThat(det.cliente().nome()).isEqualTo(cli.getNome());
    var pedido = pedidos.findById(v).orElseThrow();
    assertThat(pedido.getUsuario().getId()).isEqualTo(vendedorA.getId());   // quem registrou ≠ cliente comprador
    assertThat(pedido.getCliente().getId()).isNotEqualTo(pedido.getUsuario().getId());

    String nomeOriginal = cli.getNome();
    como(gerente);
    clienteService.atualizar(cli.getId(), new DadosCliente("Maria Nova Silva", cli.getCpf(), "11911112222", null, null, null), true);
    como(vendedorA);
    assertThat(vendas.obter(v).cliente().nome()).isEqualTo(nomeOriginal);          // venda preserva como estava
    assertThat(vendas.obter(v).cliente().telefone()).isEqualTo(cli.getTelefone());
    assertThat(historico.compras(cli.getId(), null, null, null, null, 0, 10).itens().get(0).clienteNaVenda())
        .isEqualTo(nomeOriginal);
    assertThatThrownBy(() -> venda(vendedorA, null, 1)).isInstanceOf(RuntimeException.class);   // venda sem cliente não existe
  }

  @Test
  void duasComprasAparecemDaMaisRecenteParaAMaisAntigaCanceladaFicaVisivelMasForaDosIndicadores() {
    como(vendedorA);
    var cli = novoCliente("Joao Duas Compras", "11955550001");
    Long v1 = venda(vendedorA, cli, 1);
    Long v2 = venda(vendedorA, cli, 2);
    Long v3 = venda(vendedorA, cli, 1);
    como(gerente);
    vendas.cancelar(v3, "Desistência");

    como(vendedorA);
    var pag = historico.compras(cli.getId(), null, null, null, null, 0, 10);
    assertThat(pag.itens()).extracting(c -> c.pedidoId()).containsExactly(v3, v2, v1);
    assertThat(pag.itens().get(0).statusComercial()).isEqualTo(StatusComercial.CANCELADA);
    assertThat(pag.itens().get(0).contaNosIndicadores()).isFalse();
    var r = historico.resumo(cli.getId());
    assertThat(r.compras()).isEqualTo(2);
    assertThat(r.canceladas()).isEqualTo(1);
    assertThat(r.valorComprado()).isEqualByComparingTo("3000.00");
    assertThat(r.ticketMedio()).isEqualByComparingTo("1500.00");
    assertThat(r.primeiraCompra()).isNotNull();
    assertThat(r.observacao()).contains("Não é dinheiro recebido");
    // filtros
    assertThat(historico.compras(cli.getId(), null, null, StatusComercial.CANCELADA, null, 0, 10).total()).isEqualTo(1);
    assertThat(historico.compras(cli.getId(), null, null, null, "produto-que-nao-existe", 0, 10).total()).isZero();
    assertThat(historico.compras(cli.getId(), null, null, null, null, 1, 2).itens()).hasSize(1);   // paginação
  }

  @Test
  void totaisDoClienteBatemComOsRelatoriosGerenciais() {
    como(admin);
    var antes = relatorios.vendas(relogio.hoje(), relogio.hoje(), "CANAL").total();
    como(vendedorA);
    var cli = novoCliente("Cliente Base Unica", "11955550002");
    venda(vendedorA, cli, 1);
    venda(vendedorA, cli, 2);
    como(admin);
    var depois = relatorios.vendas(relogio.hoje(), relogio.hoje(), "CANAL").total();
    var r = historico.resumo(cli.getId());
    assertThat(depois.totalVendido().subtract(antes.totalVendido())).isEqualByComparingTo(r.valorComprado());
    assertThat(depois.vendas() - antes.vendas()).isEqualTo(r.compras());
  }

  @Test
  void escopoDoVendedor_apiDireta_eD13LiberaPeloProprietario() throws Exception {
    como(vendedorA);
    var cli = novoCliente("Cliente Compartilhado", "11955550003");
    Long vA = venda(vendedorA, cli, 1);
    Long vB = venda(vendedorB, cli, 1);
    String tokenB = token(vendedorB);
    // vendedor B só enxerga a própria compra, inclusive nos indicadores
    var corpo = om.readTree(mvc.perform(get("/api/v1/clientes/" + cli.getId() + "/compras").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(corpo.path("itens")).hasSize(1);
    assertThat(corpo.path("itens").get(0).path("pedidoId").asLong()).isEqualTo(vB);
    assertThat(corpo.path("escopo").asText()).isEqualTo("SOMENTE_SUAS_VENDAS");
    var resumo = om.readTree(mvc.perform(get("/api/v1/clientes/" + cli.getId() + "/resumo").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(resumo.path("compras").asLong()).isEqualTo(1);
    assertThat(resumo.path("valorComprado").decimalValue()).isEqualByComparingTo("1000.00");
    // o corpo não carrega custo, margem, recebimento nem restituição
    assertThat(corpo.toString()).doesNotContain("custo").doesNotContain("margem").doesNotContain("recebido").doesNotContain("restitu");
    // e o detalhe da venda alheia segue negado
    mvc.perform(get("/api/v1/vendas/" + vA).header("Authorization", "Bearer " + tokenB)).andExpect(status().isNotFound());

    // gerente vê tudo
    como(gerente);
    assertThat(historico.resumo(cli.getId()).compras()).isEqualTo(2);

    // o proprietário libera (D13): aí o vendedor vê as duas compras
    como(admin);
    config.atualizarClientes(true, null);
    var liberado = om.readTree(mvc.perform(get("/api/v1/clientes/" + cli.getId() + "/compras").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(liberado.path("itens")).hasSize(2);
    mvc.perform(get("/api/v1/vendas/" + vA).header("Authorization", "Bearer " + tokenB)).andExpect(status().isNotFound());   // detalhe continua restrito
    como(vendedorB);
    assertThatThrownBy(() -> config.atualizarClientes(false, null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void clienteComVendasNaoEhExcluido_semVendasPodeSer() throws Exception {
    como(vendedorA);
    var comVenda = novoCliente("Cliente Com Venda", "11955550004");
    venda(vendedorA, comVenda, 1);
    var semVenda = novoCliente("Cliente Sem Venda", "11955550005");
    mvc.perform(delete("/api/v1/clientes/" + comVenda.getId()).header("Authorization", "Bearer " + token(gerente)))
        .andExpect(status().isForbidden());   // só o proprietário
    mvc.perform(delete("/api/v1/clientes/" + comVenda.getId()).header("Authorization", "Bearer " + token(admin)))
        .andExpect(status().isBadRequest());
    assertThat(clientes.findById(comVenda.getId())).isPresent();
    mvc.perform(delete("/api/v1/clientes/" + semVenda.getId()).header("Authorization", "Bearer " + token(admin)))
        .andExpect(status().isOk());
    como(gerente);
    clienteService.desativar(comVenda.getId());   // inativar preserva o histórico
    como(vendedorA);
    assertThat(historico.compras(comVenda.getId(), null, null, null, null, 0, 10).total()).isEqualTo(1);
  }

  @Test
  void duplicidadeDeCpfEBloqueadaEDeNomeOuTelefoneGeraAlertaSemMesclar() {
    como(vendedorA);
    var a = novoCliente("Cliente Duplicado", "11955550006");
    assertThatThrownBy(() -> clienteService.criar(new DadosCliente("Outro Nome", a.getCpf(), "11955550007", null, null, null), true))
        .hasMessageContaining("CPF");
    assertThatThrownBy(() -> clienteService.criar(new DadosCliente(a.getNome(), null, "11955550099", null, null, null), false))
        .isInstanceOf(br.com.lojaspopular.exception.DuplicidadeClienteException.class);   // alerta, não mescla
    assertThat(clienteService.buscar("(11) 95555-0006")).extracting(Cliente::getId).contains(a.getId());   // busca normalizada
  }

  @Test
  void vincularVendaAntigaSoOProprietarioComJustificativaSemMudarValores() {
    como(vendedorA);
    var cli = novoCliente("Cliente Do Vinculo", "11955550008");
    Long id = pedidos.saveAndFlush(Pedido.builder().usuario(vendedorA).status(PedidoStatus.PAGO)
        .statusComercial(StatusComercial.LEGADO).statusPagamento(StatusPagamento.NAO_INFORMADO).total(new BigDecimal("777.00")).build()).getId();
    como(gerente);
    assertThat(vendas.obter(id).cliente()).isNull();   // "Cliente não identificado"
    assertThatThrownBy(() -> vendaCliente.vincular(id, cli.getId(), "Conferido")).isInstanceOf(AccessDeniedException.class);
    como(admin);
    assertThatThrownBy(() -> vendaCliente.vincular(id, cli.getId(), " ")).isInstanceOf(NegocioException.class);
    var antes = vendas.obter(id);
    vendaCliente.vincular(id, cli.getId(), "Nota fiscal conferida com o cliente");
    var depois = vendas.obter(id);
    assertThat(depois.cliente().id()).isEqualTo(cli.getId());
    assertThat(depois.total()).isEqualByComparingTo(antes.total());
    assertThat(depois.statusPagamento()).isEqualTo(antes.statusPagamento());
    assertThat(depois.statusComercial()).isEqualTo(StatusComercial.LEGADO);
    assertThat(eventos.findByPedidoIdOrderByIdAsc(id)).hasSize(1);
    assertThat(eventos.findByPedidoIdOrderByIdAsc(id).get(0).getJustificativa()).contains("conferida");
    assertThatThrownBy(() -> vendaCliente.vincular(id, cli.getId(), "de novo")).isInstanceOf(NegocioException.class);   // já tem cliente
  }

  @Test
  void trocaDeClienteEmVendaConfirmadaFicaBloqueadaAteOProprietarioDefinirAPermissao() {
    como(vendedorA);
    var c1 = novoCliente("Cliente Original", "11955550009");
    var c2 = novoCliente("Cliente Correto", "11955550010");
    Long v = venda(vendedorA, c1, 1);
    como(admin);
    assertThatThrownBy(() -> vendaCliente.trocar(v, c2.getId(), "Cliente errado")).isInstanceOf(ConfiguracaoPendenteException.class)
        .hasMessageContaining("D13");   // até o proprietário (inclusive) fica bloqueado
    config.atualizarClientes(null, EnumSet.of(Role.GERENTE));
    como(vendedorA);
    assertThatThrownBy(() -> vendaCliente.trocar(v, c2.getId(), "x")).isInstanceOf(AccessDeniedException.class);
    como(gerente);
    assertThatThrownBy(() -> vendaCliente.trocar(v, c2.getId(), "")).isInstanceOf(NegocioException.class);
    var total = vendas.obter(v).total();
    vendaCliente.trocar(v, c2.getId(), "Vendedor selecionou o cliente errado");
    var det = vendas.obter(v);
    assertThat(det.cliente().id()).isEqualTo(c2.getId());
    assertThat(det.cliente().nome()).isEqualTo(c2.getNome());
    assertThat(det.total()).isEqualByComparingTo(total);
    assertThat(eventos.findByPedidoIdOrderByIdAsc(v)).hasSize(1);
    assertThat(eventos.findByPedidoIdOrderByIdAsc(v).get(0).getClienteAnterior().getId()).isEqualTo(c1.getId());
  }

  // ------------------------------------------------------------------ helpers

  private Cliente novoCliente(String nome, String telefone) {
    return clienteService.criar(new DadosCliente(nome + " " + UUID.randomUUID().toString().substring(0, 4), cpfAleatorio(), telefone
        + (int) (Math.random() * 10), null, null, null), true);
  }

  private Long venda(User vendedor, Cliente cliente, int qtd) {
    como(vendedor);
    Produto p = produto();
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), qtd, ModalidadeItem.PRONTA_ENTREGA);
    var v = vendas.registrar(new VendaRequest(cliente == null ? null : cliente.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null, List.of(item)));
    return vendas.confirmar(v.id(), UUID.randomUUID().toString()).id();
  }

  private Produto produto() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Comoda " + s).preco(new BigDecimal("1000.00")).estoque(50).sku("P-" + s).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("U").sku("V-" + s).adicionalPreco(BigDecimal.ZERO).estoque(50).build());
    return produtos.save(p);
  }

  private static String cpfAleatorio() {
    int[] n = new int[11];
    for (int i = 0; i < 9; i++) {
      n[i] = (int) (Math.random() * 10);
    }
    for (int j = 9; j < 11; j++) {
      int soma = 0;
      for (int i = 0; i < j; i++) {
        soma += n[i] * (j + 1 - i);
      }
      n[j] = (soma * 10 % 11) % 10;
    }
    StringBuilder sb = new StringBuilder();
    for (int d : n) {
      sb.append(d);
    }
    return sb.toString();
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
