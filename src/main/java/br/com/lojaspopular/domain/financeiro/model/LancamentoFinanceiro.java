package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Livro de lançamentos: imutável. Estorno = novo lançamento oposto que aponta para o original. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "lancamentos_financeiros")
public class LancamentoFinanceiro {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ContaLivro conta;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TipoLancamento tipo;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valor;

  @Column(nullable = false)
  private LocalDate dataEfetiva;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrigemLancamento origem;

  @Column(length = 300)
  private String descricao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sessao_caixa_id")
  private SessaoCaixa sessaoCaixa;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pedido_id")
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recebimento_id")
  private Recebimento recebimento;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recebivel_id")
  private RecebivelCartao recebivel;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conta_financeira_id")
  private ContaFinanceira contaFinanceira;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "restituicao_id")
  private Restituicao restituicao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "estorna_id")
  private LancamentoFinanceiro estorna;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "criado_por")
  private User criadoPor;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();

  @Column(length = 120)
  private String chave;
}
