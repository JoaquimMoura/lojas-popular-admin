package br.com.lojaspopular.domain.expedicao.model;

import java.time.Instant;
import java.time.LocalDate;

import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.expedicao.enums.StatusMontagemRegistro;
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

/** Montagem inclusa no preço: não há valor nem cobrança adicional. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "montagens")
public class Montagem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false, unique = true)
  private Pedido pedido;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StatusMontagemRegistro status;

  private LocalDate dataPrevista;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private PeriodoAgenda periodo;

  /** Responsável pela montagem (texto livre). */
  @Column(length = 150)
  private String responsavel;

  @Column(length = 300)
  private String observacao;

  private Instant concluidaEm;

  /** Caminho privado da evidência (foto/PDF). */
  @Column(length = 300)
  private String evidenciaArquivo;

  @Column(length = 300)
  private String motivoDispensa;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadaEm = Instant.now();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "atualizada_por")
  private User atualizadaPor;
}
