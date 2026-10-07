package br.com.lojaspopular.domain.financeiro.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.financeiro.model.TaxaCartao;

public interface TaxaCartaoRepository extends JpaRepository<TaxaCartao, Long> {

  Optional<TaxaCartao> findByOperadoraAndParcelas(String operadora, Integer parcelas);

  List<TaxaCartao> findAllByOrderByOperadoraAscParcelasAsc();

  List<TaxaCartao> findByAtivaTrueOrderByOperadoraAscParcelasAsc();
}
