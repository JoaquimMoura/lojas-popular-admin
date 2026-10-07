package br.com.lojaspopular.domain.encomenda.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.encomenda.enums.StatusEncomenda;
import br.com.lojaspopular.domain.encomenda.model.Encomenda;
import jakarta.persistence.LockModeType;

public interface EncomendaRepository extends JpaRepository<Encomenda, Long> {

  List<Encomenda> findByPedidoIdOrderByIdAsc(Long pedidoId);

  boolean existsByItemId(Long itemId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Encomenda e where e.id = :id")
  Optional<Encomenda> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = { "pedido", "item" })
  @Query("select e from Encomenda e where (:status is null or e.status = :status) order by e.id desc")
  List<Encomenda> listar(@Param("status") StatusEncomenda status);
}
