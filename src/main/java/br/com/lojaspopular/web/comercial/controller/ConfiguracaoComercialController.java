package br.com.lojaspopular.web.comercial.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService.Pendencia;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.model.CondicaoPagamento;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Configuração comercial: preços por condição, limite de desconto e perfis de cancelamento. */
@RestController
@RequestMapping("/api/v1/config/comercial")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
@RequiredArgsConstructor
public class ConfiguracaoComercialController {

  public record ConfiguracaoRequest(BigDecimal limiteDescontoPercentual, Arredondamento arredondamento,
      Set<Role> perfisCancelamento) {
  }

  public record CondicaoRequest(
      @NotNull(message = "Forma de pagamento é obrigatória") FormaPagamento forma,
      @NotNull(message = "Parcelas é obrigatório") Integer parcelas,
      @NotNull(message = "Ajuste percentual é obrigatório") BigDecimal ajustePercentual,
      Boolean ativa) {
  }

  public record CondicaoAtualizarRequest(
      @NotNull(message = "Ajuste percentual é obrigatório") BigDecimal ajustePercentual,
      @NotNull(message = "Informe se a condição está ativa") Boolean ativa) {
  }

  public record CondicaoResponse(Long id, FormaPagamento forma, Integer parcelas, BigDecimal ajustePercentual,
      boolean ativa) {
  }

  public record ConfiguracaoResponse(BigDecimal limiteDescontoPercentual, Arredondamento arredondamento,
      Set<Role> perfisCancelamento, List<Pendencia> pendencias, List<CondicaoResponse> condicoes) {
  }

  private final ConfiguracaoComercialService service;

  @GetMapping
  public ConfiguracaoResponse obter() {
    return resposta();
  }

  @PutMapping
  public ConfiguracaoResponse atualizar(@RequestBody ConfiguracaoRequest req) {
    service.atualizar(req.limiteDescontoPercentual(), req.arredondamento(), req.perfisCancelamento());
    return resposta();
  }

  @PostMapping("/condicoes")
  public CondicaoResponse criarCondicao(@Valid @RequestBody CondicaoRequest req) {
    return toResponse(service.criarCondicao(req.forma(), req.parcelas(), req.ajustePercentual(),
        req.ativa() == null || req.ativa()));
  }

  @PutMapping("/condicoes/{id}")
  public CondicaoResponse atualizarCondicao(@PathVariable Long id, @Valid @RequestBody CondicaoAtualizarRequest req) {
    return toResponse(service.atualizarCondicao(id, req.ajustePercentual(), req.ativa()));
  }

  private ConfiguracaoResponse resposta() {
    var cfg = service.obter();
    return new ConfiguracaoResponse(cfg.getLimiteDescontoPercentual(), cfg.getArredondamento(),
        service.perfisCancelamento(), service.pendencias(),
        service.listarCondicoes(false).stream().map(this::toResponse).toList());
  }

  private CondicaoResponse toResponse(CondicaoPagamento c) {
    return new CondicaoResponse(c.getId(), c.getForma(), c.getParcelas(), c.getAjustePercentual(), c.isAtiva());
  }
}
