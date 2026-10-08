package br.com.lojaspopular.web.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.financeiro.CustoService;
import br.com.lojaspopular.application.financeiro.CustoService.CustoHistorico;
import br.com.lojaspopular.application.financeiro.CustoService.CustoLinha;
import br.com.lojaspopular.application.financeiro.CustoService.ItemSemCusto;
import br.com.lojaspopular.application.financeiro.RelatorioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Relatórios gerenciais (UC-23) e cadastro de custos. Consulta: gerente e proprietário; custos: só o proprietário grava. */
@RestController
@RequestMapping("/api/v1/financeiro")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
@RequiredArgsConstructor
public class RelatorioController {

  public record CustoRequest(@NotNull(message = "Informe o produto") Long produtoId, Long variacaoId,
      @NotNull(message = "Informe o custo") BigDecimal custo, LocalDate vigenteDesde, String motivo) {
  }

  public record CustoItemRequest(@NotNull(message = "Informe o custo") BigDecimal custo, String motivo) {
  }

  private final RelatorioService relatorios;
  private final CustoService custos;
  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final br.com.lojaspopular.application.auth.UsuarioAtual usuarioAtual;

  /** Permissões financeiras do usuário atual (a tela esconde o que não pode; o servidor sempre confere). */
  @GetMapping("/permissoes")
  public java.util.Map<String, Boolean> permissoes() {
    return permissoes.minhas(usuarioAtual.get());
  }

  @GetMapping("/relatorios/vendas")
  public RelatorioService.RelatorioVendas vendas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
      @RequestParam(required = false) String agrupar) {
    return relatorios.vendas(de, ate, agrupar);
  }

  @GetMapping("/relatorios/recebimentos")
  public RelatorioService.RelatorioRecebimentos recebimentos(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
    return relatorios.recebimentos(de, ate);
  }

  @GetMapping("/relatorios/contas-pendentes")
  public RelatorioService.RelatorioContas contasPendentes() {
    return relatorios.contasPendentes();
  }

  @GetMapping("/relatorios/estoque")
  public RelatorioService.RelatorioEstoque estoque() {
    return relatorios.estoque();
  }

  @GetMapping("/relatorios/entregas")
  public RelatorioService.RelatorioEntregas entregas(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
    return relatorios.entregas(de, ate);
  }

  @GetMapping("/relatorios/comissoes")
  public RelatorioService.RelatorioComissoes comissoes(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
    return relatorios.comissoes(de, ate);
  }

  @GetMapping("/relatorios/metas")
  public RelatorioService.RelatorioMetas metas(@RequestParam String mes) {
    return relatorios.metas(mes);
  }

  // ------------------------------------------------------------------ custos

  @GetMapping("/custos")
  public List<CustoLinha> custos() {
    return custos.listar();
  }

  @GetMapping("/custos/historico/{produtoId}")
  public List<CustoHistorico> historico(@PathVariable Long produtoId) {
    return custos.historico(produtoId);
  }

  @GetMapping("/custos/itens-sem-custo")
  public List<ItemSemCusto> itensSemCusto() {
    return custos.itensSemCusto();
  }

  @PostMapping("/custos")
  public CustoHistorico registrar(@Valid @RequestBody CustoRequest req) {
    return custos.registrar(req.produtoId(), req.variacaoId(), req.custo(), req.vigenteDesde(), req.motivo());
  }

  @PostMapping("/custos/itens/{itemId}")
  public ItemSemCusto informarItem(@PathVariable Long itemId, @Valid @RequestBody CustoItemRequest req) {
    return custos.informarCustoDoItem(itemId, req.custo(), req.motivo());
  }
}
