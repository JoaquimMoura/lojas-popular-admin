package br.com.lojaspopular.domain.encomenda.model;

import java.time.Instant;
import java.time.LocalDate;

import br.com.lojaspopular.domain.encomenda.enums.StatusEncomenda;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Acompanhamento de um item de encomenda. A compra do fornecedor é feita fora do sistema:
 * aqui só se registra o pedido, a previsão e o recebimento físico (nada é enviado ao fornecedor).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "encomendas")
public class Encomenda {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id", nullable = false, unique = true)
  private ItemPedido item;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StatusEncomenda status;

  @Column(length = 150)
  private String fornecedor;

  @Column(length = 100)
  private String referenciaFornecedor;

  private LocalDate previsaoChegada;

  @Column(length = 300)
  private String observacao;

  private Integer quantidadeRecebida;

  private Instant recebidaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recebida_por")
  private User recebidaPor;

  @Column(length = 80)
  private String chaveRecebimento;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadaEm = Instant.now();
}
