package br.com.lojaspopular.domain.financeiro.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.model.Recebimento;
import jakarta.persistence.LockModeType;

public interface RecebimentoRepository extends JpaRepository<Recebimento, Long> {

  Optional<Recebimento> findByChave(String chave);

  List<Recebimento> findByPedidoIdOrderByIdAsc(Long pedidoId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Recebimento r where r.id = :id")
  Optional<Recebimento> findByIdForUpdate(@Param("id") Long id);

  @Query("select r.pedido.id from Recebimento r where r.id = :id")
  Optional<Long> pedidoIdDe(@Param("id") Long id);

  /** Total pago pelo cliente e ainda válido (não estornado). */
  @Query("select coalesce(sum(r.valor), 0) from Recebimento r where r.pedido.id = :pedidoId and r.status = 'REGISTRADO'")
  BigDecimal somaAtiva(@Param("pedidoId") Long pedidoId);
}
