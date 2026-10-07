package br.com.lojaspopular.domain.estoque.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.estoque.model.MovimentacaoEstoque;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {

  Optional<MovimentacaoEstoque> findByChave(String chave);

  List<MovimentacaoEstoque> findByPedidoIdOrderByIdAsc(Long pedidoId);

  /** Histórico filtrável por unidade de estoque (produto/variação) e/ou pedido. */
  @Query("""
      select m from MovimentacaoEstoque m
      where (:produtoId is null or m.produto.id = :produtoId)
        and (:variacaoId is null or m.variacao.id = :variacaoId)
        and (:pedidoId is null or m.pedidoId = :pedidoId)
      order by m.id desc
      """)
  Page<MovimentacaoEstoque> buscar(@Param("produtoId") Long produtoId, @Param("variacaoId") Long variacaoId,
      @Param("pedidoId") Long pedidoId, Pageable pageable);
}
