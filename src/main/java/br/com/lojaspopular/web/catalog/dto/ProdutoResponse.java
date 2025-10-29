package br.com.lojaspopular.web.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoResponse(
    Long id,
    String nome,
    String descricao,
    BigDecimal preco,
    Integer estoque,
    String sku,
    String imagemUrl,
    String categoria, // nome da categoria
    List<VariacaoResponse> variacoes,
    List<String> galeria // URLs
) {
  public record VariacaoResponse(
      Long id,
      String cor,
      String tamanho,
      String sku,
      BigDecimal adicionalPreco,
      Integer estoque
  ) {}
}
