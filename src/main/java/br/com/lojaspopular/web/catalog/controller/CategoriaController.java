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
import br.com.lojaspopular.domain.catalog.mapper.CategoriaMapper;
import br.com.lojaspopular.web.catalog.dto.CategoriaRequest;
import br.com.lojaspopular.web.catalog.dto.CategoriaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
public class CategoriaController {

  private final CategoriaService service;
  private final CategoriaMapper mapper;

  @GetMapping
  public ResponseEntity<List<CategoriaResponse>> listar() {
    var categorias = service.listar();
    return ResponseEntity.ok(mapper.toResponseList(categorias));
  }

  @GetMapping("/{id}")
  public ResponseEntity<CategoriaResponse> buscar(@PathVariable Long id) {
    var categoria = service.buscar(id);
    return ResponseEntity.ok(mapper.toResponse(categoria));
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PostMapping
  public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest req) {
    var entidade = mapper.toEntity(req);
    var salva = service.salvar(entidade);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(salva));
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @PutMapping("/{id}")
  public ResponseEntity<CategoriaResponse> atualizar(
      @PathVariable Long id,
      @Valid @RequestBody CategoriaRequest req) {
    var entidade = mapper.toEntity(req);
    var atualizada = service.atualizar(id, entidade);
    return ResponseEntity.ok(mapper.toResponse(atualizada));
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> excluir(@PathVariable Long id) {
    service.excluir(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
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
