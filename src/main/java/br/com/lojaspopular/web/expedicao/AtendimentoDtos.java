package br.com.lojaspopular.web.expedicao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.lojaspopular.domain.encomenda.enums.StatusEncomenda;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.expedicao.enums.StatusEntregaRegistro;
import br.com.lojaspopular.domain.expedicao.enums.StatusMontagemRegistro;
import br.com.lojaspopular.domain.expedicao.enums.TipoEventoEntrega;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.posvenda.enums.CondicaoFisica;
import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da Etapa 2: encomendas, entrega, montagem, agenda e pós-venda. */
public final class AtendimentoDtos {

  private AtendimentoDtos() {
  }

  // ---------------------------------------------------------------- requests

  public record AgendarEntregaRequest(
      @NotNull(message = "Informe a data") LocalDate data,
      @NotNull(message = "Informe o período") PeriodoAgenda periodo,
      @Size(max = 150, message = "Equipe: máximo de 150 caracteres") String equipe,
      @Size(max = 300, message = "Observação: máximo de 300 caracteres") String observacao) {
  }

  public record ReagendarEntregaRequest(
      @NotNull(message = "Informe a data") LocalDate data,
      @NotNull(message = "Informe o período") PeriodoAgenda periodo,
      @Size(max = 150, message = "Equipe: máximo de 150 caracteres") String equipe,
      @NotBlank(message = "Informe o motivo do reagendamento") @Size(max = 300) String motivo) {
  }

  public record MotivoRequest(
      @NotBlank(message = "Informe o motivo") @Size(max = 300, message = "Motivo: máximo de 300 caracteres") String motivo) {
  }

  public record AgendarMontagemRequest(
      @NotNull(message = "Informe a data") LocalDate data,
      @NotNull(message = "Informe o período") PeriodoAgenda periodo,
      @Size(max = 150, message = "Responsável: máximo de 150 caracteres") String responsavel,
      @Size(max = 300, message = "Observação: máximo de 300 caracteres") String observacao) {
  }

  public record AtualizarEncomendaRequest(
      @Size(max = 150, message = "Fornecedor: máximo de 150 caracteres") String fornecedor,
      @Size(max = 100, message = "Referência: máximo de 100 caracteres") String referenciaFornecedor,
      LocalDate previsaoChegada,
      @Size(max = 300, message = "Observação: máximo de 300 caracteres") String observacao) {
  }

  public record ReceberEncomendaRequest(
      @NotNull(message = "Informe a quantidade recebida") @Min(value = 1, message = "Quantidade deve ser maior que zero") Integer quantidade) {
  }

  public record AjusteEstoqueRequest(
      @NotNull(message = "Produto é obrigatório") Long produtoId,
      Long variacaoId,
      @NotNull(message = "Informe a quantidade contada") @Min(value = 0, message = "A contagem não pode ser negativa") Integer contado,
      @NotBlank(message = "Informe o motivo do ajuste") @Size(max = 300) String motivo) {
  }

  public record TrocaRequest(
      @NotNull(message = "Informe o produto da troca") Long produtoId,
      Long variacaoId,
      @NotNull(message = "Informe a quantidade do produto de troca") @Min(value = 1, message = "Quantidade deve ser maior que zero") Integer quantidade) {
  }

  public record AbrirOcorrenciaRequest(
      @NotNull(message = "Informe o tipo da ocorrência") TipoOcorrencia tipo,
      @NotBlank(message = "Descreva a ocorrência") @Size(max = 500, message = "Descrição: máximo de 500 caracteres") String descricao,
      Long itemId,
      @Min(value = 1, message = "Quantidade deve ser maior que zero") Integer quantidade,
      @Valid TrocaRequest troca) {
  }

  public record ReceberDevolucaoRequest(
      @NotNull(message = "Informe a condição física") CondicaoFisica condicao,
      @Size(max = 300, message = "Avaliação: máximo de 300 caracteres") String avaliacao) {
  }

  public record ResolverOcorrenciaRequest(
      @NotBlank(message = "Descreva a solução") @Size(max = 500, message = "Solução: máximo de 500 caracteres") String solucao) {
  }

  // ---------------------------------------------------------------- responses

  /** Um recebimento (inclusive parcial) de mercadoria do fornecedor. */
  public record RecebimentoEncomenda(Long id, Integer quantidade, Instant recebidoEm, String usuario, String observacao) {
  }

  /**
   * quantidadeRecebida é o ACUMULADO já recebido do fornecedor; quantidadeReservada é o que já está reservado ao cliente
   * (a saída ao cliente só acontece com o pedido completo: o recebimento parcial do fornecedor não gera entrega parcial).
   */
  public record EncomendaResponse(Long id, Long pedidoId, String cliente, Long itemId, String descricao,
      Integer quantidade, StatusEncomenda status, String fornecedor, String referenciaFornecedor,
      LocalDate previsaoChegada, boolean atrasada, String observacao, Integer quantidadeRecebida, Instant recebidaEm,
      StatusReserva reserva, String prazoPadrao, Integer quantidadeReservada, Integer quantidadeFaltante,
      List<RecebimentoEncomenda> recebimentos) {
  }

  public record EntregaEventoResponse(Long id, TipoEventoEntrega tipo, LocalDate dataPrevista, PeriodoAgenda periodo,
      String equipe, String motivo, String arquivo, String usuario, Instant criadoEm) {
  }

  public record EntregaResponse(Long id, TipoEntrega tipo, StatusEntregaRegistro status, LocalDate dataPrevista,
      PeriodoAgenda periodo, String equipe, String observacao, Instant saidaEm, Instant concluidaEm,
      String recebedorNome, String comprovanteArquivo, String observacaoConclusao, boolean baixaRealizada,
      int tentativasFrustradas, String frete, List<EntregaEventoResponse> eventos) {
  }

  public record MontagemResponse(Long id, StatusMontagemRegistro status, LocalDate dataPrevista, PeriodoAgenda periodo,
      String responsavel, String observacao, Instant concluidaEm, String evidenciaArquivo, String motivoDispensa,
      String cobranca) {
  }

  public record EvidenciaResponse(Long id, String arquivo, String descricao, String enviadoPor, Instant criadoEm) {
  }

  public record OcorrenciaResponse(Long id, Long pedidoId, String cliente, TipoOcorrencia tipo,
      StatusOcorrencia status, String descricao, Long itemId, String item, Integer quantidade, String trocaItem,
      Integer trocaQuantidade, BigDecimal diferencaCalculada, CondicaoFisica condicaoFisica, String avaliacao,
      Instant devolucaoRecebidaEm, boolean estoqueReposto, String solucao, Instant resolvidaEm,
      String motivoCancelamento, String abertaPor, Instant criadaEm, List<EvidenciaResponse> evidencias,
      Map<String, String> bloqueios, List<br.com.lojaspopular.web.financeiro.FinanceiroDtos.RestituicaoView> restituicoes,
      br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaView contaDiferenca, BigDecimal valorRestituivel,
      boolean podeSolicitarRestituicao, boolean podeCobrarDiferenca) {
  }

  public record AgendaItem(String tipo, Long pedidoId, String cliente, LocalDate data, PeriodoAgenda periodo,
      String responsavel, String status, String endereco, String entregaTipo) {
  }
}
