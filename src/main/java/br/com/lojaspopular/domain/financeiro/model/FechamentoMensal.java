package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.StatusFechamento;
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
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fechamento mensal versionado; APROVADO bloqueia lançamentos retroativos no mês. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "fechamentos_mensais")
public class FechamentoMensal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDate mes;

  @Column(nullable = false)
  private Integer versao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private StatusFechamento status;

  @Column(nullable = false, columnDefinition = "text")
  private String snapshot;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aprovado_por", nullable = false)
  private User aprovadoPor;

  @Column(nullable = false)
  private Instant aprovadoEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reaberto_por")
  private User reabertoPor;

  private Instant reabertoEm;

  @Column(length = 300)
  private String justificativaReabertura;
}
