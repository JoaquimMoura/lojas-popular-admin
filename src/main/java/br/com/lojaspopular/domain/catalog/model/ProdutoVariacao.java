package br.com.lojaspopular.domain.catalog.model;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "produto_variacoes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProdutoVariacao {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ex.: “Branco”, “Imbuia”, “Cinza”
  @Column(length = 80)
  private String cor;

  // ex.: “Casal”, “Queen”, “3 portas”
  @Column(length = 80)
  private String tamanho;

  @Column(length = 60, unique = true)
  private String sku;

  // preço adicional aplicado sobre o preço base do produto (pode ser 0)
  @Column(precision = 12, scale = 2)
  private BigDecimal adicionalPreco;

  // estoque da variação (se não usar por variação, deixe null e use do produto)
  private Integer estoque;

  @Column(length = 500)
  private String imagemUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;
}
