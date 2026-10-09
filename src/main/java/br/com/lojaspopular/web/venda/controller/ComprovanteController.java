package br.com.lojaspopular.web.venda.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.venda.ComprovanteService;
import br.com.lojaspopular.application.venda.ComprovanteService.Comprovante;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Comprovante de compra (pedido de venda, sem valor fiscal). Autenticado e com o mesmo escopo do pedido: sem URL pública. */
@RestController
@RequestMapping("/api/v1/vendas/{id}")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
@RequiredArgsConstructor
public class ComprovanteController {

  public record ObservacaoClienteRequest(@Size(max = 500, message = "Máximo de 500 caracteres") String texto) {
  }

  private final ComprovanteService service;

  @GetMapping("/comprovante")
  public Comprovante comprovante(@PathVariable Long id) {
    return service.emitir(id);
  }

  @PutMapping("/observacao-cliente")
  public ObservacaoClienteRequest observacaoCliente(@PathVariable Long id, @RequestBody ObservacaoClienteRequest req) {
    return new ObservacaoClienteRequest(service.definirObservacaoCliente(id, req.texto()));
  }
}
