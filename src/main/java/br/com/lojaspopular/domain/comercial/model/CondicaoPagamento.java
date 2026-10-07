package br.com.lojaspopular.domain.comercial.model;

import java.math.BigDecimal;

import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Condição de preço por forma de pagamento e número de parcelas. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "condicoes_pagamento",
    uniqueConstraints = @UniqueConstraint(name = "uk_condicao_forma_parcelas", columnNames = { "forma", "parcelas" }))
public class CondicaoPagamento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FormaPagamento forma;

  @Column(nullable = false)
  private Integer parcelas;

  /** Acréscimo (+) ou desconto (-) percentual aplicado ao preço base. */
  @Column(nullable = false, precision = 7, scale = 2)
  private BigDecimal ajustePercentual;

  @Column(nullable = false)
  @Builder.Default
  private boolean ativa = true;
}
