package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.ContaFinanceira;
import br.com.lojaspopular.domain.financeiro.model.LancamentoFinanceiro;
import br.com.lojaspopular.domain.financeiro.model.Recebimento;
import br.com.lojaspopular.domain.financeiro.model.RecebivelCartao;
import br.com.lojaspopular.domain.financeiro.model.Restituicao;
import br.com.lojaspopular.domain.financeiro.model.SessaoCaixa;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.SessaoCaixaRepository;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Único ponto de gravação do livro de lançamentos (entrada EFETIVA de dinheiro, no caixa físico ou no banco).
 *
 * <p>Regras: o livro é imutável; correção = estorno (lançamento oposto vinculado, no máximo um por lançamento);
 * dinheiro em espécie pertence sempre a uma sessão de caixa ABERTA do próprio dia; transações bancárias
 * (Pix, liquidação de cartão) nunca entram no caixa físico; período fechado não aceita lançamento.
 */
@Service
@RequiredArgsConstructor
public class LivroService {

  @Builder
  public record Lanc(ContaLivro conta, TipoLancamento tipo, BigDecimal valor, LocalDate data, OrigemLancamento origem,
      String descricao, Pedido pedido, Recebimento recebimento, RecebivelCartao recebivel, ContaFinanceira contaFinanceira,
      Restituicao restituicao, String chave, User usuario) {
  }

  private final LancamentoFinanceiroRepository repo;
  private final SessaoCaixaRepository sessoes;
  private final PeriodoService periodo;
  private final Relogio relogio;

  @Transactional
  public LancamentoFinanceiro lancar(Lanc l) {
    BigDecimal valor = l.valor().setScale(2, RoundingMode.HALF_UP);
    if (valor.signum() <= 0) {
      throw new NegocioException("O valor do lançamento deve ser maior que zero.");
    }
    periodo.exigirAberto(l.data());
    SessaoCaixa sessao = null;
    if (l.conta() == ContaLivro.CAIXA) {
      sessao = sessaoParaLancar();
      if (!l.data().equals(sessao.getDataReferencia())) {
        throw new NegocioException("Lançamentos em dinheiro entram no caixa do dia (" + sessao.getDataReferencia()
            + "); a data informada é " + l.data() + ".");
      }
      if (l.tipo() == TipoLancamento.SAIDA) {
        BigDecimal saldo = sessao.getSaldoInicial().add(repo.saldoDaSessao(sessao.getId()));
        if (saldo.compareTo(valor) < 0) {
          throw new NegocioException("Saldo insuficiente no caixa: há R$ " + saldo + " e a saída é de R$ " + valor + ".");
        }
      }
    }
    return repo.save(montar(l, valor, sessao, null));
  }

  /**
   * Estorna um lançamento: grava o lançamento oposto, vinculado ao original, na DATA ATUAL (o período do original
   * pode estar fechado). Estorno de lançamento em dinheiro exige caixa aberto hoje.
   */
  @Transactional
  public LancamentoFinanceiro estornar(LancamentoFinanceiro original, String descricao, String chave, User usuario) {
    if (repo.existsByEstornaId(original.getId())) {
      throw new NegocioException("Este lançamento já foi estornado.");
    }
    LocalDate hoje = relogio.hoje();
    periodo.exigirAberto(hoje);
    SessaoCaixa sessao = null;
    TipoLancamento oposto = original.getTipo() == TipoLancamento.ENTRADA ? TipoLancamento.SAIDA : TipoLancamento.ENTRADA;
    if (original.getConta() == ContaLivro.CAIXA) {
      sessao = sessaoParaLancar();
      if (oposto == TipoLancamento.SAIDA) {
        BigDecimal saldo = sessao.getSaldoInicial().add(repo.saldoDaSessao(sessao.getId()));
        if (saldo.compareTo(original.getValor()) < 0) {
          throw new NegocioException("Saldo insuficiente no caixa para estornar: há R$ " + saldo + " e o estorno é de R$ "
              + original.getValor() + ".");
        }
      }
    }
    Lanc l = Lanc.builder().conta(original.getConta()).tipo(oposto).valor(original.getValor()).data(hoje)
        .origem(original.getOrigem()).descricao(descricao).pedido(original.getPedido())
        .recebimento(original.getRecebimento()).recebivel(original.getRecebivel())
        .contaFinanceira(original.getContaFinanceira()).restituicao(original.getRestituicao()).chave(chave)
        .usuario(usuario).build();
    return repo.save(montar(l, original.getValor(), sessao, original));
  }

  /** Lançamento ativo (ainda não estornado) de um conjunto, o mais recente primeiro. */
  public java.util.Optional<LancamentoFinanceiro> ativo(java.util.List<LancamentoFinanceiro> lancamentos) {
    return lancamentos.stream().filter(x -> x.getEstorna() == null && !repo.existsByEstornaId(x.getId()))
        .reduce((a, b) -> b);
  }

  /** Sessão de caixa aberta do dia, travada para escrita (serializa lançamentos e o fechamento do caixa). */
  public SessaoCaixa sessaoParaLancar() {
    SessaoCaixa aberta = sessoes.findFirstByStatus(StatusSessaoCaixa.ABERTA).orElseThrow(() -> new NegocioException(
        "Caixa fechado: abra o caixa para registrar movimentos em dinheiro."));
    SessaoCaixa s = sessoes.findByIdForUpdate(aberta.getId()).orElseThrow();
    if (s.getStatus() != StatusSessaoCaixa.ABERTA) {
      throw new NegocioException("Caixa fechado: abra o caixa para registrar movimentos em dinheiro.");
    }
    if (!s.getDataReferencia().equals(relogio.hoje())) {
      throw new NegocioException("O caixa aberto é de " + s.getDataReferencia() + ": feche-o e abra o caixa de hoje.");
    }
    return s;
  }

  private LancamentoFinanceiro montar(Lanc l, BigDecimal valor, SessaoCaixa sessao, LancamentoFinanceiro estorna) {
    return LancamentoFinanceiro.builder().conta(l.conta()).tipo(l.tipo()).valor(valor).dataEfetiva(l.data())
        .origem(l.origem()).descricao(l.descricao() != null && l.descricao().length() > 300
            ? l.descricao().substring(0, 300) : l.descricao())
        .sessaoCaixa(sessao).pedido(l.pedido()).recebimento(l.recebimento()).recebivel(l.recebivel())
        .contaFinanceira(l.contaFinanceira()).restituicao(l.restituicao()).estorna(estorna).criadoPor(l.usuario())
        .chave(l.chave()).build();
  }
}
