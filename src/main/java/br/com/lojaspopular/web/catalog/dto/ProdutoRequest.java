package br.com.lojaspopular.web.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoRequest(
    String nome,
    String descricao,
    BigDecimal preco,
    Integer estoque,
    String sku,
    Long categoriaId,
    List<VariacaoRequest> variacoes // opcional
) {
  public record VariacaoRequest(
      String cor,
      String tamanho,
      String sku,
      BigDecimal adicionalPreco,
      Integer estoque
  ) {}
}
