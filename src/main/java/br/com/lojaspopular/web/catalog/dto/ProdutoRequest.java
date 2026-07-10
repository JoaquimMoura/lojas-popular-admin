package br.com.lojaspopular.web.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoRequest(
    String nome,
    String descricao,
    BigDecimal preco,
    BigDecimal precoOriginal,
    Integer estoque,
    String sku,
    String codigo,
    Long categoriaId,
    BigDecimal largura,
    BigDecimal altura,
    BigDecimal profundidade,
    BigDecimal peso,
    Integer volumes,
    List<String> diferenciais,
    List<VariacaoRequest> variacoes,
    Long version
) {
  public record VariacaoRequest(
      String cor,
      String tamanho,
      String sku,
      BigDecimal adicionalPreco,
      Integer estoque,
      String imagemUrl
  ) {}
}
