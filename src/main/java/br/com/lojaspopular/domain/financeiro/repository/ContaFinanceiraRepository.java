package br.com.lojaspopular.domain.financeiro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.OrigemConta;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.model.ContaFinanceira;
import jakarta.persistence.LockModeType;

public interface ContaFinanceiraRepository extends JpaRepository<ContaFinanceira, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from ContaFinanceira c where c.id = :id")
  Optional<ContaFinanceira> findByIdForUpdate(@Param("id") Long id);

  List<ContaFinanceira> findByOcorrenciaIdAndOrigemAndSituacaoNot(Long ocorrenciaId, OrigemConta origem,
      SituacaoConta situacao);

  @Query("""
      select c from ContaFinanceira c
      where (:tipo is null or c.tipo = :tipo) and (:situacao is null or c.situacao = :situacao)
        and (:de is null or c.vencimento >= :de) and (:ate is null or c.vencimento <= :ate)
      order by c.vencimento, c.id
      """)
  List<ContaFinanceira> listar(@Param("tipo") TipoConta tipo, @Param("situacao") SituacaoConta situacao,
      @Param("de") LocalDate de, @Param("ate") LocalDate ate);

  @Query("select count(c) from ContaFinanceira c where c.situacao = 'ABERTA' and c.vencimento < :data")
  long contarVencidasAbertas(@Param("data") LocalDate data);

  /** Despesas (contas a pagar manuais, não canceladas) com competência no período. */
  @Query("select coalesce(sum(c.valor), 0) from ContaFinanceira c where c.tipo = 'PAGAR' and c.origem = 'MANUAL' "
      + "and c.situacao <> 'CANCELADA' and c.competencia >= :de and c.competencia <= :ate")
  java.math.BigDecimal somaDespesasDaCompetencia(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
