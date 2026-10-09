package br.com.lojaspopular.domain.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.catalog.model.Produto;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
	Page<Produto> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
	Page<Produto> findByCategoriaId(Long categoriaId, Pageable pageable);

	java.util.List<Produto> findAllByCategoriaId(Long categoriaId);

	@org.springframework.data.jpa.repository.Query("select count(p) from Produto p join p.materiais m where m.id = :id")
	long contarPorMaterial(@org.springframework.data.repository.query.Param("id") Long id);
	Page<Produto> findByCategoriaNomeContainingIgnoreCase(String categoria, Pageable pageable);
}
