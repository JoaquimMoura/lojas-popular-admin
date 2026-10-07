package br.com.lojaspopular.web.expedicao;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.application.posvenda.PosVendaService;
import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.MotivoRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.OcorrenciaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.ReceberDevolucaoRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.ResolverOcorrenciaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ocorrências de pós-venda: assistência, troca e devolução. Gerente ou proprietário. */
@RestController
@RequestMapping("/api/v1/ocorrencias")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
@RequiredArgsConstructor
public class PosVendaController {

  private final PosVendaService service;

  @GetMapping
  public List<OcorrenciaResponse> listar(@RequestParam(required = false) StatusOcorrencia status,
      @RequestParam(required = false) TipoOcorrencia tipo) {
    return service.listar(status, tipo);
  }

  @GetMapping("/{id}")
  public OcorrenciaResponse obter(@PathVariable Long id) {
    return service.obter(id);
  }

  @PostMapping(value = "/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public OcorrenciaResponse anexarEvidencia(@PathVariable Long id,
      @RequestPart("arquivo") MultipartFile arquivo,
      @RequestParam(value = "descricao", required = false) String descricao) {
    return service.anexarEvidencia(id, arquivo, descricao);
  }

  @PostMapping("/{id}/receber-devolucao")
  public OcorrenciaResponse receberDevolucao(@PathVariable Long id, @Valid @RequestBody ReceberDevolucaoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return service.receberDevolucao(id, req.condicao(), req.avaliacao(), chave);
  }

  @PostMapping("/{id}/resolver")
  public OcorrenciaResponse resolver(@PathVariable Long id, @Valid @RequestBody ResolverOcorrenciaRequest req) {
    return service.resolver(id, req.solucao());
  }

  @PostMapping("/{id}/cancelar")
  public OcorrenciaResponse cancelar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
    return service.cancelar(id, req.motivo());
  }
}
