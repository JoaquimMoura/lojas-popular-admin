package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemConta;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.posvenda.model.OcorrenciaPosVenda;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Conta a pagar ou a receber, com histórico de eventos. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "contas_financeiras")
public class ContaFinanceira {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TipoConta tipo;

  @Column(nullable = false, length = 200)
  private String descricao;

  @Column(nullable = false, length = 60)
  private String categoria;

  @Column(nullable = false)
  private LocalDate competencia;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valor;

  @Column(nullable = false)
  private LocalDate vencimento;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SituacaoConta situacao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrigemConta origem;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pedido_id")
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ocorrencia_id")
  private OcorrenciaPosVenda ocorrencia;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendedor_id")
  private User vendedor;

  @Column(length = 300)
  private String observacao;

  private LocalDate pagoEm;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private ContaLivro meio;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "criado_por", nullable = false)
  private User criadoPor;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();
}
