package br.com.lojaspopular.web.catalog.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import br.com.lojaspopular.application.catalog.CategoriaService;
import br.com.lojaspopular.web.catalog.dto.CategoriaRequest;
import br.com.lojaspopular.web.catalog.dto.CategoriaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
public class CategoriaController {

  private final CategoriaService service;
  private final br.com.lojaspopular.application.catalog.CatalogoConfigService catalogo;

  @GetMapping
  public ResponseEntity<List<CategoriaResponse>> listar() {
    return ResponseEntity.ok(service.listar().stream().map(service::toResponse).toList());
  }

  @GetMapping("/{id}")
  public ResponseEntity<CategoriaResponse> buscar(@PathVariable Long id) {
    var categoria = service.buscar(id);
    return ResponseEntity.ok(service.toResponse(categoria));
  }

  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  @PostMapping
  public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.toResponse(service.criar(req)));
  }

  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  @PutMapping("/{id}")
  public ResponseEntity<CategoriaResponse> atualizar(
      @PathVariable Long id,
      @Valid @RequestBody CategoriaRequest req) {
    return ResponseEntity.ok(service.toResponse(service.atualizar(id, req)));
  }

  /** Produtos da categoria que precisam de complementação (característica obrigatória sem valor). */
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  @GetMapping("/{id}/pendencias")
  public ResponseEntity<List<br.com.lojaspopular.web.catalog.dto.CatalogoDtos.ProdutoPendente>> pendencias(@PathVariable Long id) {
    return ResponseEntity.ok(catalogo.pendentesDaCategoria(id));
  }

  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> excluir(@PathVariable Long id) {
    service.excluir(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  @PostMapping("/{id}/imagem")
  public ResponseEntity<String> uploadImagem(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file) throws IOException {

    String url = service.salvarImagemCategoria(file);
    var categoria = service.buscar(id);

    categoria.setImagemUrl(url);
    service.salvar(categoria);

    return ResponseEntity.ok(url);
  }
}
