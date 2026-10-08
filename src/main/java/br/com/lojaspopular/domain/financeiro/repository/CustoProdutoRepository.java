package br.com.lojaspopular.domain.financeiro.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.model.CustoProduto;

public interface CustoProdutoRepository extends JpaRepository<CustoProduto, Long> {

  /** Custos próprios da variação vigentes na data, do mais recente ao mais antigo. */
  @Query("select c from CustoProduto c where c.variacao.id = :variacaoId and c.vigenteDesde <= :data "
      + "order by c.vigenteDesde desc, c.id desc")
  List<CustoProduto> daVariacao(@Param("variacaoId") Long variacaoId, @Param("data") LocalDate data);

  /** Custos do produto (sem variação) vigentes na data, do mais recente ao mais antigo. */
  @Query("select c from CustoProduto c where c.produto.id = :produtoId and c.variacao is null and c.vigenteDesde <= :data "
      + "order by c.vigenteDesde desc, c.id desc")
  List<CustoProduto> doProduto(@Param("produtoId") Long produtoId, @Param("data") LocalDate data);

  boolean existsByProdutoId(Long produtoId);

  boolean existsByVariacaoId(Long variacaoId);

  @Query("select c from CustoProduto c join fetch c.criadoPor where c.produto.id = :produtoId "
      + "order by c.vigenteDesde desc, c.id desc")
  List<CustoProduto> historicoDoProduto(@Param("produtoId") Long produtoId);
}
