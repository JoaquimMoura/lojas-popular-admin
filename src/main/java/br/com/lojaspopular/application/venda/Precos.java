package br.com.lojaspopular.application.venda;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;

/** Cálculo de preço compartilhado por venda e pós-venda (BigDecimal, arredondamento configurado). */
public final class Precos {

  private static final BigDecimal CEM = new BigDecimal("100");

  private Precos() {
  }

  /** Preço de catálogo: produto + adicional da variação. */
  public static BigDecimal base(Produto produto, ProdutoVariacao variacao) {
    BigDecimal base = produto.getPreco();
    if (variacao != null && variacao.getAdicionalPreco() != null) {
      base = base.add(variacao.getAdicionalPreco());
    }
    return base.setScale(2, RoundingMode.HALF_UP);
  }

  /** {@code base × (100 + ajuste%) / 100}, escala 2, arredondamento configurado. */
  public static BigDecimal aplicar(BigDecimal base, BigDecimal ajustePercentual, Arredondamento arred) {
    return base.multiply(CEM.add(ajustePercentual)).divide(CEM, 2, arred.mode());
  }
}
