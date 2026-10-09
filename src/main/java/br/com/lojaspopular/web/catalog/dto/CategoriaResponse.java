package br.com.lojaspopular.web.catalog.dto;

import java.util.List;

import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.CaracteristicaView;
import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.MaterialRef;
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
    private String imagemUrl;
    private boolean ativa;
    private List<MaterialRef> materiais;
    private List<CaracteristicaView> caracteristicas;
    /** Produtos da categoria com característica obrigatória sem valor (precisam de complementação). */
    private long produtosPendentes;
}
