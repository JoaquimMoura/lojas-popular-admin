package br.com.lojaspopular.domain.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusMontagem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.payment.enums.PaymentMethod;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pedidos")
public class Pedido {

	/** Nome do cliente como na venda; sem cópia, o do cadastro; sem cliente, nulo. */
	public String clienteNomeHistorico() {
		return clienteNomeHist != null ? clienteNomeHist : (cliente == null ? null : cliente.getNome());
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	@Column(nullable = false)
	private Long version;

	/** Usuário autenticado que criou o pedido (legado: dono do pedido no fluxo público). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id", nullable = false)
	private User usuario;

	/** Dados do cliente COMO ESTAVAM na venda (alterar o cadastro depois não muda o histórico). */
	@Column(length = 150)
	private String clienteNomeHist;

	@Column(length = 11)
	private String clienteCpfHist;

	@Column(length = 20)
	private String clienteTelefoneHist;

	/** Comprador. Nulo em pedidos legados (não é inventado na migração). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;

	/** Vendedor responsável. Nulo em pedidos legados. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vendedor_id")
	private User vendedor;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<ItemPedido> itens = new ArrayList<>();

	/** Status legado, mantido por compatibilidade com o fluxo de pagamento existente. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@Builder.Default
	private PedidoStatus status = PedidoStatus.CRIADO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	@Builder.Default
	private StatusComercial statusComercial = StatusComercial.LEGADO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private StatusPagamento statusPagamento = StatusPagamento.NAO_INFORMADO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private StatusEntrega statusEntrega = StatusEntrega.NAO_INFORMADO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private StatusMontagem statusMontagem = StatusMontagem.NAO_INFORMADO;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private CanalVenda canal;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private FormaPagamento formaPagamento;

	private Integer parcelas;

	/** Ajuste (%) da condição de pagamento vigente na época da venda (histórico). */
	@Column(precision = 7, scale = 2)
	private BigDecimal ajusteCondicaoPercentual;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private TipoEntrega tipoEntrega;

	// Endereço de entrega preservado no pedido (não muda se o cadastro do cliente mudar)
	@Column(length = 8)
	private String entregaCep;
	@Column(length = 150)
	private String entregaLogradouro;
	@Column(length = 20)
	private String entregaNumero;
	@Column(length = 100)
	private String entregaComplemento;
	@Column(length = 100)
	private String entregaBairro;
	@Column(length = 100)
	private String entregaCidade;
	@Column(length = 2)
	private String entregaUf;

	private BigDecimal subtotal;

	@Column(nullable = false)
	@Builder.Default
	private BigDecimal desconto = BigDecimal.ZERO;

	private BigDecimal frete;
	private BigDecimal total;

	@Column(length = 500)
	private String observacao;

	@Column(length = 80)
	private String chaveCriacao;

	@Column(length = 80)
	private String chaveConfirmacao;

	private Instant confirmadoEm;

	/** Momento da baixa física de estoque (saída). Depois dela a venda não pode mais ser cancelada. */
	private Instant saidaRealizadaEm;

	@Column(length = 80)
	private String chaveSaida;
	private Instant canceladoEm;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cancelado_por")
	private User canceladoPor;

	@Column(length = 300)
	private String motivoCancelamento;

	/** Pedido legado que precisa de conferência manual (ex.: CRIADO). */
	@Column(nullable = false)
	private boolean revisaoLegado;

	@Builder.Default
	private Instant criadoEm = Instant.now();

	@Builder.Default
	private Instant atualizadoEm = Instant.now();

	@Enumerated(EnumType.STRING)
	private PaymentMethod paymentMethod; // PIX, CARD, BOLETO

	private String paymentId; // ID no gateway (ex: MP payment_id)

	@PreUpdate
	public void preUpdate() {
		atualizadoEm = Instant.now();
	}

	/**
	 * Totais do fluxo legado. Frete é sempre zero (política da loja: frete gratuito);
	 * pedidos antigos mantêm o frete que foi gravado na época.
	 */
	public void calcularTotais() {
		this.subtotal = itens.stream().map(ItemPedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		this.frete = BigDecimal.ZERO;
		this.total = subtotal.subtract(desconto == null ? BigDecimal.ZERO : desconto).add(frete);
	}

	public boolean isLegado() {
		return statusComercial == StatusComercial.LEGADO;
	}
}
