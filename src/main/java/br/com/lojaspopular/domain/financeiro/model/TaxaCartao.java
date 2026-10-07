package br.com.lojaspopular.domain.financeiro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Taxa e prazos da operadora, cadastrados pela loja (nenhum valor é presumido). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "taxas_cartao")
public class TaxaCartao {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 60)
  private String operadora;

  @Column(nullable = false)
  private Integer parcelas;

  @Column(nullable = false, precision = 7, scale = 4)
  private BigDecimal taxaPercentual;

  @Column(nullable = false)
  private Integer prazoPrimeiraParcelaDias;

  @Column(nullable = false)
  private Integer intervaloDias;

  @Column(nullable = false)
  @Builder.Default
  private boolean ativa = true;
}
