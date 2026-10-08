package br.com.lojaspopular.domain.financeiro.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoComissao;
import br.com.lojaspopular.domain.financeiro.model.Comissao;
import jakarta.persistence.LockModeType;

public interface ComissaoRepository extends JpaRepository<Comissao, Long> {

  Optional<Comissao> findFirstByPedidoIdAndTipo(Long pedidoId, TipoComissao tipo);

  List<Comissao> findByPedidoIdOrderByIdAsc(Long pedidoId);

  List<Comissao> findByContaId(Long contaId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Comissao c where c.pedido.id = :pedidoId and c.tipo = 'PREVISAO'")
  Optional<Comissao> previsaoDoPedidoParaAtualizar(@Param("pedidoId") Long pedidoId);

  @EntityGraph(attributePaths = { "pedido", "vendedor" })
  @Query("""
      select c from Comissao c
      where (:vendedorId is null or c.vendedor.id = :vendedorId) and (:status is null or c.status = :status)
      order by c.id desc
      """)
  List<Comissao> listar(@Param("vendedorId") Long vendedorId, @Param("status") StatusComissao status);

  /** Itens a pagar de um vendedor: previsões devidas e reversões lançadas (para compensar). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Comissao c where c.vendedor.id = :vendedorId and ((c.tipo = 'PREVISAO' and c.status = 'DEVIDA') "
      + "or (c.tipo = 'REVERSAO' and c.status = 'LANCADA')) order by c.id")
  List<Comissao> aPagar(@Param("vendedorId") Long vendedorId);

  @Query("select c from Comissao c where c.pedido.confirmadoEm is not null and c.tipo = 'PREVISAO' and c.status in :status")
  List<Comissao> previsoesComStatus(@Param("status") Collection<StatusComissao> status);

  @Query("select c from Comissao c where c.competencia >= :de and c.competencia <= :ate "
      + "and c.status in ('DEVIDA', 'EM_CONTA', 'PAGA', 'LANCADA', 'COMPENSADA')")
  List<Comissao> daCompetencia(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

  /** Comissões (devidas/pagas e reversões) com competência no intervalo, para o resultado do mês. */
  @Query("select coalesce(sum(c.valor), 0) from Comissao c where c.competencia >= :de and c.competencia <= :ate "
      + "and c.status in ('DEVIDA', 'EM_CONTA', 'PAGA', 'LANCADA', 'COMPENSADA')")
  java.math.BigDecimal somaDaCompetencia(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
