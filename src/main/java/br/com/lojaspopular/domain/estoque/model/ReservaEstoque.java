package br.com.lojaspopular.domain.estoque.model;

import java.time.Instant;

import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reserva de estoque criada na confirmação da venda (pronta entrega). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reservas_estoque")
public class ReservaEstoque {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id", nullable = false)
  private ItemPedido item;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;

  /** Nulo quando o saldo autoritativo é o do produto (produto sem variações). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variacao_id")
  private ProdutoVariacao variacao;

  @Column(nullable = false)
  private Integer quantidade;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StatusReserva status;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadaEm = Instant.now();

  private Instant liberadaEm;

  @Column(length = 300)
  private String motivoLiberacao;
}
