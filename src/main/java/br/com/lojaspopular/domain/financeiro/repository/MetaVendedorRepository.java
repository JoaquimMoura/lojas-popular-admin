package br.com.lojaspopular.domain.financeiro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.lojaspopular.domain.financeiro.model.MetaVendedor;

public interface MetaVendedorRepository extends JpaRepository<MetaVendedor, Long> {

  Optional<MetaVendedor> findByVendedorIdAndMes(Long vendedorId, LocalDate mes);

  List<MetaVendedor> findByMes(LocalDate mes);
}
