package br.com.lojaspopular.domain.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
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

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id", nullable = false)
	private User usuario;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ItemPedido> itens;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PedidoStatus status = PedidoStatus.CRIADO;

	private BigDecimal subtotal;
	private BigDecimal frete;
	private BigDecimal total;

	private Instant criadoEm = Instant.now();
	private Instant atualizadoEm = Instant.now();
	
	  
	@Enumerated(EnumType.STRING)
	private PaymentMethod paymentMethod; // PIX, CARD, BOLETO

	private String paymentId; // ID no gateway (ex: MP payment_id)

	@PreUpdate
	public void preUpdate() {
		atualizadoEm = Instant.now();
	}

	public void calcularTotais() {
		this.subtotal = itens.stream().map(ItemPedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		this.frete = (subtotal.compareTo(new BigDecimal("1000")) >= 0) ? BigDecimal.ZERO : new BigDecimal("50.00");
		this.total = subtotal.add(frete);
	}
}
