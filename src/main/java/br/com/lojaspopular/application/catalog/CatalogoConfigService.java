package br.com.lojaspopular.application.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.Normalizacao;
import br.com.lojaspopular.domain.catalog.enums.TipoCaracteristica;
import br.com.lojaspopular.domain.catalog.model.Caracteristica;
import br.com.lojaspopular.domain.catalog.model.CaracteristicaOpcao;
import br.com.lojaspopular.domain.catalog.model.Categoria;
import br.com.lojaspopular.domain.catalog.model.Material;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoCaracteristicaValor;
import br.com.lojaspopular.domain.catalog.repository.CaracteristicaRepository;
import br.com.lojaspopular.domain.catalog.repository.CategoriaRepository;
import br.com.lojaspopular.domain.catalog.repository.MaterialRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoCaracteristicaValorRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.CaracteristicaRequest;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.CaracteristicaView;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.Impacto;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.MaterialRef;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.MaterialView;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.OpcaoRequest;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.OpcaoView;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.ProdutoPendente;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.ValorRequest;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.ValorView;
import lombok.RequiredArgsConstructor;

/**
 * Catálogo dinâmico: materiais compartilhados, características configuráveis por categoria e valores nos produtos. Nenhuma regra
 * depende do NOME de uma categoria. Valores inválidos são rejeitados aqui (também por API direta): tipo errado, opção de outra
 * característica, opção inativa nova, obrigatório ausente. Remover algo em uso apenas inativa (o histórico é preservado).
 */
@Service
@RequiredArgsConstructor
public class CatalogoConfigService {

  private final MaterialRepository materiais;
  private final CategoriaRepository categorias;
  private final CaracteristicaRepository caracteristicas;
  private final ProdutoRepository produtos;
  private final ProdutoCaracteristicaValorRepository valores;
  private final AuditoriaService auditoria;

  // ================================================================== materiais

  @Transactional(readOnly = true)
  public List<MaterialView> listarMateriais(String q, boolean incluirInativos) {
    String termo = Normalizacao.normalizar(q);
    return materiais.findAllByOrderByNomeAsc().stream()
        .filter(m -> incluirInativos || m.isAtivo())
        .filter(m -> termo.isEmpty() || m.getNomeNormalizado().contains(termo))
        .map(this::view).toList();
  }

  @Transactional
  public MaterialView criarMaterial(String nome) {
    String limpo = nomeValido(nome, "Informe o nome do material.", 100);
    String norm = Normalizacao.normalizar(limpo);
    var existente = materiais.findByNomeNormalizado(norm);
    if (existente.isPresent()) {
      throw new NegocioException(existente.get().isAtivo()
          ? "Já existe o material \"" + existente.get().getNome() + "\". Selecione-o na lista."
          : "O material \"" + existente.get().getNome() + "\" já existe, mas está inativo. Reative-o em vez de cadastrar de novo.");
    }
    Material m = materiais.save(Material.builder().nome(limpo).nomeNormalizado(norm).build());
    auditoria.registrar(AuditoriaTipo.MATERIAL_CRIADO, "Material cadastrado: " + m.getNome(), "MATERIAL", m.getId());
    return view(m);
  }

  @Transactional
  public MaterialView atualizarMaterial(Long id, String nome, Boolean ativo) {
    Material m = materiais.findById(id).orElseThrow(() -> new NotFoundException("Material não encontrado"));
    if (nome != null && !nome.isBlank()) {
      String limpo = nomeValido(nome, "Informe o nome do material.", 100);
      String norm = Normalizacao.normalizar(limpo);
      materiais.findByNomeNormalizado(norm).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
        throw new NegocioException("Já existe o material \"" + o.getNome() + "\".");
      });
      m.setNome(limpo);
      m.setNomeNormalizado(norm);
    }
    if (ativo != null) {
      m.setAtivo(ativo);   // inativar preserva os vínculos existentes; só impede novas seleções
    }
    auditoria.registrar(AuditoriaTipo.MATERIAL_ALTERADO, "Material alterado: " + m.getNome() + (m.isAtivo() ? "" : " (inativo)"),
        "MATERIAL", m.getId());
    return view(m);
  }

  private MaterialView view(Material m) {
    long cats = categorias.findAll().stream().filter(c -> c.getMateriais().stream().anyMatch(x -> x.getId().equals(m.getId()))).count();
    return new MaterialView(m.getId(), m.getNome(), m.isAtivo(), cats, produtos.contarPorMaterial(m.getId()));
  }

  public MaterialRef ref(Material m) {
    return new MaterialRef(m.getId(), m.getNome(), m.isAtivo());
  }

  // ================================================================== categoria

  /** Aplica materiais e características recebidos (null = não mexe). Validação completa; remover algo em uso só inativa. */
  @Transactional
  public void configurarCategoria(Categoria cat, List<Long> materialIds, List<CaracteristicaRequest> reqs) {
    if (materialIds != null) {
      Set<Long> ids = new LinkedHashSet<>(materialIds);
      Set<Long> jaVinculados = cat.getMateriais().stream().map(Material::getId).collect(Collectors.toSet());
      Set<Material> novos = new LinkedHashSet<>();
      for (Long mid : ids) {
        Material m = materiais.findById(mid).orElseThrow(() -> new NegocioException("Material inexistente (id " + mid + ")."));
        if (!m.isAtivo() && !jaVinculados.contains(mid)) {
          throw new NegocioException("O material \"" + m.getNome() + "\" está inativo e não pode ser selecionado.");
        }
        novos.add(m);
      }
      cat.getMateriais().clear();
      cat.getMateriais().addAll(novos);
    }
    if (reqs != null) {
      sincronizarCaracteristicas(cat, reqs);
    }
    if (materialIds != null || reqs != null) {
      auditoria.registrar(AuditoriaTipo.CATEGORIA_CONFIGURADA, "Categoria \"" + cat.getNome() + "\": materiais e características configurados",
          "CATEGORIA", cat.getId());
    }
  }

  private void sincronizarCaracteristicas(Categoria cat, List<CaracteristicaRequest> reqs) {
    List<Caracteristica> existentes = caracteristicas.findByCategoriaIdOrderByOrdemAscIdAsc(cat.getId());
    Map<Long, Caracteristica> porId = existentes.stream().collect(Collectors.toMap(Caracteristica::getId, c -> c));
    Set<String> nomes = new HashSet<>();
    Set<Long> mantidas = new HashSet<>();
    int pos = 0;
    for (CaracteristicaRequest r : reqs) {
      String nome = nomeValido(r.nome(), "Informe o nome de cada característica.", 100);
      String norm = Normalizacao.normalizar(nome);
      if (!nomes.add(norm)) {
        throw new NegocioException("A característica \"" + nome + "\" está repetida nesta categoria.");
      }
      if (r.tipo() == null) {
        throw new NegocioException("Informe o tipo da característica \"" + nome + "\" (texto, número, seleção única ou múltipla).");
      }
      Caracteristica c;
      if (r.id() == null) {
        c = Caracteristica.builder().categoria(cat).tipo(r.tipo()).build();
        existentes.add(c);
      } else {
        c = porId.get(r.id());
        if (c == null) {
          throw new NegocioException("A característica #" + r.id() + " não pertence a esta categoria.");
        }
        if (c.getTipo() != r.tipo() && valores.produtosComCaracteristica(c.getId()) > 0) {
          throw new NegocioException("O tipo de \"" + c.getNome() + "\" não pode mudar porque já há produtos com esse valor. Crie outra característica.");
        }
        c.setTipo(r.tipo());
        mantidas.add(c.getId());
      }
      c.setNome(nome);
      c.setNomeNormalizado(norm);
      c.setUnidade(r.tipo() == TipoCaracteristica.NUMERO && r.unidade() != null && !r.unidade().isBlank() ? r.unidade().trim() : null);
      c.setObrigatoria(Boolean.TRUE.equals(r.obrigatoria()));
      c.setExibirNaVitrine(Boolean.TRUE.equals(r.exibirNaVitrine()));
      c.setOrdem(r.ordem() != null ? r.ordem() : pos);
      c.setAtiva(r.ativa() == null || r.ativa());
      pos++;
      boolean selecao = r.tipo() == TipoCaracteristica.SELECAO_UNICA || r.tipo() == TipoCaracteristica.SELECAO_MULTIPLA;
      if (selecao) {
        sincronizarOpcoes(c, r.opcoes() == null ? List.of() : r.opcoes());
      } else if (r.opcoes() != null && !r.opcoes().isEmpty()) {
        throw new NegocioException("A característica \"" + nome + "\" não é de seleção: não pode ter opções.");
      }
      if (c.getId() == null) {
        caracteristicas.save(c);
        mantidas.add(c.getId());
      }
    }
    // o que saiu do formulário: se já foi usado, só inativa (preserva o histórico); senão remove
    for (Caracteristica c : existentes) {
      if (c.getId() != null && !mantidas.contains(c.getId())) {
        if (valores.produtosComCaracteristica(c.getId()) > 0) {
          c.setAtiva(false);
        } else {
          caracteristicas.delete(c);
        }
      }
    }
  }

  private void sincronizarOpcoes(Caracteristica c, List<OpcaoRequest> reqs) {
    Map<Long, CaracteristicaOpcao> porId = c.getOpcoes().stream().filter(o -> o.getId() != null)
        .collect(Collectors.toMap(CaracteristicaOpcao::getId, o -> o));
    Set<String> normas = new HashSet<>();
    Set<Long> mantidas = new HashSet<>();
    int pos = 0;
    for (OpcaoRequest r : reqs) {
      String valor = nomeValido(r.valor(), "Informe o texto de cada opção de \"" + c.getNome() + "\".", 100);
      String norm = Normalizacao.normalizar(valor);
      if (!normas.add(norm)) {
        throw new NegocioException("A opção \"" + valor + "\" está repetida em \"" + c.getNome() + "\".");
      }
      CaracteristicaOpcao o;
      if (r.id() == null) {
        o = CaracteristicaOpcao.builder().caracteristica(c).build();
        c.getOpcoes().add(o);
      } else {
        o = porId.get(r.id());
        if (o == null) {
          throw new NegocioException("A opção #" + r.id() + " não pertence à característica \"" + c.getNome() + "\".");
        }
        mantidas.add(o.getId());
      }
      o.setValor(valor);
      o.setValorNormalizado(norm);
      o.setOrdem(pos++);
      o.setAtiva(r.ativa() == null || r.ativa());
    }
    for (CaracteristicaOpcao o : new ArrayList<>(c.getOpcoes())) {
      if (o.getId() != null && !mantidas.contains(o.getId())) {
        if (valores.existsByOpcaoId(o.getId())) {
          o.setAtiva(false);
        } else {
          c.getOpcoes().remove(o);   // orphanRemoval
        }
      }
    }
  }

  @Transactional(readOnly = true)
  public List<CaracteristicaView> caracteristicasDaCategoria(Categoria cat) {
    return caracteristicas.findByCategoriaIdOrderByOrdemAscIdAsc(cat.getId()).stream().map(this::view).toList();
  }

  private CaracteristicaView view(Caracteristica c) {
    return new CaracteristicaView(c.getId(), c.getNome(), c.getTipo(), c.getUnidade(), c.isObrigatoria(), c.getOrdem(),
        c.isExibirNaVitrine(), c.isAtiva(), valores.produtosComCaracteristica(c.getId()),
        c.getOpcoes().stream().map(o -> new OpcaoView(o.getId(), o.getValor(), o.isAtiva(), o.getOrdem())).toList());
  }

  @Transactional(readOnly = true)
  public List<ProdutoPendente> pendentesDaCategoria(Long categoriaId) {
    return produtos.findAllByCategoriaId(categoriaId).stream().map(p -> new ProdutoPendente(p.getId(), p.getNome(), faltando(p)))
        .filter(x -> !x.faltando().isEmpty()).toList();
  }

  // ================================================================== produto

  /** Nomes das características obrigatórias ativas sem valor (cadastros que precisam de complementação). */
  @Transactional(readOnly = true)
  public List<String> faltando(Produto p) {
    if (p.getCategoria() == null) {
      return List.of();
    }
    Set<Long> preenchidas = valores.findByProdutoId(p.getId()).stream().map(v -> v.getCaracteristica().getId()).collect(Collectors.toSet());
    return caracteristicas.findByCategoriaIdOrderByOrdemAscIdAsc(p.getCategoria().getId()).stream()
        .filter(c -> c.isAtiva() && c.isObrigatoria() && !preenchidas.contains(c.getId())).map(Caracteristica::getNome).toList();
  }

  /** O que seria descartado ao mover o produto para outra categoria (antes de salvar). */
  @Transactional(readOnly = true)
  public Impacto impactoTroca(Produto p, Categoria nova) {
    Long novaId = nova == null ? null : nova.getId();
    Set<Long> matsNova = nova == null ? Set.of() : nova.getMateriais().stream().map(Material::getId).collect(Collectors.toSet());
    List<String> mats = p.getMateriais().stream().filter(m -> !matsNova.contains(m.getId())).map(Material::getNome).toList();
    Map<String, List<String>> porCar = new java.util.LinkedHashMap<>();
    for (ProdutoCaracteristicaValor v : valores.findByProdutoId(p.getId())) {
      if (novaId == null || !v.getCaracteristica().getCategoria().getId().equals(novaId)) {
        porCar.computeIfAbsent(v.getCaracteristica().getNome(), k -> new ArrayList<>()).add(textoValor(v));
      }
    }
    List<String> cars = porCar.entrySet().stream().map(e -> e.getKey() + ": " + String.join(", ", e.getValue())).toList();
    return new Impacto(mats, cars);
  }

  /** Bloqueia a troca de categoria que descartaria dados sem confirmação explícita. */
  @Transactional(readOnly = true)
  public void exigirConfirmacaoTroca(Produto p, Categoria nova, boolean confirmou) {
    Long atual = p.getCategoria() == null ? null : p.getCategoria().getId();
    Long novaId = nova == null ? null : nova.getId();
    if (java.util.Objects.equals(atual, novaId)) {
      return;
    }
    Impacto i = impactoTroca(p, nova);
    if (!i.vazio() && !confirmou) {
      throw new NegocioException("Trocar a categoria vai descartar dados que não se aplicam à nova categoria"
          + (i.materiais().isEmpty() ? "" : " (materiais: " + String.join(", ", i.materiais()) + ")")
          + (i.caracteristicas().isEmpty() ? "" : " (características: " + String.join("; ", i.caracteristicas()) + ")")
          + ". Confirme para continuar.");
    }
  }

  /**
   * Aplica materiais e valores recebidos do formulário do produto (null = não altera). Se a categoria mudou, descarta o que não se
   * aplica à nova (já confirmado). Exige as características obrigatórias ativas quando os valores vêm no pedido.
   */
  @Transactional
  public void aplicarProduto(Produto p, Long categoriaAnteriorId, List<Long> materialIds, List<ValorRequest> reqs) {
    Categoria cat = p.getCategoria();
    Long catId = cat == null ? null : cat.getId();
    if (!java.util.Objects.equals(categoriaAnteriorId, catId)) {
      Set<Long> matsCat = cat == null ? Set.of() : cat.getMateriais().stream().map(Material::getId).collect(Collectors.toSet());
      p.getMateriais().removeIf(m -> !matsCat.contains(m.getId()));
      for (ProdutoCaracteristicaValor v : valores.findByProdutoId(p.getId())) {
        if (catId == null || !v.getCaracteristica().getCategoria().getId().equals(catId)) {
          valores.delete(v);
        }
      }
      valores.flush();
    }
    if (materialIds != null) {
      aplicarMateriais(p, cat, materialIds);
    }
    if (reqs != null) {
      aplicarValores(p, cat, reqs);
    }
  }

  private void aplicarMateriais(Produto p, Categoria cat, List<Long> ids) {
    Set<Long> atuais = p.getMateriais().stream().map(Material::getId).collect(Collectors.toSet());
    Set<Long> disponiveis = cat == null ? Set.of() : cat.getMateriais().stream().map(Material::getId).collect(Collectors.toSet());
    Set<Material> novos = new LinkedHashSet<>();
    for (Long id : new LinkedHashSet<>(ids)) {
      Material m = materiais.findById(id).orElseThrow(() -> new NegocioException("Material inexistente (id " + id + ")."));
      if (!atuais.contains(id)) {   // seleção NOVA: precisa estar ativo e disponível na categoria
        if (!m.isAtivo()) {
          throw new NegocioException("O material \"" + m.getNome() + "\" está inativo e não pode ser selecionado.");
        }
        if (!disponiveis.contains(id)) {
          throw new NegocioException("O material \"" + m.getNome() + "\" não está disponível para esta categoria.");
        }
      }
      novos.add(m);
    }
    p.getMateriais().clear();
    p.getMateriais().addAll(novos);
  }

  private void aplicarValores(Produto p, Categoria cat, List<ValorRequest> reqs) {
    if (cat == null) {
      if (!reqs.isEmpty()) {
        throw new NegocioException("Escolha a categoria antes de informar características.");
      }
      return;
    }
    Map<Long, Caracteristica> daCategoria = caracteristicas.findByCategoriaIdOrderByOrdemAscIdAsc(cat.getId()).stream()
        .collect(Collectors.toMap(Caracteristica::getId, c -> c));
    List<ProdutoCaracteristicaValor> atuais = p.getId() == null ? List.of() : valores.findByProdutoId(p.getId());
    Set<Long> opcoesJaSelecionadas = atuais.stream().filter(v -> v.getOpcao() != null).map(v -> v.getOpcao().getId()).collect(Collectors.toSet());
    Map<Long, ValorRequest> recebidos = new HashMap<>();
    for (ValorRequest r : reqs) {
      if (r.caracteristicaId() == null || !daCategoria.containsKey(r.caracteristicaId())) {
        throw new NegocioException("A característica #" + r.caracteristicaId() + " não pertence à categoria do produto.");
      }
      if (recebidos.put(r.caracteristicaId(), r) != null) {
        throw new NegocioException("A característica \"" + daCategoria.get(r.caracteristicaId()).getNome() + "\" foi informada duas vezes.");
      }
    }
    List<ProdutoCaracteristicaValor> novos = new ArrayList<>();
    for (Caracteristica c : daCategoria.values()) {
      ValorRequest r = recebidos.get(c.getId());
      boolean vazio = r == null || ((r.valorTexto() == null || r.valorTexto().isBlank()) && r.valorNumero() == null
          && (r.opcaoIds() == null || r.opcaoIds().isEmpty()));
      if (vazio) {
        if (c.isAtiva() && c.isObrigatoria()) {
          throw new NegocioException("Preencha \"" + c.getNome() + "\" (campo obrigatório).");
        }
        continue;
      }
      if (!c.isAtiva() && !atuais.stream().anyMatch(v -> v.getCaracteristica().getId().equals(c.getId()))) {
        throw new NegocioException("A característica \"" + c.getNome() + "\" está inativa e não aceita novos valores.");
      }
      switch (c.getTipo()) {
        case TEXTO -> {
          String t = r.valorTexto() == null ? "" : r.valorTexto().trim();
          if (t.isEmpty() || t.length() > 300) {
            throw new NegocioException("\"" + c.getNome() + "\": informe um texto de até 300 caracteres.");
          }
          if (r.valorNumero() != null || (r.opcaoIds() != null && !r.opcaoIds().isEmpty())) {
            throw new NegocioException("\"" + c.getNome() + "\" é um campo de texto.");
          }
          novos.add(ProdutoCaracteristicaValor.builder().produto(p).caracteristica(c).valorTexto(t).build());
        }
        case NUMERO -> {
          BigDecimal n = r.valorNumero();
          if (n == null || n.signum() < 0) {
            throw new NegocioException("\"" + c.getNome() + "\": informe um número igual ou maior que zero.");
          }
          if (r.valorTexto() != null && !r.valorTexto().isBlank() || (r.opcaoIds() != null && !r.opcaoIds().isEmpty())) {
            throw new NegocioException("\"" + c.getNome() + "\" é um campo numérico.");
          }
          novos.add(ProdutoCaracteristicaValor.builder().produto(p).caracteristica(c).valorNumero(n).build());
        }
        case SELECAO_UNICA, SELECAO_MULTIPLA -> {
          List<Long> ids = r.opcaoIds() == null ? List.of() : r.opcaoIds().stream().distinct().toList();
          if (ids.isEmpty() || (r.valorTexto() != null && !r.valorTexto().isBlank()) || r.valorNumero() != null) {
            throw new NegocioException("\"" + c.getNome() + "\": escolha uma das opções.");
          }
          if (c.getTipo() == TipoCaracteristica.SELECAO_UNICA && ids.size() > 1) {
            throw new NegocioException("\"" + c.getNome() + "\" aceita só uma opção.");
          }
          Map<Long, CaracteristicaOpcao> opcoes = c.getOpcoes().stream().collect(Collectors.toMap(CaracteristicaOpcao::getId, o -> o));
          for (Long oid : ids) {
            CaracteristicaOpcao o = opcoes.get(oid);
            if (o == null) {
              throw new NegocioException("A opção #" + oid + " não pertence à característica \"" + c.getNome() + "\".");
            }
            if (!o.isAtiva() && !opcoesJaSelecionadas.contains(oid)) {
              throw new NegocioException("A opção \"" + o.getValor() + "\" de \"" + c.getNome() + "\" está inativa e não pode ser escolhida.");
            }
            novos.add(ProdutoCaracteristicaValor.builder().produto(p).caracteristica(c).opcao(o).build());
          }
        }
      }
    }
    // substitui só o que veio no pedido (valores de características fora dele, por ex. inativas, permanecem)
    Set<Long> tocadas = daCategoria.keySet();
    for (ProdutoCaracteristicaValor v : atuais) {
      if (tocadas.contains(v.getCaracteristica().getId()) && (recebidos.containsKey(v.getCaracteristica().getId())
          || v.getCaracteristica().isAtiva())) {
        valores.delete(v);
      }
    }
    valores.flush();
    valores.saveAll(novos);
  }

  @Transactional(readOnly = true)
  public List<MaterialRef> materiaisDoProduto(Produto p) {
    return p.getMateriais().stream().sorted(Comparator.comparing(Material::getNome)).map(this::ref).toList();
  }

  @Transactional(readOnly = true)
  public List<ValorView> valoresDoProduto(Produto p) {
    if (p.getId() == null) {
      return List.of();
    }
    Map<Long, List<ProdutoCaracteristicaValor>> porCar = valores.findByProdutoId(p.getId()).stream()
        .collect(Collectors.groupingBy(v -> v.getCaracteristica().getId(), java.util.LinkedHashMap::new, Collectors.toList()));
    return porCar.values().stream().map(lista -> {
      Caracteristica c = lista.get(0).getCaracteristica();
      var ops = lista.stream().filter(v -> v.getOpcao() != null).map(v -> v.getOpcao())
          .map(o -> new OpcaoView(o.getId(), o.getValor(), o.isAtiva(), o.getOrdem())).toList();
      String texto = lista.get(0).getValorTexto();
      BigDecimal num = lista.get(0).getValorNumero();
      String exibicao = !ops.isEmpty() ? ops.stream().map(OpcaoView::valor).collect(Collectors.joining(", "))
          : num != null ? num.stripTrailingZeros().toPlainString() + (c.getUnidade() == null ? "" : " " + c.getUnidade()) : texto;
      return new ValorView(c.getId(), c.getNome(), c.getTipo(), c.getUnidade(), c.isExibirNaVitrine(), c.getOrdem(), c.isAtiva(), texto,
          num, ops, exibicao);
    }).sorted(Comparator.comparingInt(ValorView::ordem).thenComparing(ValorView::nome)).toList();
  }

  private String textoValor(ProdutoCaracteristicaValor v) {
    if (v.getOpcao() != null) {
      return v.getOpcao().getValor();
    }
    if (v.getValorNumero() != null) {
      return v.getValorNumero().stripTrailingZeros().toPlainString() + (v.getCaracteristica().getUnidade() == null ? ""
          : " " + v.getCaracteristica().getUnidade());
    }
    return v.getValorTexto();
  }

  private static String nomeValido(String nome, String mensagem, int max) {
    String limpo = Normalizacao.limpar(nome);
    if (limpo == null || limpo.isEmpty()) {
      throw new NegocioException(mensagem);
    }
    if (limpo.length() > max) {
      throw new NegocioException("O texto deve ter no máximo " + max + " caracteres.");
    }
    return limpo;
  }
}
