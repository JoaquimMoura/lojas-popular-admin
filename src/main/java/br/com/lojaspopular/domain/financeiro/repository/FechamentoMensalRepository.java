package br.com.lojaspopular.domain.financeiro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.StatusFechamento;
import br.com.lojaspopular.domain.financeiro.model.FechamentoMensal;

public interface FechamentoMensalRepository extends JpaRepository<FechamentoMensal, Long> {

  Optional<FechamentoMensal> findByMesAndStatus(LocalDate mes, StatusFechamento status);

  boolean existsByMesAndStatus(LocalDate mes, StatusFechamento status);

  List<FechamentoMensal> findByMesOrderByVersaoDesc(LocalDate mes);

  @Query("select coalesce(max(f.versao), 0) from FechamentoMensal f where f.mes = :mes")
  int ultimaVersao(@Param("mes") LocalDate mes);

  List<FechamentoMensal> findAllByOrderByMesDescVersaoDesc();
}
