package br.com.lojaspopular.domain.posvenda.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.posvenda.model.OcorrenciaPosVenda;
import jakarta.persistence.LockModeType;

public interface OcorrenciaPosVendaRepository extends JpaRepository<OcorrenciaPosVenda, Long> {

  List<OcorrenciaPosVenda> findByPedidoIdOrderByIdDesc(Long pedidoId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OcorrenciaPosVenda o where o.id = :id")
  Optional<OcorrenciaPosVenda> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = { "pedido", "item" })
  @Query("""
      select o from OcorrenciaPosVenda o
      where (:status is null or o.status = :status) and (:tipo is null or o.tipo = :tipo)
      order by o.id desc
      """)
  List<OcorrenciaPosVenda> listar(@Param("status") StatusOcorrencia status, @Param("tipo") TipoOcorrencia tipo);

  /** Quantidade já devolvida/trocada (ocorrências não canceladas) de um item. */
  @Query("""
      select coalesce(sum(o.quantidade), 0) from OcorrenciaPosVenda o
      where o.item.id = :itemId and o.tipo in ('DEVOLUCAO', 'TROCA') and o.status <> 'CANCELADA'
      """)
  long quantidadeEmDevolucao(@Param("itemId") Long itemId);
}
