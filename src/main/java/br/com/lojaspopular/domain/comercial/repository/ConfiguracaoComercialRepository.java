package br.com.lojaspopular.domain.comercial.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.comercial.model.ConfiguracaoComercial;

public interface ConfiguracaoComercialRepository extends JpaRepository<ConfiguracaoComercial, Long> {
}
