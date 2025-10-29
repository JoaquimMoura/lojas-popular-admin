package br.com.lojaspopular.domain.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.store.model.LojaConfig;

public interface LojaConfigRepository extends JpaRepository<LojaConfig, Long> {
}
