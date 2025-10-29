package br.com.lojaspopular.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.order.model.ItemPedido;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {}
