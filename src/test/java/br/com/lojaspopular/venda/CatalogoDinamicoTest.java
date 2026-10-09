package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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

import br.com.lojaspopular.application.catalog.CatalogoConfigService;
import br.com.lojaspopular.application.catalog.CategoriaService;
import br.com.lojaspopular.domain.catalog.enums.TipoCaracteristica;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.security.JwtUtil;
import br.com.lojaspopular.web.catalog.controller.ProdutoController;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.CaracteristicaRequest;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.CaracteristicaView;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.MaterialView;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.OpcaoRequest;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.ValorRequest;
import br.com.lojaspopular.web.catalog.dto.CategoriaRequest;
import br.com.lojaspopular.web.catalog.dto.CategoriaResponse;
import br.com.lojaspopular.web.catalog.dto.ProdutoRequest;
import br.com.lojaspopular.web.catalog.dto.ProdutoResponse;

/** Materiais cadastráveis, características por categoria, formulário de produto dinâmico e compatibilidade. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoDinamicoTest {

  @Autowired MockMvc mvc;
  @Autowired JwtUtil jwt;
  @Autowired CatalogoConfigService catalogo;
  @Autowired CategoriaService categorias;
  @Autowired ProdutoController produtos;
  @Autowired UserRepository users;

  User admin;
  User gerente;
  User vendedor;
  String s;   // sufixo único por teste (nomes de categoria são únicos)

  @BeforeEach
  void preparar() {
    admin = usuario("admin-cat@loja.com", Role.ADMIN);
    gerente = usuario("gerente-cat@loja.com", Role.GERENTE);
    vendedor = usuario("vend-cat@loja.com", Role.VENDEDOR);
    s = UUID.randomUUID().toString().substring(0, 6);
    como(gerente);
  }

  @Test
  void materialNaoDuplicaPorMaiusculaEspacoOuAcento_eEhCompartilhado() {
    var espuma = catalogo.criarMaterial("Espuma " + s);
    assertThatThrownBy(() -> catalogo.criarMaterial("  ESPUMA   " + s.toUpperCase())).isInstanceOf(NegocioException.class)
        .hasMessageContaining("Já existe");
    var acento = catalogo.criarMaterial("Látex " + s);
    assertThatThrownBy(() -> catalogo.criarMaterial("latex " + s)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> catalogo.criarMaterial("   ")).isInstanceOf(NegocioException.class);
    assertThat(catalogo.listarMateriais("latex", false)).extracting(MaterialView::id).contains(acento.id());   // busca sem acento
    // um único registro serve a várias categorias
    var c1 = categorias.criar(cat("Colchões " + s, List.of(espuma.id()), null));
    var c2 = categorias.criar(cat("Sofás " + s, List.of(espuma.id()), null));
    assertThat(categorias.toResponse(c1).getMateriais()).extracting(m -> m.id()).containsExactly(espuma.id());
    assertThat(categorias.toResponse(c2).getMateriais()).extracting(m -> m.id()).containsExactly(espuma.id());
    assertThat(catalogo.listarMateriais("espuma " + s, false).get(0).categorias()).isEqualTo(2);
  }

  @Test
  void categoriaSimplesNaoRecebeMaterialPadrao_eAdicionaMateriaisECaracteristicasDepois() {
    var c = categorias.criar(cat("Cadeiras " + s, null, null));
    var resp = categorias.toResponse(c);
    assertThat(resp.getMateriais()).isEmpty();           // nada de MDF automático
    assertThat(resp.getCaracteristicas()).isEmpty();
    var madeira = catalogo.criarMaterial("Madeira " + s);
    var tecido = catalogo.criarMaterial("Tecido " + s);
    categorias.atualizar(c.getId(), cat("Cadeiras " + s, List.of(madeira.id(), tecido.id()), List.of(
        car(null, "Altura", TipoCaracteristica.NUMERO, "cm", false, false))));
    var depois = categorias.toResponse(categorias.buscar(c.getId()));
    assertThat(depois.getMateriais()).hasSize(2);
    assertThat(depois.getCaracteristicas()).hasSize(1).first().satisfies(x -> assertThat(x.unidade()).isEqualTo("cm"));
    assertThatThrownBy(() -> categorias.criar(cat("cadeiras " + s, null, null))).isInstanceOf(NegocioException.class);
  }

  @Test
  void formularioDeProdutoDinamico_valoresValidadosPorTipoEPorCaracteristica() {
    var cc = colchoes();
    var densidade = caracteristica(cc, "Densidade");
    var conforto = caracteristica(cc, "Conforto");
    var medida = caracteristica(cc, "Altura");
    var obs = caracteristica(cc, "Observação técnica");
    var d33 = densidade.opcoes().get(1);

    // obrigatória (Densidade) ausente → recusa
    assertThatThrownBy(() -> criarProduto(cc, "Colchão A " + s, List.of(), List.of(), false)).isInstanceOf(NegocioException.class)
        .hasMessageContaining("Densidade");
    // valor correto: aparece com a exibição pronta para a vitrine
    var p = criarProduto(cc, "Colchão B " + s, List.of(),
        List.of(valorOpcao(densidade, d33.id()), valorNumero(medida, "25.5"), valorTexto(obs, "Linha premium")), false);
    assertThat(p.caracteristicas()).extracting(v -> v.nome() + "=" + v.exibicao())
        .contains("Densidade=D33", "Altura=25.5 cm", "Observação técnica=Linha premium");
    assertThat(p.pendencias()).isEmpty();
    assertThat(p.materiais()).isEmpty();                    // nada é preenchido sem escolha do usuário

    // valores incompatíveis (API direta)
    var outraOpcaoDeConforto = conforto.opcoes().get(0).id();
    assertThatThrownBy(() -> criarProduto(cc, "Colchão C " + s, List.of(), List.of(valorOpcao(densidade, outraOpcaoDeConforto)), false))
        .isInstanceOf(NegocioException.class).hasMessageContaining("não pertence");
    assertThatThrownBy(() -> criarProduto(cc, "Colchão D " + s, List.of(), List.of(valorOpcao(densidade, d33.id(), densidade.opcoes().get(0).id())),
        false)).isInstanceOf(NegocioException.class).hasMessageContaining("só uma opção");
    assertThatThrownBy(() -> criarProduto(cc, "Colchão E " + s, List.of(),
        List.of(valorOpcao(densidade, d33.id()), valorTexto(medida, "alto")), false)).isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> criarProduto(cc, "Colchão F " + s, List.of(),
        List.of(valorOpcao(densidade, d33.id()), valorNumero(obs, "10")), false)).isInstanceOf(NegocioException.class);
    // característica de OUTRA categoria
    var gr = categorias.toResponse(categorias.criar(cat("Guarda-roupas " + s, null, List.of(car(null, "Portas", TipoCaracteristica.NUMERO, null, false, false)))));
    var portas = gr.getCaracteristicas().get(0);
    assertThatThrownBy(() -> criarProduto(cc, "Colchão G " + s, List.of(),
        List.of(valorOpcao(densidade, d33.id()), valorNumero(portas, "2")), false)).isInstanceOf(NegocioException.class)
        .hasMessageContaining("não pertence à categoria");
  }

  @Test
  void materiaisDoProdutoSaoEscolhidosEValidadosContraACategoria() {
    var espuma = catalogo.criarMaterial("Espuma " + s);
    var madeira = catalogo.criarMaterial("Madeira " + s);
    var cat = categorias.criar(cat("Camas " + s, List.of(espuma.id()), null));
    var p1 = criarProdutoSimples(cat, "Cama 1 " + s, List.of(espuma.id()));
    assertThat(p1.materiais()).extracting(m -> m.nome()).containsExactly("Espuma " + s);
    assertThatThrownBy(() -> criarProdutoSimples(cat, "Cama 2 " + s, List.of(madeira.id()))).isInstanceOf(NegocioException.class)
        .hasMessageContaining("não está disponível");
    // material inativado: preserva o vínculo existente, mas não aceita nova seleção
    catalogo.atualizarMaterial(espuma.id(), null, false);
    var recarregado = produtos.buscar(p1.id());
    assertThat(recarregado.materiais()).hasSize(1);
    assertThatThrownBy(() -> criarProdutoSimples(cat, "Cama 3 " + s, List.of(espuma.id()))).isInstanceOf(NegocioException.class)
        .hasMessageContaining("inativo");
    assertThat(categorias.toResponse(categorias.buscar(cat.getId())).getMateriais()).hasSize(1);   // categoria também preservada
  }

  @Test
  void opcaoOuCaracteristicaEmUsoSoInativa_eTipoNaoMudaSeEmUso() {
    var cc = colchoes();
    var densidade = caracteristica(cc, "Densidade");
    var d28 = densidade.opcoes().get(0);
    var p = criarProduto(cc, "Colchão Uso " + s, List.of(), List.of(valorOpcao(densidade, d28.id())), false);

    // remove D28 (em uso) do formulário → fica inativa, valor do produto preservado
    var novasOpcoes = densidade.opcoes().stream().filter(o -> !o.id().equals(d28.id()))
        .map(o -> new OpcaoRequest(o.id(), o.valor(), true)).toList();
    categorias.atualizar(cc.getId(), cat(cc.getNome(), null, List.of(new CaracteristicaRequest(densidade.id(), "Densidade",
        TipoCaracteristica.SELECAO_UNICA, null, true, 0, true, true, novasOpcoes))));
    var depois = caracteristica(categorias.toResponse(categorias.buscar(cc.getId())), "Densidade");
    assertThat(depois.opcoes()).filteredOn(o -> o.id().equals(d28.id())).singleElement().satisfies(o -> assertThat(o.ativa()).isFalse());
    assertThat(produtos.buscar(p.id()).caracteristicas().get(0).exibicao()).isEqualTo("D28");
    // nova seleção da opção inativa é recusada
    assertThatThrownBy(() -> criarProduto(cc, "Colchão Novo " + s, List.of(), List.of(valorOpcao(depois, d28.id())), false))
        .isInstanceOf(NegocioException.class).hasMessageContaining("inativa");
    // tipo de característica usada não muda
    assertThatThrownBy(() -> categorias.atualizar(cc.getId(), cat(cc.getNome(), null, List.of(new CaracteristicaRequest(densidade.id(),
        "Densidade", TipoCaracteristica.TEXTO, null, true, 0, true, true, List.of()))))).isInstanceOf(NegocioException.class)
        .hasMessageContaining("não pode mudar");
    // característica usada que sai da lista é inativada; uma sem uso é removida de fato
    categorias.atualizar(cc.getId(), cat(cc.getNome(), null, List.of()));
    var apos = categorias.toResponse(categorias.buscar(cc.getId()));
    assertThat(apos.getCaracteristicas()).extracting(CaracteristicaView::nome).containsExactly("Densidade");
    assertThat(apos.getCaracteristicas().get(0).ativa()).isFalse();
    assertThat(produtos.buscar(p.id()).caracteristicas()).isNotEmpty();
  }

  @Test
  void trocarCategoriaMostraImpactoEExigeConfirmacaoSemDescartarEmSilencio() {
    var espuma = catalogo.criarMaterial("Espuma " + s);
    var cc = categorias.toResponse(categorias.criar(cat("Colchões " + s, List.of(espuma.id()), List.of(
        car(null, "Densidade", TipoCaracteristica.TEXTO, null, false, false)))));
    var dens = cc.getCaracteristicas().get(0);
    var p = criarProdutoComMateriais(cc, "Colchão Troca " + s, List.of(espuma.id()), List.of(valorTexto(dens, "D33")));
    var outra = categorias.toResponse(categorias.criar(cat("Sofás " + s, null, null)));

    var impacto = produtos.impactoCategoria(p.id(), outra.getId());
    assertThat(impacto.materiais()).containsExactly("Espuma " + s);
    assertThat(impacto.caracteristicas()).containsExactly("Densidade: D33");

    assertThatThrownBy(() -> atualizarCategoria(p, outra.getId(), false)).isInstanceOf(NegocioException.class)
        .hasMessageContaining("Confirme");
    assertThat(produtos.buscar(p.id()).caracteristicas()).hasSize(1);   // nada foi descartado
    var movido = atualizarCategoria(p, outra.getId(), true);
    assertThat(movido.categoriaId()).isEqualTo(outra.getId());
    assertThat(movido.materiais()).isEmpty();
    assertThat(movido.caracteristicas()).isEmpty();
  }

  @Test
  void novoCampoObrigatorioNaoInvalidaProdutoAntigo_masIdentificaOsPendentes() {
    var cat = categorias.toResponse(categorias.criar(cat("Mesas " + s, null, null)));
    var antigo = criarProdutoComMateriais(cat, "Mesa antiga " + s, List.of(), List.of());
    categorias.atualizar(cat.getId(), cat(cat.getNome(), null, List.of(car(null, "Material do tampo", TipoCaracteristica.TEXTO, null, true, false))));
    var depois = produtos.buscar(antigo.id());
    assertThat(depois.nome()).isEqualTo("Mesa antiga " + s);               // continua existindo e vendável
    assertThat(depois.pendencias()).containsExactly("Material do tampo");
    var resp = categorias.toResponse(categorias.buscar(cat.getId()));
    assertThat(resp.getProdutosPendentes()).isEqualTo(1);
    assertThat(catalogo.pendentesDaCategoria(cat.getId())).extracting(x -> x.id()).containsExactly(antigo.id());
  }

  @Test
  void permissoes_vendedorPreencheProdutoMasNaoConfiguraCategoriaNemMaterial() throws Exception {
    como(vendedor);
    assertThatThrownBy(() -> categorias.criar(cat("Proibida " + s, List.of(), null))).isInstanceOf(AccessDeniedException.class);
    assertThat(categorias.criar(cat("Simples do vendedor " + s, null, null)).getId()).isNotNull();   // categoria simples, como antes
    String tv = token(vendedor);
    mvc.perform(post("/api/v1/materiais").header("Authorization", "Bearer " + tv).contentType(MediaType.APPLICATION_JSON)
        .content("{\"nome\":\"Vidro " + s + "\"}")).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/materiais").header("Authorization", "Bearer " + token(gerente)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"nome\":\"Vidro " + s + "\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/materiais").header("Authorization", "Bearer " + token(gerente)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"nome\":\"vidro " + s + "\"}")).andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ helpers

  private CategoriaResponse colchoes() {
    var espuma = catalogo.criarMaterial("Espuma " + s);
    var latex = catalogo.criarMaterial("Látex " + s);
    return categorias.toResponse(categorias.criar(cat("Colchões " + s, List.of(espuma.id(), latex.id()), List.of(
        new CaracteristicaRequest(null, "Densidade", TipoCaracteristica.SELECAO_UNICA, null, true, 0, true, true,
            List.of(new OpcaoRequest(null, "D28", true), new OpcaoRequest(null, "D33", true), new OpcaoRequest(null, "D45", true))),
        new CaracteristicaRequest(null, "Conforto", TipoCaracteristica.SELECAO_UNICA, null, false, 1, true, true,
            List.of(new OpcaoRequest(null, "Macio", true), new OpcaoRequest(null, "Firme", true))),
        new CaracteristicaRequest(null, "Altura", TipoCaracteristica.NUMERO, "cm", false, 2, true, true, null),
        new CaracteristicaRequest(null, "Observação técnica", TipoCaracteristica.TEXTO, null, false, 3, false, true, null)))));
  }

  private static CaracteristicaView caracteristica(CategoriaResponse c, String nome) {
    return c.getCaracteristicas().stream().filter(x -> x.nome().equals(nome)).findFirst().orElseThrow();
  }

  private static CaracteristicaRequest car(Long id, String nome, TipoCaracteristica tipo, String unidade, boolean obrigatoria, boolean vitrine) {
    return new CaracteristicaRequest(id, nome, tipo, unidade, obrigatoria, null, vitrine, true, null);
  }

  private static CategoriaRequest cat(String nome, List<Long> materiais, List<CaracteristicaRequest> cars) {
    return CategoriaRequest.builder().nome(nome).materialIds(materiais).caracteristicas(cars).build();
  }

  private static ValorRequest valorOpcao(CaracteristicaView c, Long... opcoes) {
    return new ValorRequest(c.id(), List.of(opcoes), null, null);
  }

  private static ValorRequest valorNumero(CaracteristicaView c, String n) {
    return new ValorRequest(c.id(), null, null, new BigDecimal(n));
  }

  private static ValorRequest valorTexto(CaracteristicaView c, String t) {
    return new ValorRequest(c.id(), null, t, null);
  }

  private ProdutoResponse criarProduto(CategoriaResponse cat, String nome, List<Long> materiais, List<ValorRequest> valores, boolean x) {
    return criarProdutoComMateriais(cat, nome, materiais, valores);
  }

  private ProdutoResponse criarProdutoSimples(br.com.lojaspopular.domain.catalog.model.Categoria cat, String nome, List<Long> materiais) {
    return produtos.criar(request(cat.getId(), nome, materiais, List.of(), false));
  }

  private ProdutoResponse criarProdutoComMateriais(CategoriaResponse cat, String nome, List<Long> materiais, List<ValorRequest> valores) {
    return produtos.criar(request(cat.getId(), nome, materiais, valores, false));
  }

  private ProdutoResponse atualizarCategoria(ProdutoResponse p, Long categoriaId, boolean confirma) {
    var atual = produtos.buscar(p.id());
    return produtos.atualizar(p.id(), new ProdutoRequest(atual.nome(), atual.descricao(), atual.preco(), null, atual.estoque(), atual.sku(),
        atual.codigo(), categoriaId, null, null, null, null, null, List.of(), List.of(), atual.version(), atual.modalidade(), null, null, null,
        confirma));
  }

  private ProdutoRequest request(Long categoriaId, String nome, List<Long> materiais, List<ValorRequest> valores, boolean confirma) {
    return new ProdutoRequest(nome, null, new BigDecimal("100.00"), null, 5, "SKU-" + UUID.randomUUID().toString().substring(0, 10), null,
        categoriaId, null, null, null, null, null, List.of(), List.of(), null, null, null, materiais, valores, confirma);
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
