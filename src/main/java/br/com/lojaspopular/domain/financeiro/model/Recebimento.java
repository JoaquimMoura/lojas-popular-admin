package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.StatusRecebimento;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pagamento feito pelo CLIENTE (uma única forma por venda). Não é, por si só, dinheiro em caixa ou no banco. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "recebimentos")
public class Recebimento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FormaPagamento forma;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valor;

  private Integer parcelas;

  /** CREDITO ou DEBITO (só no cartão). */
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private br.com.lojaspopular.domain.financeiro.enums.TipoCartao tipoCartao;

  /** Cartão registrado sem taxa cadastrada: um recebível único, sem taxa, liquidado à mão com o valor real depositado. */
  @Column(nullable = false)
  @Builder.Default
  private boolean planoManual = false;

  @Column(nullable = false)
  private LocalDate dataPagamento;

  @Column(length = 100)
  private String referencia;

  @Column(length = 60)
  private String operadora;

  @Column(length = 300)
  private String observacao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private StatusRecebimento status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "registrado_por", nullable = false)
  private User registradoPor;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();

  @Column(nullable = false, length = 80)
  private String chave;

  private Instant estornadoEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "estornado_por")
  private User estornadoPor;

  @Column(length = 300)
  private String motivoEstorno;

  @Column(length = 80)
  private String chaveEstorno;
}
