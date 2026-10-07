package br.com.lojaspopular.web.estoque;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.estoque.EstoqueService.MovimentacaoView;
import br.com.lojaspopular.application.estoque.EstoqueService.PaginaMovimentacoes;
import br.com.lojaspopular.application.estoque.EstoqueService.Saldo;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AjusteEstoqueRequest;
import jakarta.validation.Valid;
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

  /** Histórico de movimentações do saldo físico (entradas, saídas, ajustes, devoluções). */
  @GetMapping("/movimentacoes")
  public PaginaMovimentacoes movimentacoes(@RequestParam(required = false) Long produtoId,
      @RequestParam(required = false) Long variacaoId, @RequestParam(required = false) Long pedidoId,
      @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "30") int tamanho) {
    return service.listarMovimentacoes(produtoId, variacaoId, pedidoId, pagina, tamanho);
  }

  /**
   * Contagem de inventário: informa o físico contado; o sistema ajusta a diferença com motivo obrigatório.
   * Repetir a mesma Idempotency-Key não duplica o ajuste.
   */
  @PostMapping("/ajustes")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public MovimentacaoView ajustar(@Valid @RequestBody AjusteEstoqueRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return service.ajustarContagem(req.produtoId(), req.variacaoId(), req.contado(), req.motivo(), chave);
  }
}
