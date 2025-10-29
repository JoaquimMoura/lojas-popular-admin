package br.com.lojaspopular.domain.auditoria.repositoty;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.auditoria.model.AuditoriaEvento;

@Repository
public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {
  List<AuditoriaEvento> findAllByOrderByDataEventoDesc();
}
