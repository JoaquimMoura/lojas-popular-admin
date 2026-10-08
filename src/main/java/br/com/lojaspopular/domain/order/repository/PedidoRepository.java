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

  /** Vendas confirmadas (e não canceladas) que ainda não têm previsão de comissão. */
  @Query("select p from Pedido p where p.statusComercial = 'CONFIRMADA' and p.vendedor is not null "
      + "and not exists (select 1 from Comissao c where c.pedido = p and c.tipo = 'PREVISAO')")
  List<Pedido> confirmadasSemComissao();

  /** Total vendido (confirmadas, não canceladas) por um vendedor num intervalo de confirmação [de, ate). */
  @Query("select coalesce(sum(p.total), 0) from Pedido p where p.vendedor.id = :vendedorId "
      + "and p.statusComercial = 'CONFIRMADA' and p.confirmadoEm >= :de and p.confirmadoEm < :ate")
  java.math.BigDecimal somaVendidaNoPeriodo(@Param("vendedorId") Long vendedorId, @Param("de") java.time.Instant de,
      @Param("ate") java.time.Instant ate);

  @EntityGraph(attributePaths = { "itens" })
  @Query("select distinct p from Pedido p where p.statusComercial = 'CONFIRMADA' and p.confirmadoEm >= :de and p.confirmadoEm < :ate")
  List<Pedido> confirmadasNoPeriodo(@Param("de") java.time.Instant de, @Param("ate") java.time.Instant ate);

  /** Pedidos legados (checkout online anterior à gestão) criados no intervalo [de, ate). */
  @Query("select distinct p from Pedido p where p.statusComercial = 'LEGADO' and p.criadoEm >= :de and p.criadoEm < :ate")
  List<Pedido> legadosNoPeriodo(@Param("de") java.time.Instant de, @Param("ate") java.time.Instant ate);

  @Query("select count(p) from Pedido p where p.statusComercial = 'AGUARDANDO_APROVACAO'")
  long contarAguardandoAprovacao();

  @Query("select count(p) from Pedido p where p.statusComercial = 'CONFIRMADA' and p.statusPagamento <> 'PAGO' and p.confirmadoEm < :ate")
  long contarConfirmadasNaoQuitadas(@Param("ate") java.time.Instant ate);

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
