package br.com.lojaspopular.domain.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;

@Repository
public interface ProdutoVariacaoRepository extends JpaRepository<ProdutoVariacao, Long> {
}
