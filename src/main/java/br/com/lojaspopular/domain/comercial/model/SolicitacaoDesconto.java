package br.com.lojaspopular.domain.comercial.model;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.lojaspopular.domain.comercial.enums.StatusSolicitacaoDesconto;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Solicitação de aprovação de desconto acima do limite do vendedor. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "solicitacoes_desconto")
public class SolicitacaoDesconto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "solicitante_id", nullable = false)
  private User solicitante;

  @Column(nullable = false)
  private BigDecimal valorDesconto;

  @Column(nullable = false, precision = 9, scale = 4)
  private BigDecimal percentual;

  /** Subtotal em que o desconto foi solicitado; a aprovação só vale para estes valores. */
  @Column(nullable = false)
  private BigDecimal subtotalBase;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StatusSolicitacaoDesconto status;

  @Column(length = 300)
  private String justificativa;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decidido_por")
  private User decididoPor;

  private Instant decididoEm;

  @Column(length = 300)
  private String motivoDecisao;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
