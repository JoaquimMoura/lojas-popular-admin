package br.com.lojaspopular.web.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProdutoRequest(
    @NotBlank(message = "Nome é obrigatório") String nome,
    String descricao,
    @NotNull(message = "Preço é obrigatório") @PositiveOrZero(message = "Preço não pode ser negativo") BigDecimal preco,
    BigDecimal precoOriginal,
    @NotNull(message = "Estoque é obrigatório") @PositiveOrZero(message = "Estoque não pode ser negativo") Integer estoque,
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
