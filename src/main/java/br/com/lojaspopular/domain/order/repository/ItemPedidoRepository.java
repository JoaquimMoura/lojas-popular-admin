package br.com.lojaspopular.domain.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.lojaspopular.domain.order.model.ItemPedido;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

  boolean existsByProdutoId(Long produtoId);

  boolean existsByVariacaoId(Long variacaoId);

  /** Itens de encomenda de vendas confirmadas que ainda não têm acompanhamento (vendas anteriores à Etapa 2). */
  @Query("""
      select i from ItemPedido i
      where i.modalidade = 'ENCOMENDA' and i.pedido.statusComercial = 'CONFIRMADA'
        and not exists (select 1 from Encomenda e where e.item = i)
      """)
  List<ItemPedido> encomendasSemAcompanhamento();
}
