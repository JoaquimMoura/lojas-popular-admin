package br.com.lojaspopular.web.catalog.dto;

import br.com.lojaspopular.domain.catalog.enums.MaterialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para criação/atualização de categoria.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaRequest {
    private String nome;
    private String descricao;
    private MaterialType material;
}

