package br.com.lojaspopular.domain.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.catalog.model.Produto;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
	Page<Produto> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}
