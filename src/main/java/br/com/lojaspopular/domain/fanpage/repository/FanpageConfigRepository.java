package br.com.lojaspopular.domain.fanpage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.fanpage.model.FanpageConfig;

public interface FanpageConfigRepository extends JpaRepository<FanpageConfig, Long> {

  Optional<FanpageConfig> findFirstByOrderByIdAsc();
}
