package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lojaspopular.application.cliente.ClienteService;
import br.com.lojaspopular.application.cliente.ClienteService.DadosCliente;
import br.com.lojaspopular.application.cliente.ClienteService.DadosEndereco;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.expedicao.ExpedicaoService;
import br.com.lojaspopular.application.financeiro.CartaoService;
import br.com.lojaspopular.application.financeiro.RecebimentoService;
import br.com.lojaspopular.application.venda.ComprovanteService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.repository.CondicaoPagamentoRepository;
import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.security.JwtUtil;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RegistrarRecebimentoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoRequest;
import br.com.lojaspopular.web.financeiro.PermissaoDtos.PermissoesFinanceirasRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;

/** Comprovante de compra: dados oficiais, histórico preservado, escopo, D12 e ausência de efeitos colaterais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComprovanteTest {

  @Autowired MockMvc mvc;
  @Autowired JwtUtil jwt;
  @Autowired ComprovanteService comprovante;
  @Autowired VendaService vendas;
  @Autowired ClienteService clienteService;
  @Autowired RecebimentoService recebimentos;
  @Autowired CartaoService cartao;
  @Autowired ExpedicaoService expedicao;
  @Autowired EstoqueService estoque;
  @Autowired ConfiguracaoComercialService config;
  @Autowired UserRepository users;
  @Autowired ProdutoRepository produtos;
  @Autowired PedidoRepository pedidos;
  @Autowired CondicaoPagamentoRepository condicoes;
  @Autowired br.com.lojaspopular.domain.auditoria.repositoty.AuditoriaEventoRepository auditoriaRepo;

  User admin;
  User gerente;
  User vendedorA;
  User vendedorB;

  @BeforeEach
  void preparar() {
    admin = usuario("admin-c2@loja.com", Role.ADMIN);
    gerente = usuario("gerente-c2@loja.com", Role.GERENTE);
    vendedorA = usuario("vc-a-" + UUID.randomUUID().toString().substring(0, 6) + "@loja.com", Role.VENDEDOR);
    vendedorB = usuario("vc-b-" + UUID.randomUUID().toString().substring(0, 6) + "@loja.com", Role.VENDEDOR);
    como(admin);
    config.atualizar(new BigDecimal("10.00"), Arredondamento.HALF_UP, EnumSet.of(Role.ADMIN, Role.GERENTE), false);
    cond(FormaPagamento.PIX, 1);
    cond(FormaPagamento.DINHEIRO, 1);
    cond(FormaPagamento.CARTAO, 3);
    config.atualizarPermissoes(new PermissoesFinanceirasRequest(Set.of(Role.GERENTE), Set.of(Role.GERENTE), Set.of(Role.GERENTE),
        Set.of(Role.GERENTE), Set.of(Role.GERENTE)));
  }

  @Test
  void totaisEItensSaoOsOficiaisDoPedido_eRascunhoNaoTemComprovante() {
    como(vendedorA);
    var cli = cliente("Maria Comprovante");
    Long id = venda(vendedorA, cli, FormaPagamento.PIX, 1, 2, ModalidadeItem.PRONTA_ENTREGA, TipoEntrega.RETIRADA);
    var oficial = vendas.obter(id);
    var c = comprovante.emitir(id);
    assertThat(c.total()).isEqualByComparingTo(oficial.total());
    assertThat(c.subtotal()).isEqualByComparingTo(oficial.subtotal());
    assertThat(c.desconto()).isEqualByComparingTo(oficial.desconto());
    assertThat(c.frete()).isEqualByComparingTo("0");
    assertThat(c.itens()).hasSize(1);
    assertThat(c.itens().get(0).quantidade()).isEqualTo(2);
    assertThat(c.itens().get(0).total()).isEqualByComparingTo(oficial.itens().get(0).total());
    assertThat(c.emitidoEm()).isNotNull();
    assertThat(c.cancelado()).isFalse();

    // rascunho (registrado e não confirmado) não vira "venda concluída"
    var item = new ItemRequest(produto().getId(), null, 1, ModalidadeItem.PRONTA_ENTREGA);
    Produto p = produto();
    var rascunho = vendas.registrar(new VendaRequest(cli.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null,
        FormaPagamento.PIX, 1, null, null, null, null,
        List.of(new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 1, ModalidadeItem.PRONTA_ENTREGA))));
    assertThatThrownBy(() -> comprovante.emitir(rascunho.id())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("confirmadas");
    assertThat(item).isNotNull();
  }

  @Test
  void historicoPreservado_cadastroEPrecoPosterioresNaoMudamOComprovante() {
    como(vendedorA);
    var cli = cliente("Cliente Historico Doc");
    Long id = venda(vendedorA, cli, FormaPagamento.PIX, 1, 1, ModalidadeItem.PRONTA_ENTREGA, TipoEntrega.RETIRADA);
    var antes = comprovante.emitir(id);
    como(gerente);
    clienteService.atualizar(cli.getId(), new DadosCliente("Nome Totalmente Novo", cli.getCpf(), "11900000000", null, null, null), true);
    como(admin);
    var prod = produtos.findById(vendas.obter(id).itens().get(0).produtoId()).orElseThrow();
    prod.setPreco(new BigDecimal("5555.00"));
    prod.setNome("Produto Renomeado Depois");
    produtos.save(prod);
    como(vendedorA);
    var depois = comprovante.emitir(id);
    assertThat(depois.cliente().nome()).isEqualTo(antes.cliente().nome()).isEqualTo(cli.getNome());
    assertThat(depois.cliente().telefone()).isEqualTo(antes.cliente().telefone());
    assertThat(depois.itens().get(0).descricao()).isEqualTo(antes.itens().get(0).descricao());
    assertThat(depois.total()).isEqualByComparingTo(antes.total());
  }

  @Test
  void entregaAgendadaXNaoAgendada_enderecoDaEntregaXCadastral_observacaoSoDoCliente() {
    como(vendedorA);
    var cli = clienteComEndereco("Cliente Entrega Doc");
    Long id = venda(vendedorA, cli, FormaPagamento.PIX, 1, 1, ModalidadeItem.PRONTA_ENTREGA, TipoEntrega.ENTREGA);
    var c1 = comprovante.emitir(id);
    assertThat(c1.entrega().tipo()).isEqualTo(TipoEntrega.ENTREGA);
    assertThat(c1.entrega().dataAgendada()).isNull();          // não agendada: nada de data inventada
    assertThat(c1.entrega().enderecoDestaEntrega().logradouro()).isEqualTo("Rua das Flores");
    assertThat(c1.cliente().enderecoCadastral().logradouro()).isEqualTo("Rua das Flores");
    assertThat(c1.montagem().dataAgendada()).isNull();
    assertThat(c1.observacaoCliente()).isNull();

    como(gerente);
    expedicao.agendarEntrega(id, LocalDate.now().plusDays(3), PeriodoAgenda.TARDE, "Equipe interna", "NOTA INTERNA DA EQUIPE");
    como(vendedorA);
    comprovante.definirObservacaoCliente(id, "Portaria só recebe até as 17h");
    var c2 = comprovante.emitir(id);
    assertThat(c2.entrega().dataAgendada()).isEqualTo(LocalDate.now().plusDays(3));
    assertThat(c2.entrega().periodo()).isEqualTo(PeriodoAgenda.TARDE);
    assertThat(c2.observacaoCliente()).isEqualTo("Portaria só recebe até as 17h");
    assertThat(c2.toString()).doesNotContain("NOTA INTERNA").doesNotContain("Equipe interna");   // observações internas não saem
  }

  @Test
  void encomendaTemPrevisaoDeChegadaSeparadaDoAgendamento() {
    como(vendedorA);
    var cli = cliente("Cliente Encomenda Doc");
    String s = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Sofa sob encomenda " + s).preco(new BigDecimal("2000.00")).estoque(0).sku("E-" + s)
        .modalidade(br.com.lojaspopular.domain.catalog.enums.ModalidadeProduto.ENCOMENDA).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Cinza").tamanho("3 lugares").sku("EV-" + s).adicionalPreco(BigDecimal.ZERO).estoque(0).build());
    p = produtos.save(p);
    var v = vendas.registrar(new VendaRequest(cli.getId(), null, CanalVenda.LOJA, TipoEntrega.RETIRADA, null, FormaPagamento.PIX, 1, null,
        null, null, null, List.of(new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), 1, ModalidadeItem.ENCOMENDA))));
    Long id = vendas.confirmar(v.id(), UUID.randomUUID().toString()).id();
    var c = comprovante.emitir(id);
    assertThat(c.itens().get(0).modalidade()).isEqualTo(ModalidadeItem.ENCOMENDA);
    assertThat(c.itens().get(0).previsaoChegada()).isNull();   // sem previsão informada: "A combinar" na impressão
  }

  @Test
  void pagamentoPendenteQuitadoEParcelasDoCartaoSoComConsultaFinanceira() {
    como(admin);
    cartao.salvarTaxa(null, new TaxaCartaoRequest("OPCOMP" + UUID.randomUUID().toString().substring(0, 4), 3, new BigDecimal("4.0000"), 30, 30, true));
    var op = cartao.listarTaxas().get(cartao.listarTaxas().size() - 1).operadora();
    como(vendedorA);
    var cli = cliente("Cliente Cartao Doc");
    Long id = venda(vendedorA, cli, FormaPagamento.CARTAO, 3, 1, ModalidadeItem.PRONTA_ENTREGA, TipoEntrega.RETIRADA);

    como(gerente);
    var pendente = comprovante.emitir(id);
    assertThat(pendente.pagamento().situacao()).isEqualTo("PENDENTE");
    assertThat(pendente.pagamento().parcelas()).isEqualTo(3);
    assertThat(pendente.pagamento().valorPago()).isEqualByComparingTo("0");
    assertThat(pendente.pagamento().saldo()).isEqualByComparingTo(vendas.obter(id).total());
    assertThat(pendente.pagamento().parcelasRegistradas()).isEmpty();

    recebimentos.registrar(id, new RegistrarRecebimentoRequest(vendas.obter(id).total(), null, null, op, null), UUID.randomUUID().toString());
    var pago = comprovante.emitir(id);
    assertThat(pago.pagamento().situacao()).isEqualTo("PAGO");
    assertThat(pago.pagamento().valorPago()).isEqualByComparingTo(vendas.obter(id).total());
    assertThat(pago.pagamento().parcelasRegistradas()).hasSize(3);
    assertThat(pago.pagamento().parcelasRegistradas().stream().map(x -> x.valor()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(vendas.obter(id).total());   // arredondamento preservado: a soma bate
    assertThat(pago.toString()).doesNotContain("taxa").doesNotContain("Liquido").doesNotContain("custo").doesNotContain("comiss");

    // quem não pode consultar o financeiro (D12) não recebe valor pago, saldo nem parcelas, só a situação
    como(admin);
    config.atualizarPermissoes(new PermissoesFinanceirasRequest(null, null, null, null, null));
    como(gerente);
    var restrito = comprovante.emitir(id);
    assertThat(restrito.pagamento().financeiroVisivel()).isFalse();
    assertThat(restrito.pagamento().valorPago()).isNull();
    assertThat(restrito.pagamento().saldo()).isNull();
    assertThat(restrito.pagamento().parcelasRegistradas()).isEmpty();
    assertThat(restrito.pagamento().situacao()).isEqualTo("PAGO");
    como(vendedorA);
    assertThat(comprovante.emitir(id).pagamento().financeiroVisivel()).isFalse();
  }

  @Test
  void canceladoFicaIdentificado_eEscopoDoVendedorPelaApiDireta() throws Exception {
    como(vendedorA);
    var cli = cliente("Cliente Cancelado Doc");
    Long id = venda(vendedorA, cli, FormaPagamento.PIX, 1, 1, ModalidadeItem.PRONTA_ENTREGA, TipoEntrega.RETIRADA);
    como(gerente);
    vendas.cancelar(id, "Desistência");
    como(vendedorA);
    var c = comprovante.emitir(id);
    assertThat(c.cancelado()).isTrue();
    assertThat(c.canceladoEm()).isNotNull();
    mvc.perform(get("/api/v1/vendas/" + id + "/comprovante").header("Authorization", "Bearer " + token(vendedorA))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/vendas/" + id + "/comprovante").header("Authorization", "Bearer " + token(vendedorB))).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/vendas/" + id + "/comprovante")).andExpect(status().isUnauthorized());
  }

  @Test
  void reimprimirNaoAlteraEstoquePagamentoNemSituacao_soAuditaAEmissao() {
    como(vendedorA);
    var cli = cliente("Cliente Reimpressao Doc");
    Long id = venda(vendedorA, cli, FormaPagamento.PIX, 1, 1, ModalidadeItem.PRONTA_ENTREGA, TipoEntrega.RETIRADA);
    var antes = vendas.obter(id);
    var produtoId = antes.itens().get(0).produtoId();
    var saldoAntes = estoque.listarSaldos().stream().filter(s -> s.produtoId().equals(produtoId)).toList();
    long emissoesAntes = emissoes(id);
    comprovante.emitir(id);
    comprovante.emitir(id);
    var depois = vendas.obter(id);
    assertThat(depois.statusPagamento()).isEqualTo(antes.statusPagamento());
    assertThat(depois.statusComercial()).isEqualTo(StatusComercial.CONFIRMADA);
    assertThat(depois.total()).isEqualByComparingTo(antes.total());
    assertThat(depois.version()).isEqualTo(antes.version());
    assertThat(estoque.listarSaldos().stream().filter(s -> s.produtoId().equals(produtoId)).toList()).isEqualTo(saldoAntes);
    assertThat(emissoes(id) - emissoesAntes).isEqualTo(2);
  }

  @Test
  void pedidoAntigoIncompletoNaoInventaDados() {
    como(admin);
    Long id = pedidos.saveAndFlush(br.com.lojaspopular.domain.order.model.Pedido.builder().usuario(admin)
        .status(br.com.lojaspopular.domain.catalog.enums.PedidoStatus.PAGO).statusComercial(StatusComercial.LEGADO)
        .total(new BigDecimal("777.00")).build()).getId();
    var c = comprovante.emitir(id);
    assertThat(c.legado()).isTrue();
    assertThat(c.cliente()).isNull();
    assertThat(c.vendedor()).isNull();
    assertThat(c.pagamento().financeiroVisivel()).isFalse();   // sem controle de pagamento: nada de "pago R$ 0" nem saldo
    assertThat(c.pagamento().valorPago()).isNull();
    assertThat(c.entrega().tipo()).isNull();
    assertThat(c.itens()).isEmpty();
  }

  // ------------------------------------------------------------------ helpers

  private long emissoes(Long pedidoId) {
    return auditoriaRepo.findAll().stream().filter(a -> a.getTipo() == AuditoriaTipo.COMPROVANTE_EMITIDO
        && a.getDescricao() != null && a.getDescricao().contains("#" + pedidoId + " ")).count();
  }

  private void cond(FormaPagamento forma, int parcelas) {
    var c = condicoes.findByFormaAndParcelas(forma, parcelas);
    if (c.isPresent()) {
      config.atualizarCondicao(c.get().getId(), BigDecimal.ZERO, true);
    } else {
      config.criarCondicao(forma, parcelas, BigDecimal.ZERO, true);
    }
  }

  private br.com.lojaspopular.domain.cliente.model.Cliente cliente(String nome) {
    return clienteService.criar(new DadosCliente(nome + " " + UUID.randomUUID().toString().substring(0, 4), cpf(), "119555" + (int) (1000 + Math.random() * 8999),
        null, null, null), true);
  }

  private br.com.lojaspopular.domain.cliente.model.Cliente clienteComEndereco(String nome) {
    return clienteService.criar(new DadosCliente(nome + " " + UUID.randomUUID().toString().substring(0, 4), cpf(), "119555" + (int) (1000 + Math.random() * 8999),
        null, null, List.of(new DadosEndereco("Casa", "01001000", "Rua das Flores", "100", "Apto 12", "Centro", "São Paulo", "SP", true))), true);
  }

  private Long venda(User vendedor, br.com.lojaspopular.domain.cliente.model.Cliente cli, FormaPagamento forma, int parcelas, int qtd,
      ModalidadeItem modalidade, TipoEntrega tipo) {
    como(vendedor);
    Produto p = produto();
    var item = new ItemRequest(p.getId(), p.getVariacoes().get(0).getId(), qtd, modalidade);
    Long enderecoId = tipo == TipoEntrega.ENTREGA ? clienteService.obter(cli.getId()).getEnderecos().get(0).getId() : null;
    var v = vendas.registrar(new VendaRequest(cli.getId(), null, CanalVenda.LOJA, tipo, enderecoId, forma, parcelas, null, null, null, null,
        List.of(item)));
    return vendas.confirmar(v.id(), UUID.randomUUID().toString()).id();
  }

  private Produto produto() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    Produto p = Produto.builder().nome("Armario longo nome para teste de impressao " + s).preco(new BigDecimal("1000.00")).estoque(50)
        .sku("P-" + s).build();
    p.addVariacao(ProdutoVariacao.builder().cor("Branco").tamanho("Casal").sku("V-" + s).adicionalPreco(BigDecimal.ZERO).estoque(50).build());
    return produtos.save(p);
  }

  private static String cpf() {
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
