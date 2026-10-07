package br.com.lojaspopular.domain.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.LockModeType;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
  List<Pedido> findByUsuario(User usuario);

  Optional<Pedido> findByChaveCriacao(String chaveCriacao);

  Optional<Pedido> findByPaymentId(String paymentId);

  /** Trava o pedido: serializa confirmação, cancelamento e edição concorrentes. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Pedido p where p.id = :id")
  Optional<Pedido> findByIdForUpdate(@Param("id") Long id);

  /** Listagem gerencial. O filtro q aceita nome do cliente ou número do pedido. */
  @EntityGraph(attributePaths = { "cliente", "vendedor" })
  @Query("""
      select p from Pedido p left join p.cliente c
      where (:status is null or p.statusComercial = :status)
        and (:q = '' or lower(coalesce(c.nome, '')) like lower(concat('%', :q, '%')) or cast(p.id as string) = :q)
      order by p.id desc
      """)
  Page<Pedido> listar(@Param("status") StatusComercial status, @Param("q") String q, Pageable pageable);

  /** Listagem do escopo do vendedor: apenas vendas em que ele é o responsável. */
  @EntityGraph(attributePaths = { "cliente", "vendedor" })
  @Query("""
      select p from Pedido p left join p.cliente c
      where p.vendedor.id = :vendedorId
        and (:status is null or p.statusComercial = :status)
        and (:q = '' or lower(coalesce(c.nome, '')) like lower(concat('%', :q, '%')) or cast(p.id as string) = :q)
      order by p.id desc
      """)
  Page<Pedido> listarDoVendedor(@Param("vendedorId") Long vendedorId, @Param("status") StatusComercial status,
      @Param("q") String q, Pageable pageable);
}
