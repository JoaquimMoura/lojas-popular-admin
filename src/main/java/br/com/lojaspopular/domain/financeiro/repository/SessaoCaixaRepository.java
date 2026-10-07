package br.com.lojaspopular.domain.financeiro.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
import br.com.lojaspopular.domain.financeiro.model.SessaoCaixa;
import jakarta.persistence.LockModeType;

public interface SessaoCaixaRepository extends JpaRepository<SessaoCaixa, Long> {

  Optional<SessaoCaixa> findFirstByStatus(StatusSessaoCaixa status);

  List<SessaoCaixa> findAllByOrderByIdDesc(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SessaoCaixa s where s.id = :id")
  Optional<SessaoCaixa> findByIdForUpdate(@Param("id") Long id);

  long countByStatusAndDataReferenciaLessThan(StatusSessaoCaixa status, java.time.LocalDate data);
}
