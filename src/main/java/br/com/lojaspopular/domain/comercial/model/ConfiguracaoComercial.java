package br.com.lojaspopular.domain.comercial.model;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.financeiro.enums.AquisicaoComissao;
import br.com.lojaspopular.domain.financeiro.enums.CompetenciaReceita;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuração comercial da loja (linha única, id = 1).
 * Campo nulo = decisão ainda pendente: as funções dependentes ficam bloqueadas.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "configuracao_comercial")
public class ConfiguracaoComercial {

  public static final long ID_UNICO = 1L;

  @Id
  private Long id = ID_UNICO;

  /** D03 — limite de desconto que o vendedor concede sem aprovação. */
  @Column(precision = 5, scale = 2)
  private BigDecimal limiteDescontoPercentual;

  /** D04 — arredondamento dos preços calculados. */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Arredondamento arredondamento;

  /** D07 — perfis que podem cancelar vendas (ex.: "ADMIN,GERENTE"). */
  @Column(length = 100)
  private String perfisCancelamento;

  /**
   * D05 — se o pagamento precisa estar quitado para a saída (baixa de estoque).
   * Nulo = decisão pendente: a saída fica bloqueada. Não se presume quitação pela forma de pagamento.
   */
  private Boolean exigePagamentoExpedir;

  /** D01 — percentual de comissão (igual para todos, sobre o total cobrado). Nulo = pendente: nada é apurado. */
  @Column(precision = 5, scale = 2)
  private BigDecimal comissaoPercentual;

  /** D02 — quando a comissão deixa de ser previsão e passa a ser devida. Nulo = pendente. */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private AquisicaoComissao comissaoAquisicao;

  /** D06 — qual data define o mês de competência da receita. Nulo = pendente (resultado só provisório). */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private CompetenciaReceita competenciaReceita;

  /** D07 — perfis que podem reabrir um período fechado. Nulo = reabertura indisponível. */
  @Column(length = 100)
  private String perfisReabertura;

  /** D07 — perfis que podem autorizar restituições. Nulo = autorização indisponível. */
  @Column(length = 100)
  private String perfisRestituicao;

  /** D09 — a loja permite restituir valores ao cliente? Nulo = pendente. */
  private Boolean permiteRestituicao;

  /** D09 — a loja permite cobrar a diferença de uma troca? Nulo = pendente. */
  private Boolean permiteCobrancaDiferenca;

  /** D09 — devoluções restituídas abatem a meta do vendedor? Nulo = pendente (atingimento provisório). */
  private Boolean metaDescontaDevolucoes;

  /** D10 — a aprovação do fechamento exige ausência de pendências? Nulo = aprovação indisponível. */
  private Boolean fechamentoExigeSemPendencias;

  private Instant atualizadoEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "atualizado_por")
  private User atualizadoPor;
}
