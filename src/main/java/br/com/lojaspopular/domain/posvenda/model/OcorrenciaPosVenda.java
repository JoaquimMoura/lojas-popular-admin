package br.com.lojaspopular.domain.posvenda.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.posvenda.enums.CondicaoFisica;
import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ocorrência de pós-venda (assistência, troca ou devolução). A solução financeira
 * (restituição, cobrança ou crédito da diferença) depende de política ainda não definida (D09):
 * o sistema só registra e calcula a diferença, sem movimentar valores.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "ocorrencias_pos_venda")
public class OcorrenciaPosVenda {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  /** Item do pedido a que a ocorrência se refere (obrigatório em troca e devolução). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "item_id")
  private ItemPedido item;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoOcorrencia tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StatusOcorrencia status;

  @Column(nullable = false, length = 500)
  private String descricao;

  /** Quantidade do item devolvida/trocada. */
  private Integer quantidade;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "troca_produto_id")
  private Produto trocaProduto;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "troca_variacao_id")
  private ProdutoVariacao trocaVariacao;

  private Integer trocaQuantidade;

  /** Diferença calculada (novo item - item devolvido), apenas informativa. */
  private BigDecimal diferencaCalculada;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private CondicaoFisica condicaoFisica;

  @Column(length = 300)
  private String avaliacao;

  private Instant devolucaoRecebidaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "devolucao_recebida_por")
  private User devolucaoRecebidaPor;

  /** Verdadeiro quando a devolução apta voltou ao saldo disponível. */
  @Column(nullable = false)
  private boolean estoqueReposto;

  @Column(length = 80)
  private String chaveDevolucao;

  @Column(length = 500)
  private String solucao;

  private Instant resolvidaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resolvida_por")
  private User resolvidaPor;

  @Column(length = 300)
  private String motivoCancelamento;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aberta_por", nullable = false)
  private User abertaPor;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadaEm = Instant.now();

  @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  @Builder.Default
  private List<OcorrenciaEvidencia> evidencias = new ArrayList<>();
}
