package br.com.lojaspopular.domain.cliente.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.cliente.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

  Optional<Cliente> findByCpf(String cpf);

  List<Cliente> findByTelefone(String telefone);

  @Query("""
      select c from Cliente c
      where c.ativo = true
        and (lower(c.nome) like lower(concat('%', :q, '%'))
             or c.cpf like concat('%', :digitos, '%')
             or c.telefone like concat('%', :digitos, '%'))
      order by c.nome
      """)
  List<Cliente> buscar(@Param("q") String q, @Param("digitos") String digitos, Pageable pageable);

  @Query("select c from Cliente c where lower(c.nome) = lower(:nome)")
  List<Cliente> findByNomeIgnoreCase(@Param("nome") String nome);
}
