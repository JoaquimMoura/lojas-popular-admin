package br.com.lojaspopular.domain.catalog.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import br.com.lojaspopular.domain.catalog.model.Categoria;
import br.com.lojaspopular.web.catalog.dto.CategoriaRequest;
import br.com.lojaspopular.web.catalog.dto.CategoriaResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoriaMapper {

    // 🔹 Converte DTO → Entidade
    @Mapping(target = "id", ignore = true) // o ID é gerado pelo banco
    @Mapping(target = "ativa", ignore = true) // o service define o status
    Categoria toEntity(CategoriaRequest dto);

    // 🔹 Converte Entidade → DTO
    CategoriaResponse toResponse(Categoria entity);

    // 🔹 Converte Lista de Entidades → Lista de DTOs
    List<CategoriaResponse> toResponseList(List<Categoria> entities);
}

