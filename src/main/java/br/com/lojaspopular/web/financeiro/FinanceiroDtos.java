package br.com.lojaspopular.web.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.lojaspopular.domain.financeiro.enums.AquisicaoComissao;
import br.com.lojaspopular.domain.financeiro.enums.CompetenciaReceita;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemConta;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.StatusFechamento;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebimento;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
import br.com.lojaspopular.domain.financeiro.enums.TipoComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoEventoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.user.Role;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Contratos da Etapa 3 (financeiro e gestão). Valores monetários sempre em BigDecimal com 2 casas. */
public final class FinanceiroDtos {

  private FinanceiroDtos() {
  }

  public enum TipoMovimentoCaixa { SUPRIMENTO, RETIRADA }

  // ================================================================== requests

  public record RegistrarRecebimentoRequest(
      @NotNull(message = "Informe o valor recebido") @Positive(message = "O valor deve ser maior que zero") BigDecimal valor,
      LocalDate dataPagamento,
      @Size(max = 100, message = "Referência: máximo de 100 caracteres") String referencia,
      @Size(max = 60, message = "Operadora: máximo de 60 caracteres") String operadora,
      @Size(max = 300, message = "Observação: máximo de 300 caracteres") String observacao,
      FormaPagamento forma, Integer parcelas, br.com.lojaspopular.domain.financeiro.enums.TipoCartao tipoCartao) {

    /** Forma, parcelas e tipo do cartão são opcionais: sem eles vale o que a venda já tem. */
    public RegistrarRecebimentoRequest(BigDecimal valor, LocalDate dataPagamento, String referencia, String operadora,
        String observacao) {
      this(valor, dataPagamento, referencia, operadora, observacao, null, null, null);
    }
  }

  public record AbrirCaixaRequest(
      @NotNull(message = "Informe o saldo inicial") @PositiveOrZero(message = "O saldo inicial não pode ser negativo") BigDecimal saldoInicial) {
  }

  public record MovimentoCaixaRequest(
      @NotNull(message = "Informe o tipo do movimento") TipoMovimentoCaixa tipo,
      @NotNull(message = "Informe o valor") @Positive(message = "O valor deve ser maior que zero") BigDecimal valor,
      @NotBlank(message = "Informe o motivo") @Size(max = 300, message = "Motivo: máximo de 300 caracteres") String motivo) {
  }

  public record FecharCaixaRequest(
      @NotNull(message = "Informe o valor contado") @PositiveOrZero(message = "O valor contado não pode ser negativo") BigDecimal saldoContado,
      @Size(max = 300, message = "Motivo: máximo de 300 caracteres") String motivoDiferenca) {
  }

  public record LiquidarRecebivelRequest(LocalDate dataLiquidacao, @Positive(message = "O valor liquidado deve ser maior que zero") BigDecimal valorLiquidado) {
  }

  public record TaxaCartaoRequest(
      @NotBlank(message = "Informe a operadora") @Size(max = 60) String operadora,
      @NotNull(message = "Informe as parcelas") @Min(value = 1, message = "Parcelas deve ser no mínimo 1") Integer parcelas,
      @NotNull(message = "Informe a taxa percentual") BigDecimal taxaPercentual,
      @NotNull(message = "Informe o prazo da 1ª parcela (dias)") @Min(0) Integer prazoPrimeiraParcelaDias,
      @NotNull(message = "Informe o intervalo entre parcelas (dias)") @Min(0) Integer intervaloDias,
      Boolean ativa) {
  }

  public record ContaRequest(
      @NotNull(message = "Informe se é conta a pagar ou a receber") TipoConta tipo,
      @NotBlank(message = "Informe a descrição") @Size(max = 200) String descricao,
      @NotBlank(message = "Informe a categoria") @Size(max = 60) String categoria,
      @NotNull(message = "Informe a competência (mês)") LocalDate competencia,
      @NotNull(message = "Informe o valor") @Positive(message = "O valor deve ser maior que zero") BigDecimal valor,
      @NotNull(message = "Informe o vencimento") LocalDate vencimento,
      @Size(max = 300) String observacao) {
  }

  public record BaixarContaRequest(@NotNull(message = "Informe se saiu/entrou pelo caixa ou pelo banco") ContaLivro meio,
      LocalDate data) {
  }

  public record SolicitarRestituicaoRequest(
      @NotNull(message = "Informe o valor") @Positive(message = "O valor deve ser maior que zero") BigDecimal valor,
      @NotBlank(message = "Informe o motivo") @Size(max = 300) String motivo) {
  }

  public record EfetivarRestituicaoRequest(LocalDate data) {
  }

  public record CobrarDiferencaRequest(@NotNull(message = "Informe o vencimento") LocalDate vencimento) {
  }

  public record GerarPagamentoComissaoRequest(@NotNull(message = "Informe o vendedor") Long vendedorId,
      @NotNull(message = "Informe o vencimento do pagamento") LocalDate vencimento) {
  }

  public record MetaRequest(@NotNull(message = "Informe o vendedor") Long vendedorId,
      @NotBlank(message = "Informe o mês (aaaa-mm)") String mes,
      @NotNull(message = "Informe o valor da meta") @Positive(message = "A meta deve ser maior que zero") BigDecimal valor) {
  }

  public record MesRequest(@NotBlank(message = "Informe o mês (aaaa-mm)") String mes) {
  }

  public record ReabrirFechamentoRequest(@NotBlank(message = "Informe o mês (aaaa-mm)") String mes,
      @NotBlank(message = "Informe a justificativa") @Size(max = 300, message = "Justificativa: máximo de 300 caracteres") String justificativa) {
  }

  /** Decisões financeiras da loja (todas nulas = pendentes). Somente o proprietário altera. */
  public record ConfigFinanceiraRequest(BigDecimal comissaoPercentual, AquisicaoComissao comissaoAquisicao,
      CompetenciaReceita competenciaReceita, java.util.Set<Role> perfisReabertura, java.util.Set<Role> perfisRestituicao,
      Boolean permiteRestituicao, Boolean permiteCobrancaDiferenca, Boolean metaDescontaDevolucoes,
      Boolean fechamentoExigeSemPendencias) {
  }

  // ================================================================== responses

  public record ConfigFinanceiraView(BigDecimal comissaoPercentual, AquisicaoComissao comissaoAquisicao,
      CompetenciaReceita competenciaReceita, java.util.Set<Role> perfisReabertura, java.util.Set<Role> perfisRestituicao,
      Boolean permiteRestituicao, Boolean permiteCobrancaDiferenca, Boolean metaDescontaDevolucoes,
      Boolean fechamentoExigeSemPendencias) {
  }

  public record RecebivelView(Long id, Long recebimentoId, Long pedidoId, String operadora, Integer parcela,
      Integer totalParcelas, BigDecimal valorBruto, BigDecimal taxaPercentual, BigDecimal valorTaxa,
      BigDecimal valorLiquido, LocalDate dataPrevista, StatusRecebivel status, LocalDate dataLiquidacao,
      BigDecimal valorLiquidado, BigDecimal diferencaLiquidacao, boolean vencido, boolean planoManual) {
  }

  public record RecebimentoView(Long id, Long pedidoId, FormaPagamento forma, BigDecimal valor, Integer parcelas,
      LocalDate dataPagamento, String referencia, String operadora, String observacao, StatusRecebimento status,
      String registradoPor, Instant criadoEm, Instant estornadoEm, String estornadoPor, String motivoEstorno,
      List<RecebivelView> recebiveis, br.com.lojaspopular.domain.financeiro.enums.TipoCartao tipoCartao, boolean planoManual) {
  }

  public record LancamentoView(Long id, ContaLivro conta, TipoLancamento tipo, BigDecimal valor, LocalDate dataEfetiva,
      OrigemLancamento origem, String descricao, Long sessaoCaixaId, Long pedidoId, Long estornaId, boolean estornado,
      String criadoPor, Instant criadoEm) {
  }

  public record SessaoCaixaView(Long id, StatusSessaoCaixa status, LocalDate dataReferencia, BigDecimal saldoInicial,
      BigDecimal entradas, BigDecimal saidas, BigDecimal saldoEsperado, BigDecimal saldoContado, BigDecimal diferenca,
      String motivoDiferenca, Instant abertaEm, String abertaPor, Instant fechadaEm, String fechadaPor,
      List<LancamentoView> movimentos) {
  }

  public record TaxaCartaoView(Long id, String operadora, Integer parcelas, BigDecimal taxaPercentual,
      Integer prazoPrimeiraParcelaDias, Integer intervaloDias, boolean ativa) {
  }

  public record ContaEventoView(Long id, TipoEventoConta tipo, String motivo, String usuario, Instant criadoEm) {
  }

  public record ContaView(Long id, TipoConta tipo, String descricao, String categoria, LocalDate competencia,
      BigDecimal valor, LocalDate vencimento, SituacaoConta situacao, OrigemConta origem, Long pedidoId, Long ocorrenciaId,
      String vendedor, String observacao, LocalDate pagoEm, ContaLivro meio, boolean vencida,
      List<ContaEventoView> eventos) {
  }

  public record RestituicaoView(Long id, Long pedidoId, Long ocorrenciaId, BigDecimal valor, FormaPagamento forma,
      StatusRestituicao status, String motivo, String solicitadaPor, Instant solicitadaEm, String autorizadaPor,
      Instant autorizadaEm, String efetivadaPor, Instant efetivadaEm, LocalDate dataEfetiva, String motivoCancelamento) {
  }

  public record ComissaoView(Long id, Long pedidoId, Long vendedorId, String vendedor, TipoComissao tipo,
      StatusComissao status, BigDecimal base, BigDecimal percentual, BigDecimal valor, LocalDate competencia,
      LocalDate adquiridaEm, Long reverteId, Long contaId, String motivo, Instant criadaEm) {
  }

  public record ComissaoResumo(boolean percentualDefinido, boolean aquisicaoDefinida, List<String> avisos,
      BigDecimal previstas, BigDecimal devidas, BigDecimal emConta, BigDecimal pagas, BigDecimal reversoesPendentes,
      BigDecimal saldoAPagar, List<ComissaoView> itens) {
  }

  public record MetaView(Long vendedorId, String vendedor, String mes, BigDecimal meta, BigDecimal vendido,
      BigDecimal restituidoNoMes, BigDecimal atingimentoPercentual, String politicaDevolucoes, boolean provisorio,
      String observacao) {
  }

  public record Pendencia(String codigo, String descricao, long quantidade, boolean bloqueante) {
  }

  public record Resultado(BigDecimal receitaBruta, BigDecimal restituicoes, BigDecimal taxasCartao,
      BigDecimal despesas, BigDecimal comissoes, BigDecimal comissoesPrevistas, BigDecimal custosConhecidos,
      long itensSemCusto, BigDecimal resultadoParcial, BigDecimal lucroApurado, boolean definitivo,
      String criterioCompetencia, List<String> faltantes, BigDecimal taxasCartaoLiquidadas, BigDecimal taxasCartaoEmAberto,
      List<String> criterios) {
  }

  public record CaixaFechamento(BigDecimal entradasCaixa, BigDecimal saidasCaixa, BigDecimal saldoCaixa,
      BigDecimal entradasBanco, BigDecimal saidasBanco, BigDecimal saldoBanco, Map<String, BigDecimal> porOrigem) {
  }

  public record FechamentoView(String mes, boolean fechado, Integer versao, StatusFechamento ultimoStatus,
      CaixaFechamento caixa, Resultado resultado, List<Pendencia> pendencias, boolean podeAprovar,
      List<String> bloqueiosAprovacao, boolean podeReabrir, List<String> bloqueiosReabertura,
      List<VersaoFechamento> versoes) {
  }

  public record VersaoFechamento(Integer versao, StatusFechamento status, Instant aprovadoEm, String aprovadoPor,
      Instant reabertoEm, String reabertoPor, String justificativaReabertura) {
  }

  /** Bloco financeiro do detalhe da venda. */
  public record PagamentoPedido(FormaPagamento forma, Integer parcelas, BigDecimal total, BigDecimal recebido,
      BigDecimal saldo, BigDecimal restituido, List<RecebimentoView> recebimentos, List<RestituicaoView> restituicoes,
      List<LancamentoView> lancamentos, Map<String, String> bloqueios, boolean podeRegistrar, boolean podeEstornar) {
  }
}
