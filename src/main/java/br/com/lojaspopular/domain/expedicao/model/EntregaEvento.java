package br.com.lojaspopular.domain.expedicao.model;

import java.time.Instant;
import java.time.LocalDate;

import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.expedicao.enums.TipoEventoEntrega;
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

/** Linha do tempo da entrega: agendamento, reagendamentos, saída, tentativas e conclusão. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "entrega_eventos")
public class EntregaEvento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "entrega_id", nullable = false)
  private Entrega entrega;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TipoEventoEntrega tipo;

  private LocalDate dataPrevista;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private PeriodoAgenda periodo;

  @Column(length = 150)
  private String equipe;

  @Column(length = 300)
  private String motivo;

  @Column(length = 300)
  private String arquivo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
