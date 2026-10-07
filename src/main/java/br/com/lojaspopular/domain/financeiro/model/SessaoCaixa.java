package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
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

/** Sessão (dia) do caixa físico. Só existe uma aberta por vez. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "sessoes_caixa")
public class SessaoCaixa {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private StatusSessaoCaixa status;

  @Column(nullable = false)
  private LocalDate dataReferencia;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal saldoInicial;

  @Column(nullable = false)
  private Instant abertaEm;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aberta_por", nullable = false)
  private User abertaPor;

  @Column(precision = 38, scale = 2)
  private BigDecimal saldoEsperado;

  @Column(precision = 38, scale = 2)
  private BigDecimal saldoContado;

  @Column(precision = 38, scale = 2)
  private BigDecimal diferenca;

  @Column(length = 300)
  private String motivoDiferenca;

  private Instant fechadaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fechada_por")
  private User fechadaPor;
}
