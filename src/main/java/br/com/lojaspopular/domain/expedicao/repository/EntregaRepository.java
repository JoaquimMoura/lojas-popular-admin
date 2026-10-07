package br.com.lojaspopular.domain.expedicao.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.expedicao.model.Entrega;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

  Optional<Entrega> findByPedidoId(Long pedidoId);

  @EntityGraph(attributePaths = { "pedido" })
  @Query("select e from Entrega e where e.dataPrevista between :de and :ate and e.status <> 'CANCELADA' order by e.dataPrevista, e.id")
  List<Entrega> agenda(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
