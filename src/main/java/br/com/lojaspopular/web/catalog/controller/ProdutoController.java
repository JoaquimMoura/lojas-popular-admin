package br.com.lojaspopular.web.catalog.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.application.catalog.ProdutoService;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoImagem;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.web.catalog.dto.ProdutoRequest;
import br.com.lojaspopular.web.catalog.dto.ProdutoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/produtos")
@RequiredArgsConstructor
public class ProdutoController {

  private final ProdutoService service;

  @GetMapping
  @Transactional(readOnly = true)
  public Page<ProdutoResponse> listar(
      @RequestParam(required = false) String nome,
      @RequestParam(required = false) Long categoriaId,
      @RequestParam(required = false) String categoria,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {

    Pageable pageable = PageRequest.of(page, size, Sort.by("nome"));
    var list = service.listar(nome, categoriaId, categoria, pageable);
    var resp = list.stream().map(this::toResponse).toList();
    return new PageImpl<>(resp, pageable, list.getTotalElements());
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public ProdutoResponse buscar(@PathVariable Long id) {
    return toResponse(service.buscar(id));
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PostMapping
  @Transactional
  public ProdutoResponse criar(@Valid @RequestBody ProdutoRequest req) {
    var cat = req.categoriaId() != null ? service.buscarCategoria(req.categoriaId()) : null;

    Produto produto = Produto.builder()
        .nome(req.nome())
        .descricao(req.descricao())
        .preco(req.preco())
        .precoOriginal(req.precoOriginal())
        .estoque(req.estoque())
        .sku(req.sku())
        .codigo(req.codigo())
        .categoria(cat)
        .largura(req.largura())
        .altura(req.altura())
        .profundidade(req.profundidade())
        .peso(req.peso())
        .volumes(req.volumes())
        .build();

    if (req.diferenciais() != null) {
      produto.getDiferenciais().addAll(req.diferenciais());
    }

    if (req.variacoes() != null) {
      req.variacoes().forEach(v -> produto.addVariacao(ProdutoVariacao.builder()
          .cor(v.cor())
          .tamanho(v.tamanho())
          .sku(v.sku())
          .adicionalPreco(v.adicionalPreco())
          .estoque(v.estoque())
          .imagemUrl(v.imagemUrl())
          .build()));
    }

    var result = service.salvar(produto);
    return toResponse(result);
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PutMapping("/{id}")
  @Transactional
  public ProdutoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequest req) {
    var cat = req.categoriaId() != null ? service.buscarCategoria(req.categoriaId()) : null;

    List<ProdutoVariacao> variacoes = new ArrayList<>();
    if (req.variacoes() != null) {
      req.variacoes().forEach(v -> variacoes.add(ProdutoVariacao.builder()
          .cor(v.cor())
          .tamanho(v.tamanho())
          .sku(v.sku())
          .adicionalPreco(v.adicionalPreco())
          .estoque(v.estoque())
          .imagemUrl(v.imagemUrl())
          .build()));
    }

    var novo = Produto.builder()
        .nome(req.nome())
        .descricao(req.descricao())
        .preco(req.preco())
        .precoOriginal(req.precoOriginal())
        .estoque(req.estoque())
        .sku(req.sku())
        .codigo(req.codigo())
        .categoria(cat)
        .largura(req.largura())
        .altura(req.altura())
        .profundidade(req.profundidade())
        .peso(req.peso())
        .volumes(req.volumes())
        .version(req.version())
        .build();

    if (req.diferenciais() != null) {
      novo.getDiferenciais().addAll(req.diferenciais());
    }
    variacoes.forEach(novo::addVariacao);

    var att = service.atualizar(id, novo);
    return toResponse(att);
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> excluir(@PathVariable Long id) {
    service.excluir(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PostMapping("/{id}/imagem")
  @Transactional
  public ResponseEntity<String> uploadCapa(@PathVariable Long id, @RequestParam("file") MultipartFile file)
      throws IOException {
    var produto = service.buscar(id);
    var url = service.salvarImagemCapa(file, produto.getCategoria());
    produto.setImagemUrl(url);
    service.salvar(produto);
    return ResponseEntity.ok(url);
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PostMapping("/{produtoId}/variacoes/{variacaoId}/imagem")
  @Transactional
  public ResponseEntity<String> uploadVariacaoImagem(
      @PathVariable Long produtoId,
      @PathVariable Long variacaoId,
      @RequestParam("file") MultipartFile file) throws IOException {
    var url = service.salvarImagemVariacao(produtoId, variacaoId, file);
    return ResponseEntity.ok(url);
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PostMapping("/{id}/galeria")
  @Transactional
  public ResponseEntity<List<String>> uploadGaleria(
      @PathVariable Long id,
      @RequestParam("files") List<MultipartFile> files) throws IOException {

    var produto = service.buscar(id);
    int ordem = produto.getGaleria().size();
    List<String> urls = new ArrayList<>();

    for (MultipartFile file : files) {
      var url = service.salvarImagemGaleria(file, produto.getCategoria());
      produto.addImagem(ProdutoImagem.builder().url(url).ordem(ordem++).build());
      urls.add(url);
    }
    service.salvar(produto);
    return ResponseEntity.ok(urls);
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @DeleteMapping("/{id}/galeria")
  public ResponseEntity<Void> removerImagemGaleria(@PathVariable Long id, @RequestParam("url") String url) {
    service.removerImagemGaleria(id, url);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PutMapping("/{id}/galeria/reordena")
  public ResponseEntity<Void> reordenarGaleria(@PathVariable Long id, @RequestBody List<String> urlsNaOrdem) {
    service.reordenarGaleria(id, urlsNaOrdem);
    return ResponseEntity.noContent().build();
  }

  private ProdutoResponse toResponse(Produto produto) {
    var variacoes = produto.getVariacoes().stream()
        .map(v -> new ProdutoResponse.VariacaoResponse(
            v.getId(),
            v.getCor(),
            v.getTamanho(),
            v.getSku(),
            v.getAdicionalPreco(),
            v.getEstoque(),
            v.getImagemUrl()))
        .toList();
    var galeria = produto.getGaleria().stream().map(ProdutoImagem::getUrl).toList();

    return new ProdutoResponse(
        produto.getId(),
        produto.getNome(),
        produto.getDescricao(),
        produto.getPreco(),
        produto.getPrecoOriginal(),
        produto.getEstoque(),
        produto.getSku(),
        produto.getCodigo(),
        produto.getImagemUrl(),
        produto.getCategoria() != null ? produto.getCategoria().getNome() : null,
        produto.getCategoria() != null ? produto.getCategoria().getId() : null,
        produto.getLargura(),
        produto.getAltura(),
        produto.getProfundidade(),
        produto.getPeso(),
        produto.getVolumes(),
        produto.getDiferenciais(),
        variacoes,
        galeria,
        produto.getVersion());
  }
}
