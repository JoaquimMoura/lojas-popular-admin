package br.com.lojaspopular.web.venda.controller;

import java.math.BigDecimal;
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

import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService.Pendencia;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.web.venda.dto.VendaDtos.CancelarRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.DecisaoDescontoRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.Pagina;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaResumoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/vendas")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
@RequiredArgsConstructor
public class VendaController {

  public record CondicaoVenda(FormaPagamento forma, Integer parcelas, BigDecimal ajustePercentual) {
  }

  /** Dados de configuração necessários para vender, visíveis ao vendedor. */
  public record ConfiguracaoVenda(List<Pendencia> pendencias, List<CondicaoVenda> condicoes,
      BigDecimal limiteDescontoPercentual, Arredondamento arredondamento) {
  }

  public record VincularClienteRequest(@jakarta.validation.constraints.NotNull(message = "Informe o cliente") Long clienteId,
      @jakarta.validation.constraints.NotBlank(message = "Informe a justificativa")
      @jakarta.validation.constraints.Size(max = 300) String justificativa) {
  }

  private final VendaService service;
  private final br.com.lojaspopular.application.venda.VendaClienteService clienteVenda;
  private final ConfiguracaoComercialService config;

  @GetMapping("/configuracao")
  public ConfiguracaoVenda configuracao() {
    var cfg = config.obter();
    var pendencias = config.pendencias().stream().filter(p -> !"FINANCEIRO".equals(p.area())).toList();
    return new ConfiguracaoVenda(pendencias,
        config.listarCondicoes(true).stream()
            .map(c -> new CondicaoVenda(c.getForma(), c.getParcelas(), c.getAjustePercentual())).toList(),
        cfg.getLimiteDescontoPercentual(), cfg.getArredondamento());
  }

  @GetMapping
  public Pagina<VendaResumoResponse> listar(@RequestParam(required = false) StatusComercial status,
      @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(status, q, pagina, tamanho);
  }

  @GetMapping("/{id}")
  public VendaDetalheResponse obter(@PathVariable Long id) {
    return service.obter(id);
  }

  @PostMapping
  public VendaDetalheResponse registrar(@Valid @RequestBody VendaRequest req) {
    return service.registrar(req);
  }

  @PutMapping("/{id}")
  public VendaDetalheResponse atualizar(@PathVariable Long id, @Valid @RequestBody VendaRequest req) {
    return service.atualizar(id, req);
  }

  @PostMapping("/{id}/recalcular")
  public VendaDetalheResponse recalcular(@PathVariable Long id) {
    return service.recalcular(id);
  }

  @PostMapping("/{id}/confirmar")
  public VendaDetalheResponse confirmar(@PathVariable Long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return service.confirmar(id, chave);
  }

  @PostMapping("/{id}/cancelar")
  public VendaDetalheResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarRequest req) {
    return service.cancelar(id, req.motivo());
  }

  /** Vincula uma venda antiga (sem cliente) a um cliente: só o proprietário, com justificativa. Sem efeito financeiro. */
  @PostMapping("/{id}/vincular-cliente")
  public VendaDetalheResponse vincularCliente(@PathVariable Long id, @Valid @RequestBody VincularClienteRequest req) {
    clienteVenda.vincular(id, req.clienteId(), req.justificativa());
    return service.obter(id);
  }

  /** Troca o cliente de uma venda confirmada: exige a permissão específica (D13) e justificativa. */
  @PostMapping("/{id}/trocar-cliente")
  public VendaDetalheResponse trocarCliente(@PathVariable Long id, @Valid @RequestBody VincularClienteRequest req) {
    clienteVenda.trocar(id, req.clienteId(), req.justificativa());
    return service.obter(id);
  }

  @PostMapping("/{id}/desconto/aprovar")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse aprovarDesconto(@PathVariable Long id,
      @RequestBody(required = false) DecisaoDescontoRequest req) {
    return service.decidirDesconto(id, true, req == null ? null : req.motivo());
  }

  @PostMapping("/{id}/desconto/rejeitar")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public VendaDetalheResponse rejeitarDesconto(@PathVariable Long id,
      @RequestBody(required = false) DecisaoDescontoRequest req) {
    return service.decidirDesconto(id, false, req == null ? null : req.motivo());
  }
}
