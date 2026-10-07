package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoComissao;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Comissão por venda. O percentual fica gravado no lançamento (regra histórica); reversão = lançamento negativo vinculado. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comissoes")
public class Comissao {

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
  @JoinColumn(name = "vendedor_id", nullable = false)
  private User vendedor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TipoComissao tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private StatusComissao status;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal base;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal percentual;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valor;

  private LocalDate competencia;

  private LocalDate adquiridaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reverte_id")
  private Comissao reverte;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "restituicao_id")
  private Restituicao restituicao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conta_id")
  private ContaFinanceira conta;

  @Column(length = 300)
  private String motivo;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadaEm = Instant.now();
}
