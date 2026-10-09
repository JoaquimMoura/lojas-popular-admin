package br.com.lojaspopular.domain.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.catalog.model.Caracteristica;

public interface CaracteristicaRepository extends JpaRepository<Caracteristica, Long> {

  List<Caracteristica> findByCategoriaIdOrderByOrdemAscIdAsc(Long categoriaId);
}
