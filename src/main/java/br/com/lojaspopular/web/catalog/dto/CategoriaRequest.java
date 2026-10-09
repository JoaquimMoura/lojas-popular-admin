package br.com.lojaspopular.web.catalog.dto;

import java.util.List;

import br.com.lojaspopular.web.catalog.dto.CatalogoDtos.CaracteristicaRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Criação/atualização de categoria. Materiais e características são opcionais: nulo = não altera (uma categoria simples
 * pode ser salva só com nome e descrição). Configurar materiais/características exige gerente ou proprietário.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaRequest {
    private String nome;
    private String descricao;
    private List<Long> materialIds;
    private List<CaracteristicaRequest> caracteristicas;
}
