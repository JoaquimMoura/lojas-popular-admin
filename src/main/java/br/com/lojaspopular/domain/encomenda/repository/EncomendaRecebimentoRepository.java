package br.com.lojaspopular.domain.encomenda.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.encomenda.model.EncomendaRecebimento;

public interface EncomendaRecebimentoRepository extends JpaRepository<EncomendaRecebimento, Long> {

  Optional<EncomendaRecebimento> findByChave(String chave);

  List<EncomendaRecebimento> findByEncomendaIdOrderByIdAsc(Long encomendaId);
}
