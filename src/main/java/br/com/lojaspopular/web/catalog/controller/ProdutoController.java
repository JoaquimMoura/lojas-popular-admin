// src/main/java/br/com/lojaspopular/web/catalog/controller/ProdutoController.java
package br.com.lojaspopular.web.catalog.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.application.catalog.ProdutoService;
import br.com.lojaspopular.domain.catalog.model.*;
import br.com.lojaspopular.web.catalog.dto.ProdutoRequest;
import br.com.lojaspopular.web.catalog.dto.ProdutoResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService service;

    @GetMapping
    public Page<ProdutoResponse> listar(@RequestParam(required = false) String nome,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("nome"));
        var list = service.listar(nome, pageable);
        var resp = list.stream().map(this::toResponse).toList();
        return new PageImpl<>(resp, pageable, list.getTotalElements());
    }

    @GetMapping("/{id}")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return toResponse(service.buscar(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ProdutoResponse criar(@RequestBody ProdutoRequest req) {
        var cat = req.categoriaId() != null ? service.buscarCategoria(req.categoriaId()) : null;

        Produto p = Produto.builder()
                .nome(req.nome())
                .descricao(req.descricao())
                .preco(req.preco())
                .estoque(req.estoque())
                .sku(req.sku())
                .categoria(cat)
                .build();

        if (req.variacoes() != null) {
            req.variacoes().forEach(v -> p.addVariacao(ProdutoVariacao.builder()
                    .cor(v.cor()).tamanho(v.tamanho())
                    .sku(v.sku()).adicionalPreco(v.adicionalPreco())
                    .estoque(v.estoque()).build()));
        }

        var result = service.salvar(p);
        return toResponse(result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ProdutoResponse atualizar(@PathVariable Long id, @RequestBody ProdutoRequest req) {
        var cat = req.categoriaId() != null ? service.buscarCategoria(req.categoriaId()) : null;
        var novo = Produto.builder()
                .nome(req.nome())
                .descricao(req.descricao())
                .preco(req.preco())
                .estoque(req.estoque())
                .sku(req.sku())
                .categoria(cat)
                .build();

        if (req.variacoes() != null) {
            req.variacoes().forEach(v -> novo.addVariacao(ProdutoVariacao.builder()
                    .cor(v.cor()).tamanho(v.tamanho())
                    .sku(v.sku()).adicionalPreco(v.adicionalPreco())
                    .estoque(v.estoque()).build()));
        }
        var att = service.atualizar(id, novo);
        return toResponse(att);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        // implementar exclusão se necessário
        return ResponseEntity.noContent().build();
    }

    // ===== Imagens =====

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/imagem")
    public ResponseEntity<String> uploadCapa(@PathVariable Long id, @RequestParam("file") MultipartFile file)
            throws IOException {
        var p = service.buscar(id);
        var url = service.salvarImagemCapa(file);
        p.setImagemUrl(url);
        service.salvar(p);
        return ResponseEntity.ok(url);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/galeria")
    public ResponseEntity<List<String>> uploadGaleria(@PathVariable Long id,
                                                      @RequestParam("files") List<MultipartFile> files)
            throws IOException {

        var p = service.buscar(id);
        int ordem = p.getGaleria().size();
        List<String> urls = new java.util.ArrayList<>();

        for (MultipartFile f : files) {
            var url = service.salvarImagemGaleria(f);
            p.addImagem(ProdutoImagem.builder().url(url).ordem(ordem++).build());
            urls.add(url);
        }
        service.salvar(p);
        return ResponseEntity.ok(urls);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/galeria")
    public ResponseEntity<Void> removerImagemGaleria(@PathVariable Long id, @RequestParam("url") String url) {
        service.removerImagemGaleria(id, url);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/galeria/reordena")
    public ResponseEntity<Void> reordenarGaleria(@PathVariable Long id, @RequestBody List<String> urlsNaOrdem) {
        service.reordenarGaleria(id, urlsNaOrdem);
        return ResponseEntity.noContent().build();
    }

    private ProdutoResponse toResponse(Produto p) {
        var variacoes = p.getVariacoes().stream()
                .map(v -> new ProdutoResponse.VariacaoResponse(v.getId(), v.getCor(),
                        v.getTamanho(), v.getSku(), v.getAdicionalPreco(), v.getEstoque()))
                .toList();
        var galeria = p.getGaleria().stream().map(ProdutoImagem::getUrl).toList();

        return new ProdutoResponse(
                p.getId(), p.getNome(), p.getDescricao(), p.getPreco(), p.getEstoque(), p.getSku(),
                p.getImagemUrl(), p.getCategoria() != null ? p.getCategoria().getNome() : null,
                variacoes, galeria
        );
    }
}
