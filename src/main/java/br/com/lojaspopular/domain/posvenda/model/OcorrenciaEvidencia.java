package br.com.lojaspopular.domain.posvenda.model;

import java.time.Instant;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "ocorrencia_evidencias")
public class OcorrenciaEvidencia {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ocorrencia_id", nullable = false)
  private OcorrenciaPosVenda ocorrencia;

  /** Caminho privado do arquivo (foto/PDF). */
  @Column(nullable = false, length = 300)
  private String arquivo;

  @Column(length = 200)
  private String descricao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "enviado_por")
  private User enviadoPor;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
