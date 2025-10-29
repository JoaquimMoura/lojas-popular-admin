package br.com.lojaspopular.domain.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.catalog.enums.MaterialType;
import br.com.lojaspopular.domain.catalog.model.Categoria;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

	Optional<Categoria> findByNomeIgnoreCaseAndMaterial(String nome, MaterialType material);

	List<Categoria> findByAtivaTrue();
}