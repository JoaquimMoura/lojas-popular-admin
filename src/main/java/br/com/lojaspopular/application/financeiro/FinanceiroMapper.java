package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.Comissao;
import br.com.lojaspopular.domain.financeiro.model.ContaFinanceira;
import br.com.lojaspopular.domain.financeiro.model.LancamentoFinanceiro;
import br.com.lojaspopular.domain.financeiro.model.Recebimento;
import br.com.lojaspopular.domain.financeiro.model.RecebivelCartao;
import br.com.lojaspopular.domain.financeiro.model.Restituicao;
import br.com.lojaspopular.domain.financeiro.model.SessaoCaixa;
import br.com.lojaspopular.domain.financeiro.model.TaxaCartao;
import br.com.lojaspopular.domain.financeiro.repository.ContaEventoRepository;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebivelCartaoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaEventoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.LancamentoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RecebimentoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RecebivelView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RestituicaoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.SessaoCaixaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoView;
import lombok.RequiredArgsConstructor;

/** Conversão das entidades financeiras em respostas da API (sempre chamado dentro de transação). */
@Component
@RequiredArgsConstructor
public class FinanceiroMapper {

  private final LancamentoFinanceiroRepository lancamentos;
  private final RecebivelCartaoRepository recebiveis;
  private final ContaEventoRepository eventos;
  private final Relogio relogio;

  public static String nome(User u) {
    return u == null ? null : (u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail());
  }

  public LancamentoView view(LancamentoFinanceiro l) {
    return new LancamentoView(l.getId(), l.getConta(), l.getTipo(), l.getValor(), l.getDataEfetiva(), l.getOrigem(),
        l.getDescricao(), l.getSessaoCaixa() == null ? null : l.getSessaoCaixa().getId(),
        l.getPedido() == null ? null : l.getPedido().getId(), l.getEstorna() == null ? null : l.getEstorna().getId(),
        l.getEstorna() == null && lancamentos.existsByEstornaId(l.getId()), nome(l.getCriadoPor()), l.getCriadoEm());
  }

  public RecebivelView view(RecebivelCartao r) {
    BigDecimal diferenca = r.getValorLiquidado() == null ? null : r.getValorLiquidado().subtract(r.getValorLiquido());
    boolean vencido = r.getStatus() == StatusRecebivel.PREVISTO && r.getDataPrevista().isBefore(relogio.hoje());
    return new RecebivelView(r.getId(), r.getRecebimento().getId(), r.getPedido().getId(), r.getOperadora(), r.getParcela(),
        r.getTotalParcelas(), r.getValorBruto(), r.getTaxaPercentual(), r.getValorTaxa(), r.getValorLiquido(),
        r.getDataPrevista(), r.getStatus(), r.getDataLiquidacao(), r.getValorLiquidado(), diferenca, vencido);
  }

  public RecebimentoView view(Recebimento r) {
    var parcelas = recebiveis.findByRecebimentoIdOrderByParcelaAsc(r.getId()).stream().map(this::view).toList();
    return new RecebimentoView(r.getId(), r.getPedido().getId(), r.getForma(), r.getValor(), r.getParcelas(),
        r.getDataPagamento(), r.getReferencia(), r.getOperadora(), r.getObservacao(), r.getStatus(),
        nome(r.getRegistradoPor()), r.getCriadoEm(), r.getEstornadoEm(), nome(r.getEstornadoPor()), r.getMotivoEstorno(),
        parcelas);
  }

  public SessaoCaixaView view(SessaoCaixa s, boolean comMovimentos) {
    List<LancamentoFinanceiro> movs = lancamentos.findBySessaoCaixaIdOrderByIdAsc(s.getId());
    BigDecimal entradas = movs.stream().filter(m -> m.getTipo() == TipoLancamento.ENTRADA).map(LancamentoFinanceiro::getValor)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal saidas = movs.stream().filter(m -> m.getTipo() == TipoLancamento.SAIDA).map(LancamentoFinanceiro::getValor)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal esperado = s.getSaldoInicial().add(entradas).subtract(saidas);
    return new SessaoCaixaView(s.getId(), s.getStatus(), s.getDataReferencia(), s.getSaldoInicial(), entradas, saidas,
        s.getSaldoEsperado() != null ? s.getSaldoEsperado() : esperado, s.getSaldoContado(), s.getDiferenca(),
        s.getMotivoDiferenca(), s.getAbertaEm(), nome(s.getAbertaPor()), s.getFechadaEm(), nome(s.getFechadaPor()),
        comMovimentos ? movs.stream().map(this::view).toList() : List.of());
  }

  public TaxaCartaoView view(TaxaCartao t) {
    return new TaxaCartaoView(t.getId(), t.getOperadora(), t.getParcelas(), t.getTaxaPercentual(),
        t.getPrazoPrimeiraParcelaDias(), t.getIntervaloDias(), t.isAtiva());
  }

  public ContaView view(ContaFinanceira c, boolean comEventos) {
    var ev = comEventos ? eventos.findByContaIdOrderByIdAsc(c.getId()).stream()
        .map(e -> new ContaEventoView(e.getId(), e.getTipo(), e.getMotivo(), nome(e.getUsuario()), e.getCriadoEm())).toList()
        : List.<ContaEventoView>of();
    boolean vencida = c.getSituacao() == br.com.lojaspopular.domain.financeiro.enums.SituacaoConta.ABERTA
        && c.getVencimento().isBefore(relogio.hoje());
    return new ContaView(c.getId(), c.getTipo(), c.getDescricao(), c.getCategoria(), c.getCompetencia(), c.getValor(),
        c.getVencimento(), c.getSituacao(), c.getOrigem(), c.getPedido() == null ? null : c.getPedido().getId(),
        c.getOcorrencia() == null ? null : c.getOcorrencia().getId(), nome(c.getVendedor()), c.getObservacao(),
        c.getPagoEm(), c.getMeio(), vencida, ev);
  }

  public RestituicaoView view(Restituicao r) {
    return new RestituicaoView(r.getId(), r.getPedido().getId(), r.getOcorrencia().getId(), r.getValor(), r.getForma(),
        r.getStatus(), r.getMotivo(), nome(r.getSolicitadaPor()), r.getSolicitadaEm(), nome(r.getAutorizadaPor()),
        r.getAutorizadaEm(), nome(r.getEfetivadaPor()), r.getEfetivadaEm(), r.getDataEfetiva(), r.getMotivoCancelamento());
  }

  public ComissaoView view(Comissao c) {
    return new ComissaoView(c.getId(), c.getPedido().getId(), c.getVendedor().getId(), nome(c.getVendedor()), c.getTipo(),
        c.getStatus(), c.getBase(), c.getPercentual(), c.getValor(), c.getCompetencia(), c.getAdquiridaEm(),
        c.getReverte() == null ? null : c.getReverte().getId(), c.getConta() == null ? null : c.getConta().getId(),
        c.getMotivo(), c.getCriadaEm());
  }
}
