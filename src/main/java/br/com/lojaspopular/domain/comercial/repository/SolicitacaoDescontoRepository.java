package br.com.lojaspopular.domain.comercial.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.comercial.enums.StatusSolicitacaoDesconto;
import br.com.lojaspopular.domain.comercial.model.SolicitacaoDesconto;

public interface SolicitacaoDescontoRepository extends JpaRepository<SolicitacaoDesconto, Long> {

  List<SolicitacaoDesconto> findByPedidoIdOrderByIdDesc(Long pedidoId);

  List<SolicitacaoDesconto> findByPedidoIdAndStatusIn(Long pedidoId, Collection<StatusSolicitacaoDesconto> status);

  List<SolicitacaoDesconto> findByStatusOrderByIdAsc(StatusSolicitacaoDesconto status);
}
