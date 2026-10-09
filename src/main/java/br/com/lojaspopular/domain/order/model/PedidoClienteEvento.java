package br.com.lojaspopular.domain.order.model;

import java.time.Instant;

import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Trilha imutável de vínculo posterior e troca do cliente de uma venda (quem, quando, por quê). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pedido_cliente_eventos")
public class PedidoClienteEvento {

  public static final String VINCULO_POSTERIOR = "VINCULO_POSTERIOR";
  public static final String TROCA = "TROCA";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @Column(nullable = false, length = 20)
  private String tipo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cliente_anterior_id")
  private Cliente clienteAnterior;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cliente_novo_id", nullable = false)
  private Cliente clienteNovo;

  @Column(nullable = false, length = 300)
  private String justificativa;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "usuario_id", nullable = false)
  private User usuario;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
