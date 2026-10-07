package br.com.lojaspopular.domain.financeiro.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.financeiro.model.ContaEvento;

public interface ContaEventoRepository extends JpaRepository<ContaEvento, Long> {

  List<ContaEvento> findByContaIdOrderByIdAsc(Long contaId);
}
