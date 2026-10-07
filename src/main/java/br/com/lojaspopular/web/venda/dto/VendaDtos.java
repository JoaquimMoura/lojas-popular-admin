package br.com.lojaspopular.web.venda.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.com.lojaspopular.application.estoque.EstoqueService.MovimentacaoView;
import br.com.lojaspopular.domain.comercial.enums.StatusSolicitacaoDesconto;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusMontagem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EncomendaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EntregaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.MontagemResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.OcorrenciaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class VendaDtos {

  private VendaDtos() {
  }

  // ---- requests ----

  public record ItemRequest(
      @NotNull(message = "Produto é obrigatório") Long produtoId,
      Long variacaoId,
      @NotNull(message = "Quantidade é obrigatória") @Positive(message = "Quantidade deve ser maior que zero") Integer quantidade,
      @NotNull(message = "Modalidade do item é obrigatória") ModalidadeItem modalidade) {
  }

  public record VendaRequest(
      @NotNull(message = "Cliente é obrigatório") Long clienteId,
      Long vendedorId,
      @NotNull(message = "Canal é obrigatório") CanalVenda canal,
      @NotNull(message = "Tipo de entrega é obrigatório") TipoEntrega tipoEntrega,
      Long enderecoId,
      @NotNull(message = "Forma de pagamento é obrigatória") FormaPagamento formaPagamento,
      @NotNull(message = "Número de parcelas é obrigatório") @Min(value = 1, message = "Parcelas deve ser no mínimo 1") Integer parcelas,
      @PositiveOrZero(message = "Desconto não pode ser negativo") BigDecimal desconto,
      @Size(max = 300, message = "Justificativa do desconto: máximo de 300 caracteres") String justificativaDesconto,
      @Size(max = 500, message = "Observação: máximo de 500 caracteres") String observacao,
      @Size(max = 80, message = "Chave de idempotência: máximo de 80 caracteres") String chaveIdempotencia,
      @NotEmpty(message = "A venda precisa de ao menos um item") @Valid List<ItemRequest> itens) {
  }

  public record CancelarRequest(
      @NotBlank(message = "Informe o motivo do cancelamento") @Size(max = 300, message = "Motivo: máximo de 300 caracteres") String motivo) {
  }

  public record DecisaoDescontoRequest(
      @Size(max = 300, message = "Motivo: máximo de 300 caracteres") String motivo) {
  }

  // ---- responses ----

  public record PessoaResumo(Long id, String nome, String telefone) {
  }

  public record EnderecoEntrega(String cep, String logradouro, String numero, String complemento, String bairro,
      String cidade, String uf) {
  }

  public record ItemResponse(Long id, Long produtoId, String descricao, Long variacaoId, String sku,
      Integer quantidade, BigDecimal precoBase, BigDecimal precoUnitario, BigDecimal total,
      ModalidadeItem modalidade, StatusReserva reserva) {
  }

  public record ReservaResponse(Long id, Long itemId, Integer quantidade, StatusReserva status, Instant criadaEm,
      Instant liberadaEm, String motivoLiberacao) {
  }

  public record DescontoResponse(Long id, BigDecimal valor, BigDecimal percentual, StatusSolicitacaoDesconto status,
      String solicitante, String decididoPor, Instant decididoEm, String motivoDecisao, String justificativa,
      Instant criadoEm) {
  }

  public record HistoricoResponse(String tipo, String descricao, String usuario, Instant data) {
  }

  /** Ações disponíveis ao usuário atual, com o motivo quando bloqueadas (ex.: configuração pendente). */
  public record Acoes(boolean podeEditar, boolean podeConfirmar, boolean podeCancelar,
      boolean podeAprovarDesconto, Map<String, String> bloqueios,
      boolean podeAgendarEntrega, boolean podeReagendarEntrega, boolean podeRegistrarSaida,
      boolean podeRegistrarTentativaFrustrada, boolean podeConcluirEntrega, boolean podeAgendarMontagem,
      boolean podeConcluirMontagem, boolean podeDispensarMontagem, boolean podeAbrirOcorrencia) {
  }

  public record VendaResumoResponse(Long id, StatusComercial statusComercial, StatusPagamento statusPagamento,
      StatusEntrega statusEntrega, String cliente, String vendedor, CanalVenda canal, BigDecimal total,
      Instant criadoEm, boolean revisaoLegado) {
  }

  public record VendaDetalheResponse(
      Long id, Long version,
      StatusComercial statusComercial, StatusPagamento statusPagamento, StatusEntrega statusEntrega,
      StatusMontagem statusMontagem, boolean revisaoLegado,
      CanalVenda canal, PessoaResumo cliente, PessoaResumo vendedor,
      TipoEntrega tipoEntrega, EnderecoEntrega endereco,
      FormaPagamento formaPagamento, Integer parcelas, BigDecimal ajusteCondicaoPercentual,
      BigDecimal subtotal, BigDecimal desconto, BigDecimal frete, BigDecimal total,
      String observacao, Instant criadoEm, Instant confirmadoEm, Instant canceladoEm, String motivoCancelamento,
      List<ItemResponse> itens, List<ReservaResponse> reservas, List<DescontoResponse> descontos,
      List<HistoricoResponse> historico, Acoes acoes,
      EntregaResponse entrega, MontagemResponse montagem, List<EncomendaResponse> encomendas,
      List<OcorrenciaResponse> ocorrencias, List<MovimentacaoView> movimentacoes) {
  }

  public record Pagina<T>(List<T> conteudo, int pagina, int tamanho, long total, int totalPaginas) {
  }
}
