package br.com.lojaspopular.domain.expedicao.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.expedicao.model.Montagem;

public interface MontagemRepository extends JpaRepository<Montagem, Long> {

  Optional<Montagem> findByPedidoId(Long pedidoId);

  @EntityGraph(attributePaths = { "pedido" })
  @Query("select m from Montagem m where m.status = 'AGENDADA' and m.dataPrevista between :de and :ate order by m.dataPrevista, m.id")
  List<Montagem> agenda(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
