package br.com.lojaspopular.domain.catalog.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.lojaspopular.domain.catalog.enums.TipoCaracteristica;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.CascadeType;
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
@Table(name = "caracteristicas")
public class Caracteristica {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "categoria_id", nullable = false)
  private Categoria categoria;

  @Column(nullable = false, length = 100)
  private String nome;

  @Column(nullable = false, length = 100)
  private String nomeNormalizado;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoCaracteristica tipo;

  @Column(length = 20)
  private String unidade;

  @Column(nullable = false)
  private boolean obrigatoria;

  @Column(nullable = false)
  private int ordem;

  @Column(nullable = false)
  private boolean exibirNaVitrine;

  @Column(nullable = false)
  @Builder.Default
  private boolean ativa = true;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();

  @OneToMany(mappedBy = "caracteristica", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("ordem ASC, id ASC")
  @Builder.Default
  private List<CaracteristicaOpcao> opcoes = new ArrayList<>();
}
