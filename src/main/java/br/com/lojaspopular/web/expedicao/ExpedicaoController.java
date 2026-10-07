package br.com.lojaspopular.web.expedicao;

import java.time.LocalDate;
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

import br.com.lojaspopular.application.expedicao.ExpedicaoService;
import br.com.lojaspopular.application.posvenda.PosVendaService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AbrirOcorrenciaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AgendaItem;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AgendarEntregaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AgendarMontagemRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.MotivoRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.OcorrenciaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.ReagendarEntregaRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Saída, entrega/retirada, montagem e pós-venda de uma venda. Todas as respostas de escrita devolvem o
 * detalhe atualizado da venda (a mesma tela de acompanhamento). Escrita: gerente ou proprietário.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
@RequiredArgsConstructor
public class ExpedicaoController {

  private final ExpedicaoService expedicao;
  private final PosVendaService posVenda;
  private final VendaService vendas;

  // ---- entrega / retirada / saída

  @PostMapping("/vendas/{id}/entrega/agendar")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse agendarEntrega(@PathVariable Long id, @Valid @RequestBody AgendarEntregaRequest req) {
    expedicao.agendarEntrega(id, req.data(), req.periodo(), req.equipe(), req.observacao());
    return vendas.obter(id);
  }

  @PostMapping("/vendas/{id}/entrega/reagendar")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse reagendarEntrega(@PathVariable Long id, @Valid @RequestBody ReagendarEntregaRequest req) {
    expedicao.reagendarEntrega(id, req.data(), req.periodo(), req.equipe(), req.motivo());
    return vendas.obter(id);
  }

  @PostMapping("/vendas/{id}/saida")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse registrarSaida(@PathVariable Long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    expedicao.registrarSaida(id, chave);
    return vendas.obter(id);
  }

  @PostMapping("/vendas/{id}/entrega/tentativa-frustrada")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse tentativaFrustrada(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
    expedicao.registrarTentativaFrustrada(id, req.motivo());
    return vendas.obter(id);
  }

  @PostMapping(value = "/vendas/{id}/entrega/concluir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse concluirEntrega(@PathVariable Long id,
      @RequestParam(value = "recebedor", required = false) String recebedor,
      @RequestParam(value = "observacao", required = false) String observacao,
      @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
    expedicao.concluirEntrega(id, recebedor, observacao, arquivo);
    return vendas.obter(id);
  }

  // ---- montagem

  @PostMapping("/vendas/{id}/montagem/agendar")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse agendarMontagem(@PathVariable Long id, @Valid @RequestBody AgendarMontagemRequest req) {
    expedicao.agendarMontagem(id, req.data(), req.periodo(), req.responsavel(), req.observacao());
    return vendas.obter(id);
  }

  @PostMapping(value = "/vendas/{id}/montagem/concluir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse concluirMontagem(@PathVariable Long id,
      @RequestParam(value = "observacao", required = false) String observacao,
      @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
    expedicao.concluirMontagem(id, observacao, arquivo);
    return vendas.obter(id);
  }

  @PostMapping("/vendas/{id}/montagem/nao-necessaria")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse montagemNaoNecessaria(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
    expedicao.dispensarMontagem(id, req.motivo());
    return vendas.obter(id);
  }

  // ---- pós-venda (abertura a partir da venda)

  @PostMapping("/vendas/{id}/ocorrencias")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public OcorrenciaResponse abrirOcorrencia(@PathVariable Long id, @Valid @RequestBody AbrirOcorrenciaRequest req) {
    return posVenda.abrir(id, req);
  }

  // ---- agenda

  @GetMapping("/agenda")
  public List<AgendaItem> agenda(@RequestParam LocalDate de, @RequestParam LocalDate ate) {
    return expedicao.agenda(de, ate);
  }
}
