package br.com.lojaspopular.web.estoque;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.estoque.EstoqueService.Saldo;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/estoque")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
@RequiredArgsConstructor
public class EstoqueController {

  private final EstoqueService service;

  /** Saldo físico, reservado e disponível por unidade de estoque (variação ou produto sem variação). */
  @GetMapping
  public List<Saldo> saldos(@RequestParam(required = false) Long produtoId,
      @RequestParam(required = false, defaultValue = "false") boolean apenasAlertas) {
    return service.listarSaldos().stream()
        .filter(s -> produtoId == null || produtoId.equals(s.produtoId()))
        .filter(s -> !apenasAlertas || s.alerta() != null)
        .toList();
  }
}
