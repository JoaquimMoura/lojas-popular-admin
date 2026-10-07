package br.com.lojaspopular.domain.estoque.model;

import java.time.Instant;

import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.estoque.enums.TipoMovimentacao;
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

/** Trilha imutável do saldo físico: cada alteração de estoque gera uma movimentação. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "movimentacoes_estoque")
public class MovimentacaoEstoque {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TipoMovimentacao tipo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "produto_id", nullable = false)
  private Produto produto;

  /** Nulo quando o saldo autoritativo é o do produto (produto sem variações). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variacao_id")
  private ProdutoVariacao variacao;

  /** Variação do saldo: positivo = entrada, negativo = saída. */
  @Column(nullable = false)
  private Integer quantidade;

  private Integer saldoAnterior;

  @Column(nullable = false)
  private Integer saldoPosterior;

  private Long pedidoId;
  private Long itemId;
  private Long encomendaId;
  private Long ocorrenciaId;

  @Column(length = 300)
  private String motivo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(length = 80)
  private String chave;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
