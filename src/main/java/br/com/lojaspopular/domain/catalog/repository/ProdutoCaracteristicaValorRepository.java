package br.com.lojaspopular.domain.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.catalog.model.ProdutoCaracteristicaValor;

public interface ProdutoCaracteristicaValorRepository extends JpaRepository<ProdutoCaracteristicaValor, Long> {

  List<ProdutoCaracteristicaValor> findByProdutoId(Long produtoId);

  /** Produtos distintos que têm valor para a característica (uso). */
  @Query("select count(distinct v.produto.id) from ProdutoCaracteristicaValor v where v.caracteristica.id = :id")
  long produtosComCaracteristica(@Param("id") Long id);

  boolean existsByOpcaoId(Long opcaoId);
}
