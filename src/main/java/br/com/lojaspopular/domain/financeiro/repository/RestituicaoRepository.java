package br.com.lojaspopular.domain.financeiro.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.financeiro.model.Restituicao;
import jakarta.persistence.LockModeType;

public interface RestituicaoRepository extends JpaRepository<Restituicao, Long> {

  List<Restituicao> findByPedidoIdOrderByIdDesc(Long pedidoId);

  List<Restituicao> findByOcorrenciaIdOrderByIdDesc(Long ocorrenciaId);

  @Query("select r.pedido.id from Restituicao r where r.id = :id")
  Optional<Long> pedidoIdDe(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Restituicao r where r.id = :id")
  Optional<Restituicao> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = { "pedido", "ocorrencia" })
  @Query("select r from Restituicao r where (:status is null or r.status = :status) order by r.id desc")
  List<Restituicao> listar(@Param("status") StatusRestituicao status);

  @Query("select coalesce(sum(r.valor), 0) from Restituicao r where r.pedido.id = :pedidoId and r.status in :status")
  BigDecimal somaPorPedido(@Param("pedidoId") Long pedidoId, @Param("status") Collection<StatusRestituicao> status);

  @Query("select coalesce(sum(r.valor), 0) from Restituicao r where r.ocorrencia.id = :ocorrenciaId and r.status in :status")
  BigDecimal somaPorOcorrencia(@Param("ocorrenciaId") Long ocorrenciaId,
      @Param("status") Collection<StatusRestituicao> status);

  @Query("select coalesce(sum(r.valor), 0) from Restituicao r where r.status = 'EFETIVADA' and r.dataEfetiva >= :de and r.dataEfetiva <= :ate")
  BigDecimal somaEfetivadaNoPeriodo(@Param("de") java.time.LocalDate de, @Param("ate") java.time.LocalDate ate);

  @Query("select coalesce(sum(r.valor), 0) from Restituicao r where r.status = 'EFETIVADA' and r.dataEfetiva >= :de "
      + "and r.dataEfetiva <= :ate and r.pedido.vendedor.id = :vendedorId")
  BigDecimal somaEfetivadaDoVendedor(@Param("vendedorId") Long vendedorId, @Param("de") java.time.LocalDate de,
      @Param("ate") java.time.LocalDate ate);

  @Query("select count(r) from Restituicao r where r.status in ('SOLICITADA', 'AUTORIZADA')")
  long contarEmAberto();
}
