package br.com.lojaspopular.web.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoResponse(
    Long id,
    String nome,
    String descricao,
    BigDecimal preco,
    BigDecimal precoOriginal,
    Integer estoque,
    String sku,
    String codigo,
    String imagemUrl,
    String categoria,
    Long categoriaId,
    BigDecimal largura,
    BigDecimal altura,
    BigDecimal profundidade,
    BigDecimal peso,
    Integer volumes,
    List<String> diferenciais,
    List<VariacaoResponse> variacoes,
    List<String> galeria
) {
  public record VariacaoResponse(
      Long id,
      String cor,
      String tamanho,
      String sku,
      BigDecimal adicionalPreco,
      Integer estoque,
      String imagemUrl
  ) {}
}
