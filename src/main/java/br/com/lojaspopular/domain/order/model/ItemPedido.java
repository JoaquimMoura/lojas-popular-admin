package br.com.lojaspopular.domain.order.model;

import java.math.BigDecimal;

import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "itens_pedido")
public class ItemPedido {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;

  /** Variação vendida (nulo no fluxo legado e em produtos sem variação). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variacao_id")
  private ProdutoVariacao variacao;

  @Column(length = 60)
  private String skuHistorico;

  @Column(length = 300)
  private String descricaoHistorica;

  private Integer quantidade;

  /** Preço de catálogo (produto + adicional da variação) no momento da venda. */
  private BigDecimal precoBase;

  /** Preço unitário aplicado (já com a condição de pagamento), preservado no pedido. */
  private BigDecimal precoUnitario;

  private BigDecimal total;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private ModalidadeItem modalidade;

  /** Custo histórico congelado na confirmação (nulo = custo não informado; nunca é preenchido com zero). */
  private BigDecimal custoUnitario;

  /** CATALOGO (congelado do cadastro de custos) ou MANUAL (informado depois, com motivo e auditoria). */
  @Column(length = 20)
  private String custoOrigem;

  @PrePersist
  public void prePersist() {
    this.total = precoUnitario.multiply(new BigDecimal(quantidade));
  }
}
