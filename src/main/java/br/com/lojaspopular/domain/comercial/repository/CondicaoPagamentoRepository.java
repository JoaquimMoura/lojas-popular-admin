package br.com.lojaspopular.domain.comercial.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.comercial.model.CondicaoPagamento;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;

public interface CondicaoPagamentoRepository extends JpaRepository<CondicaoPagamento, Long> {

  Optional<CondicaoPagamento> findByFormaAndParcelas(FormaPagamento forma, Integer parcelas);

  List<CondicaoPagamento> findByAtivaTrueOrderByFormaAscParcelasAsc();

  List<CondicaoPagamento> findAllByOrderByFormaAscParcelasAsc();
}
