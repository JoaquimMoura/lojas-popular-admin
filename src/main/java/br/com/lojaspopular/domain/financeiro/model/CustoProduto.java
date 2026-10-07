package br.com.lojaspopular.domain.financeiro.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
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

/**
 * Custo de um produto (ou de uma variação) a partir de uma data. Imutável: um novo custo é um novo registro, e o
 * custo vigente é o mais recente cuja vigência já começou. Variação sem custo próprio herda o do produto.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "custos_produto")
public class CustoProduto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variacao_id")
  private ProdutoVariacao variacao;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal custo;

  @Column(nullable = false)
  private LocalDate vigenteDesde;

  @Column(length = 300)
  private String motivo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "criado_por", nullable = false)
  private User criadoPor;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
