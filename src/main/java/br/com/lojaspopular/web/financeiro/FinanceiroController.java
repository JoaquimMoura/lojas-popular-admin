package br.com.lojaspopular.web.financeiro;

import java.time.LocalDate;
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

import br.com.lojaspopular.application.financeiro.CaixaService;
import br.com.lojaspopular.application.financeiro.CartaoService;
import br.com.lojaspopular.application.financeiro.ComissaoService;
import br.com.lojaspopular.application.financeiro.ContaFinanceiraService;
import br.com.lojaspopular.application.financeiro.FechamentoService;
import br.com.lojaspopular.application.financeiro.FinanceiroMapper;
import br.com.lojaspopular.application.financeiro.MetaService;
import br.com.lojaspopular.application.financeiro.RecebimentoService;
import br.com.lojaspopular.application.financeiro.RestituicaoService;
import br.com.lojaspopular.application.venda.VendaService;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.MotivoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.AbrirCaixaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.BaixarContaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.CobrarDiferencaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoResumo;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.EfetivarRestituicaoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.FecharCaixaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.FechamentoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.GerarPagamentoComissaoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.LancamentoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.LiquidarRecebivelRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.MesRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.MetaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.MetaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.MovimentoCaixaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ReabrirFechamentoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RecebivelView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RegistrarRecebimentoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RestituicaoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.SessaoCaixaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.SolicitarRestituicaoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoView;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Financeiro e gestão (Etapa 3). Operação financeira: gerente e proprietário (quem é o "usuário financeiro
 * autorizado" não foi definido; adotados esses dois perfis). Comissões, aprovação e reabertura têm regras próprias
 * nos serviços (proprietário; perfis de D07). O vendedor só lê a própria comissão e a própria meta.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
@RequiredArgsConstructor
public class FinanceiroController {

  public record CaixaAtual(boolean aberta, SessaoCaixaView sessao) {
  }

  private final RecebimentoService recebimentos;
  private final CaixaService caixa;
  private final CartaoService cartao;
  private final ContaFinanceiraService contas;
  private final RestituicaoService restituicoes;
  private final ComissaoService comissoes;
  private final MetaService metas;
  private final FechamentoService fechamento;
  private final VendaService vendas;
  private final LancamentoFinanceiroRepository lancamentos;
  private final FinanceiroMapper mapper;
  private final br.com.lojaspopular.application.financeiro.Relogio relogio;

  // ------------------------------------------------------------------ recebimentos (pagamento do cliente)

  @PostMapping("/vendas/{id}/recebimentos")
  public VendaDetalheResponse registrarRecebimento(@PathVariable Long id, @Valid @RequestBody RegistrarRecebimentoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    recebimentos.registrar(id, req, chave);
    return vendas.obter(id);
  }

  @PostMapping("/recebimentos/{id}/estornar")
  public VendaDetalheResponse estornarRecebimento(@PathVariable Long id, @Valid @RequestBody MotivoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    var r = recebimentos.estornar(id, req.motivo(), chave);
    return vendas.obter(r.pedidoId());
  }

  // ------------------------------------------------------------------ caixa físico

  @GetMapping("/financeiro/caixa/atual")
  public CaixaAtual caixaAtual() {
    var s = caixa.atual();
    return new CaixaAtual(s != null, s);
  }

  @PostMapping("/financeiro/caixa/abrir")
  public SessaoCaixaView abrirCaixa(@Valid @RequestBody AbrirCaixaRequest req) {
    return caixa.abrir(req.saldoInicial());
  }

  @PostMapping("/financeiro/caixa/movimentos")
  public SessaoCaixaView movimentarCaixa(@Valid @RequestBody MovimentoCaixaRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return caixa.movimentar(req.tipo(), req.valor(), req.motivo(), chave);
  }

  @PostMapping("/financeiro/caixa/fechar")
  public SessaoCaixaView fecharCaixa(@Valid @RequestBody FecharCaixaRequest req) {
    return caixa.fechar(req.saldoContado(), req.motivoDiferenca());
  }

  @GetMapping("/financeiro/caixa/sessoes")
  public List<SessaoCaixaView> sessoes(@RequestParam(defaultValue = "30") int limite) {
    return caixa.historico(limite);
  }

  @GetMapping("/financeiro/caixa/sessoes/{id}")
  public SessaoCaixaView sessao(@PathVariable Long id) {
    return caixa.obter(id);
  }

  /** Livro de lançamentos efetivos (caixa físico e banco) de um período. */
  @GetMapping("/financeiro/lancamentos")
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public List<LancamentoView> lancamentos(@RequestParam(required = false) LocalDate de,
      @RequestParam(required = false) LocalDate ate, @RequestParam(required = false) ContaLivro conta) {
    LocalDate hoje = relogio.hoje();
    LocalDate inicio = de == null ? hoje.withDayOfMonth(1) : de;
    LocalDate fim = ate == null ? hoje : ate;
    return lancamentos.periodo(inicio, fim, conta == null ? "" : conta.name(), conta).stream().map(mapper::view).toList();
  }

  // ------------------------------------------------------------------ cartão

  @GetMapping("/financeiro/taxas-cartao")
  public List<TaxaCartaoView> taxas() {
    return cartao.listarTaxas();
  }

  @PostMapping("/financeiro/taxas-cartao")
  public TaxaCartaoView criarTaxa(@Valid @RequestBody TaxaCartaoRequest req) {
    return cartao.salvarTaxa(null, req);
  }

  @PutMapping("/financeiro/taxas-cartao/{id}")
  public TaxaCartaoView atualizarTaxa(@PathVariable Long id, @Valid @RequestBody TaxaCartaoRequest req) {
    return cartao.salvarTaxa(id, req);
  }

  @GetMapping("/financeiro/recebiveis")
  public List<RecebivelView> recebiveis(@RequestParam(required = false) StatusRecebivel status,
      @RequestParam(required = false) String operadora, @RequestParam(required = false) LocalDate de,
      @RequestParam(required = false) LocalDate ate) {
    return cartao.listar(status, operadora, de, ate);
  }

  @PostMapping("/financeiro/recebiveis/{id}/liquidar")
  public RecebivelView liquidar(@PathVariable Long id, @Valid @RequestBody(required = false) LiquidarRecebivelRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return cartao.liquidar(id, req == null ? null : req.dataLiquidacao(), req == null ? null : req.valorLiquidado(), chave);
  }

  @PostMapping("/financeiro/recebiveis/{id}/estornar-liquidacao")
  public RecebivelView estornarLiquidacao(@PathVariable Long id, @Valid @RequestBody MotivoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return cartao.estornarLiquidacao(id, req.motivo(), chave);
  }

  // ------------------------------------------------------------------ contas a pagar e a receber

  @GetMapping("/financeiro/contas")
  public List<ContaView> contas(@RequestParam(required = false) TipoConta tipo,
      @RequestParam(required = false) SituacaoConta situacao, @RequestParam(required = false) LocalDate de,
      @RequestParam(required = false) LocalDate ate) {
    return contas.listar(tipo, situacao, de, ate);
  }

  @GetMapping("/financeiro/contas/{id}")
  public ContaView conta(@PathVariable Long id) {
    return contas.obter(id);
  }

  @PostMapping("/financeiro/contas")
  public ContaView criarConta(@Valid @RequestBody ContaRequest req) {
    return contas.criar(req);
  }

  @PutMapping("/financeiro/contas/{id}")
  public ContaView atualizarConta(@PathVariable Long id, @Valid @RequestBody ContaRequest req) {
    return contas.atualizar(id, req);
  }

  @PostMapping("/financeiro/contas/{id}/baixar")
  public ContaView baixarConta(@PathVariable Long id, @Valid @RequestBody BaixarContaRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return contas.baixar(id, req.meio(), req.data(), chave);
  }

  @PostMapping("/financeiro/contas/{id}/estornar")
  public ContaView estornarConta(@PathVariable Long id, @Valid @RequestBody MotivoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return contas.estornar(id, req.motivo(), chave);
  }

  @PostMapping("/financeiro/contas/{id}/cancelar")
  public ContaView cancelarConta(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
    return contas.cancelar(id, req.motivo());
  }

  // ------------------------------------------------------------------ restituições (devolução FINANCEIRA)

  @GetMapping("/financeiro/restituicoes")
  public List<RestituicaoView> restituicoes(@RequestParam(required = false) StatusRestituicao status) {
    return restituicoes.listar(status);
  }

  @PostMapping("/ocorrencias/{id}/restituicoes")
  public RestituicaoView solicitarRestituicao(@PathVariable Long id, @Valid @RequestBody SolicitarRestituicaoRequest req) {
    return restituicoes.solicitar(id, req.valor(), req.motivo());
  }

  @PostMapping("/ocorrencias/{id}/cobrar-diferenca")
  public ContaView cobrarDiferenca(@PathVariable Long id, @Valid @RequestBody CobrarDiferencaRequest req) {
    return restituicoes.cobrarDiferenca(id, req.vencimento());
  }

  @PostMapping("/restituicoes/{id}/autorizar")
  public RestituicaoView autorizarRestituicao(@PathVariable Long id) {
    return restituicoes.autorizar(id);
  }

  @PostMapping("/restituicoes/{id}/efetivar")
  public RestituicaoView efetivarRestituicao(@PathVariable Long id,
      @Valid @RequestBody(required = false) EfetivarRestituicaoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
    return restituicoes.efetivar(id, req == null ? null : req.data(), chave);
  }

  @PostMapping("/restituicoes/{id}/cancelar")
  public RestituicaoView cancelarRestituicao(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
    return restituicoes.cancelar(id, req.motivo());
  }

  // ------------------------------------------------------------------ comissões e metas

  @GetMapping("/financeiro/comissoes")
  public ComissaoResumo comissoes(@RequestParam(required = false) Long vendedorId,
      @RequestParam(required = false) StatusComissao status) {
    return comissoes.listar(vendedorId, status);
  }

  /** O vendedor lê apenas a própria comissão. */
  @GetMapping("/financeiro/comissoes/minhas")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  public ComissaoResumo minhasComissoes() {
    return comissoes.minhas();
  }

  @PostMapping("/financeiro/comissoes/gerar-previsoes")
  @PreAuthorize("hasRole('ADMIN')")
  public ComissaoResumo gerarPrevisoes() {
    return comissoes.gerarPrevisoes();
  }

  @PostMapping("/financeiro/comissoes/pagamento")
  @PreAuthorize("hasRole('ADMIN')")
  public ContaView gerarPagamentoComissao(@Valid @RequestBody GerarPagamentoComissaoRequest req) {
    return comissoes.gerarPagamento(req.vendedorId(), req.vencimento());
  }

  @GetMapping("/financeiro/metas")
  public List<MetaView> metas(@RequestParam String mes) {
    return metas.listar(mes);
  }

  @GetMapping("/financeiro/metas/minha")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  public MetaView minhaMeta(@RequestParam String mes) {
    return metas.minha(mes);
  }

  @PutMapping("/financeiro/metas")
  public MetaView definirMeta(@Valid @RequestBody MetaRequest req) {
    return metas.definir(req.vendedorId(), req.mes(), req.valor());
  }

  // ------------------------------------------------------------------ fechamento mensal

  @GetMapping("/financeiro/fechamento/previa")
  public FechamentoView previa(@RequestParam String mes) {
    return fechamento.previa(mes);
  }

  @PostMapping("/financeiro/fechamento/aprovar")
  public FechamentoView aprovar(@Valid @RequestBody MesRequest req) {
    return fechamento.aprovar(req.mes());
  }

  @PostMapping("/financeiro/fechamento/reabrir")
  public FechamentoView reabrir(@Valid @RequestBody ReabrirFechamentoRequest req) {
    return fechamento.reabrir(req.mes(), req.justificativa());
  }
}
