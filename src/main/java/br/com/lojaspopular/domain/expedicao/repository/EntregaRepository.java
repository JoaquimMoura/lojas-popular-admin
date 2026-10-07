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

  /** Entregas concluídas em [de, ate): critério de competência por data de entrega (D06). */
  @EntityGraph(attributePaths = { "pedido" })
  @Query("select e from Entrega e where e.status = 'ENTREGUE' and e.concluidaEm >= :de and e.concluidaEm < :ate")
  List<Entrega> concluidasNoPeriodo(@Param("de") java.time.Instant de, @Param("ate") java.time.Instant ate);

  @EntityGraph(attributePaths = { "pedido" })
  @Query("select e from Entrega e where e.dataPrevista between :de and :ate and e.status <> 'CANCELADA' order by e.dataPrevista, e.id")
  List<Entrega> agenda(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
