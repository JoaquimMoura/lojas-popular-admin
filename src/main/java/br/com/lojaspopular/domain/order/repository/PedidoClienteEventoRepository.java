package br.com.lojaspopular.domain.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.order.model.PedidoClienteEvento;

public interface PedidoClienteEventoRepository extends JpaRepository<PedidoClienteEvento, Long> {

  List<PedidoClienteEvento> findByPedidoIdOrderByIdAsc(Long pedidoId);
}
