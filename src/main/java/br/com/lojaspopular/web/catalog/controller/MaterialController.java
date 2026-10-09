package br.com.lojaspopular.web.catalog.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.catalog.CatalogoConfigService;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.MaterialRequest;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.MaterialView;
import lombok.RequiredArgsConstructor;

/** Cadastro de materiais (compartilhado entre categorias e produtos). Consultar: equipe do catálogo; cadastrar/alterar: gerente e proprietário. */
@RestController
@RequestMapping("/api/v1/materiais")
@RequiredArgsConstructor
public class MaterialController {

  private final CatalogoConfigService service;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  public List<MaterialView> listar(@RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean incluirInativos) {
    return service.listarMateriais(q, incluirInativos);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public MaterialView criar(@RequestBody MaterialRequest req) {
    return service.criarMaterial(req.nome());
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public MaterialView atualizar(@PathVariable Long id, @RequestBody MaterialRequest req) {
    return service.atualizarMaterial(id, req.nome(), req.ativo());
  }
}
