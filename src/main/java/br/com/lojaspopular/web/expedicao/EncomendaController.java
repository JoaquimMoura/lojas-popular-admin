package br.com.lojaspopular.web.expedicao;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.encomenda.EncomendaService;
import br.com.lojaspopular.domain.encomenda.enums.StatusEncomenda;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AtualizarEncomendaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EncomendaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.ReceberEncomendaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Acompanhamento de encomendas (a compra é feita fora do sistema; nada é enviado ao fornecedor). */
@RestController
@RequestMapping("/api/v1/encomendas")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
@RequiredArgsConstructor
public class EncomendaController {

  private final EncomendaService service;

  @GetMapping
  public List<EncomendaResponse> listar(@RequestParam(required = false) StatusEncomenda status) {
    return service.listar(status);
  }

  @PutMapping("/{id}")
  public EncomendaResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarEncomendaRequest req) {
    return service.atualizar(id, req);
  }

  @PostMapping("/{id}/receber")
  public EncomendaResponse receber(@PathVariable Long id, @Valid @RequestBody ReceberEncomendaRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return service.receber(id, req.quantidade(), chave);
  }
}
