package br.com.lojaspopular.domain.financeiro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.model.RecebivelCartao;
import jakarta.persistence.LockModeType;

public interface RecebivelCartaoRepository extends JpaRepository<RecebivelCartao, Long> {

  List<RecebivelCartao> findByRecebimentoIdOrderByParcelaAsc(Long recebimentoId);

  List<RecebivelCartao> findByPedidoIdOrderByIdAsc(Long pedidoId);

  List<RecebivelCartao> findByPedidoIdIn(java.util.Collection<Long> pedidoIds);

  List<RecebivelCartao> findByPedidoIdAndStatusOrderByParcelaDesc(Long pedidoId, StatusRecebivel status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RecebivelCartao r where r.id = :id")
  Optional<RecebivelCartao> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = { "pedido" })
  @Query("""
      select r from RecebivelCartao r
      where (:status is null or r.status = :status)
        and (:operadora = '' or r.operadora = :operadora)
        and (:de is null or r.dataPrevista >= :de) and (:ate is null or r.dataPrevista <= :ate)
      order by r.dataPrevista, r.id
      """)
  List<RecebivelCartao> listar(@Param("status") StatusRecebivel status, @Param("operadora") String operadora,
      @Param("de") LocalDate de, @Param("ate") LocalDate ate);

  /** Taxas de cartão dos recebimentos pagos no período (competência por data do pagamento do cliente). */
  @Query("select coalesce(sum(r.valorTaxa), 0) from RecebivelCartao r where r.status <> 'CANCELADO' "
      + "and r.recebimento.dataPagamento >= :de and r.recebimento.dataPagamento <= :ate")
  java.math.BigDecimal somaTaxasDoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

  @Query("select count(r) from RecebivelCartao r where r.status = 'PREVISTO' and r.dataPrevista < :data")
  long contarVencidosNaoLiquidados(@Param("data") LocalDate data);
}
