package br.com.lojaspopular.domain.financeiro.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.model.LancamentoFinanceiro;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {

  Optional<LancamentoFinanceiro> findByChave(String chave);

  boolean existsByEstornaId(Long estornaId);

  List<LancamentoFinanceiro> findBySessaoCaixaIdOrderByIdAsc(Long sessaoId);

  List<LancamentoFinanceiro> findByPedidoIdOrderByIdAsc(Long pedidoId);

  List<LancamentoFinanceiro> findByRecebimentoIdOrderByIdAsc(Long recebimentoId);

  List<LancamentoFinanceiro> findByRecebivelIdOrderByIdAsc(Long recebivelId);

  List<LancamentoFinanceiro> findByContaFinanceiraIdOrderByIdAsc(Long contaId);

  List<LancamentoFinanceiro> findByRestituicaoIdOrderByIdAsc(Long restituicaoId);

  /** Saldo (entradas - saídas) dos lançamentos de uma sessão de caixa. */
  @Query("select coalesce(sum(case when l.tipo = 'ENTRADA' then l.valor else -l.valor end), 0) "
      + "from LancamentoFinanceiro l where l.sessaoCaixa.id = :sessaoId")
  BigDecimal saldoDaSessao(@Param("sessaoId") Long sessaoId);

  @Query("""
      select l from LancamentoFinanceiro l
      where l.dataEfetiva >= :de and l.dataEfetiva <= :ate and (:conta = '' or l.conta = :contaEnum)
      order by l.dataEfetiva desc, l.id desc
      """)
  List<LancamentoFinanceiro> periodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate,
      @Param("conta") String conta, @Param("contaEnum") ContaLivro contaEnum);

  /** [conta, origem, tipo, soma] dos lançamentos efetivos do período (estornos já entram como lançamentos opostos). */
  @Query("select l.conta, l.origem, l.tipo, sum(l.valor) from LancamentoFinanceiro l "
      + "where l.dataEfetiva >= :de and l.dataEfetiva <= :ate group by l.conta, l.origem, l.tipo")
  List<Object[]> totaisDoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
