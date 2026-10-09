package br.com.lojaspopular.domain.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.catalog.model.Material;

public interface MaterialRepository extends JpaRepository<Material, Long> {

  Optional<Material> findByNomeNormalizado(String nomeNormalizado);

  List<Material> findAllByOrderByNomeAsc();
}
