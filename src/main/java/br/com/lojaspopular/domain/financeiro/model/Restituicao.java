package br.com.lojaspopular.domain.financeiro.model;

import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
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

/** Devolução FINANCEIRA ao cliente, com controles próprios (a devolução física é da ocorrência de pós-venda). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "restituicoes")
public class Restituicao {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ocorrencia_id", nullable = false)
  private OcorrenciaPosVenda ocorrencia;

  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal valor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FormaPagamento forma;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private StatusRestituicao status;

  @Column(nullable = false, length = 300)
  private String motivo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "solicitada_por", nullable = false)
  private User solicitadaPor;

  @Column(nullable = false)
  private Instant solicitadaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "autorizada_por")
  private User autorizadaPor;

  private Instant autorizadaEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "efetivada_por")
  private User efetivadaPor;

  private Instant efetivadaEm;

  private LocalDate dataEfetiva;

  @Column(length = 80)
  private String chaveEfetivacao;

  @Column(length = 300)
  private String motivoCancelamento;
}
