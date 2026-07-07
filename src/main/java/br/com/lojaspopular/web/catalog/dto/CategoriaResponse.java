package br.com.lojaspopular.web.catalog.dto;

import br.com.lojaspopular.domain.catalog.enums.MaterialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaResponse {
    private Long id;
    private String nome;
    private String descricao;
    private MaterialType material;
    private String imagemUrl;
    private boolean ativa;
}

