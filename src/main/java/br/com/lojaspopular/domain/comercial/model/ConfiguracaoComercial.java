package br.com.lojaspopular.domain.comercial.model;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
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

  private Instant atualizadoEm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "atualizado_por")
  private User atualizadoPor;
}
