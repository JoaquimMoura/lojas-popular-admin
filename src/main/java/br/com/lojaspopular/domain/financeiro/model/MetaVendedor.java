package br.com.lojaspopular.domain.financeiro.model;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Meta mensal individual (mes = primeiro dia do mês). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "metas_vendedor")
public class MetaVendedor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "vendedor_id", nullable = false)
  private User vendedor;

  @Column(nullable = false)
  private LocalDate mes;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "definida_por")
  private User definidaPor;

  @Column(nullable = false)
  private Instant definidaEm;
}
