package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
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

/** Valor a receber da OPERADORA (bruto, taxa, líquido e previsão). O dinheiro só entra no banco na liquidação. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "recebiveis_cartao")
public class RecebivelCartao {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recebimento_id", nullable = false)
  private Recebimento recebimento;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @Column(nullable = false, length = 60)
  private String operadora;

  @Column(nullable = false)
  private Integer parcela;

  @Column(nullable = false)
  private Integer totalParcelas;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valorBruto;

  @Column(nullable = false, precision = 7, scale = 4)
  private BigDecimal taxaPercentual;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valorTaxa;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valorLiquido;

  @Column(nullable = false)
  private LocalDate dataPrevista;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private StatusRecebivel status;

  private LocalDate dataLiquidacao;

  @Column(precision = 38, scale = 2)
  private BigDecimal valorLiquidado;

  private Instant liquidadoEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "liquidado_por")
  private User liquidadoPor;

  @Column(length = 80)
  private String chaveLiquidacao;
}
