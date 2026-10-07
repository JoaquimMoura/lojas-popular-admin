package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.TipoEventoConta;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Histórico imutável de uma conta financeira. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "conta_eventos")
public class ContaEvento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conta_id", nullable = false)
  private ContaFinanceira conta;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private TipoEventoConta tipo;

  @Column(length = 300)
  private String motivo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
