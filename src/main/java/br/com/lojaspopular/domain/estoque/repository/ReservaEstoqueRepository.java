package br.com.lojaspopular.domain.estoque.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.estoque.model.ReservaEstoque;

public interface ReservaEstoqueRepository extends JpaRepository<ReservaEstoque, Long> {

  List<ReservaEstoque> findByPedidoIdOrderByIdAsc(Long pedidoId);

  List<ReservaEstoque> findByPedidoIdAndStatus(Long pedidoId, StatusReserva status);

  @Query("select coalesce(sum(r.quantidade), 0) from ReservaEstoque r where r.variacao.id = :variacaoId and r.status = :status")
  long somaPorVariacao(@Param("variacaoId") Long variacaoId, @Param("status") StatusReserva status);

  @Query("select coalesce(sum(r.quantidade), 0) from ReservaEstoque r where r.produto.id = :produtoId and r.variacao is null and r.status = :status")
  long somaPorProdutoSemVariacao(@Param("produtoId") Long produtoId, @Param("status") StatusReserva status);

  /** Reservas ativas agrupadas por unidade de estoque: [produtoId, variacaoId ou null, quantidade]. */
  @Query("select r.produto.id, r.variacao.id, sum(r.quantidade) from ReservaEstoque r where r.status = :status group by r.produto.id, r.variacao.id")
  List<Object[]> somaPorUnidade(@Param("status") StatusReserva status);

  boolean existsByVariacaoId(Long variacaoId);
}
