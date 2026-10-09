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
@Table(name = "produto_caracteristica_valores")
public class ProdutoCaracteristicaValor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caracteristica_id", nullable = false)
  private Caracteristica caracteristica;

  /** Opção escolhida (seleção única ou múltipla: uma linha por opção). Nulo em texto e número. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "opcao_id")
  private CaracteristicaOpcao opcao;

  @Column(length = 300)
  private String valorTexto;

  @Column(precision = 14, scale = 3)
  private BigDecimal valorNumero;
}
