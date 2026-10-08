package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.financeiro.LivroService.Lanc;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.SessaoCaixa;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.SessaoCaixaRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.SessaoCaixaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TipoMovimentoCaixa;
import lombok.RequiredArgsConstructor;

/**
 * Caixa físico (dinheiro): abertura, suprimento/retirada, contagem e fechamento diário.
 * Só dinheiro em espécie passa por aqui; Pix e liquidação de cartão são lançamentos bancários e nunca
 * aumentam o caixa físico. Diferença na contagem exige motivo e fica registrada (não é "corrigida" em silêncio).
 */
@Service
@RequiredArgsConstructor
public class CaixaService {

  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final SessaoCaixaRepository sessoes;
  private final LancamentoFinanceiroRepository lancamentos;
  private final LivroService livro;
  private final FinanceiroMapper mapper;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional
  public SessaoCaixaView abrir(BigDecimal saldoInicial) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER);
    if (sessoes.findFirstByStatus(StatusSessaoCaixa.ABERTA).isPresent()) {
      throw new NegocioException("Já existe um caixa aberto: feche-o antes de abrir outro.");
    }
    SessaoCaixa s = sessoes.save(SessaoCaixa.builder().status(StatusSessaoCaixa.ABERTA).dataReferencia(relogio.hoje())
        .saldoInicial(saldoInicial.setScale(2, java.math.RoundingMode.HALF_UP)).abertaEm(relogio.agora()).abertaPor(ator)
        .build());
    auditoria.registrar(AuditoriaTipo.CAIXA_ABERTO, "Caixa aberto com R$ " + s.getSaldoInicial(), "CAIXA", s.getId());
    return mapper.view(s, true);
  }

  @Transactional
  public SessaoCaixaView movimentar(TipoMovimentoCaixa tipo, BigDecimal valor, String motivo, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, tipo.name().equals("RETIRADA") ? br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.PAGAR : br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do movimento (cabeçalho Idempotency-Key).");
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo do movimento de caixa.");
    String chaveLivro = "caixa:" + k;
    var repetido = lancamentos.findByChave(chaveLivro);
    if (repetido.isPresent()) {
      return mapper.view(repetido.get().getSessaoCaixa(), true);
    }
    var l = livro.lancar(Lanc.builder().conta(ContaLivro.CAIXA)
        .tipo(tipo == TipoMovimentoCaixa.SUPRIMENTO ? TipoLancamento.ENTRADA : TipoLancamento.SAIDA).valor(valor)
        .data(relogio.hoje()).origem(tipo == TipoMovimentoCaixa.SUPRIMENTO ? OrigemLancamento.SUPRIMENTO_CAIXA
            : OrigemLancamento.RETIRADA_CAIXA).descricao(mot).chave(chaveLivro).usuario(ator).build());
    auditoria.registrar(AuditoriaTipo.CAIXA_MOVIMENTO, tipo + " de R$ " + l.getValor() + " no caixa: " + mot, "CAIXA",
        l.getSessaoCaixa().getId());
    return mapper.view(l.getSessaoCaixa(), true);
  }

  /** Fecha o caixa: compara o contado com o esperado; diferença exige motivo. */
  @Transactional
  public SessaoCaixaView fechar(BigDecimal saldoContado, String motivoDiferenca) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER);
    SessaoCaixa aberta = sessoes.findFirstByStatus(StatusSessaoCaixa.ABERTA)
        .orElseThrow(() -> new NegocioException("Não há caixa aberto."));
    SessaoCaixa s = sessoes.findByIdForUpdate(aberta.getId()).orElseThrow();
    if (s.getStatus() != StatusSessaoCaixa.ABERTA) {
      throw new NegocioException("Não há caixa aberto.");
    }
    BigDecimal esperado = s.getSaldoInicial().add(lancamentos.saldoDaSessao(s.getId()));
    BigDecimal contado = saldoContado.setScale(2, java.math.RoundingMode.HALF_UP);
    BigDecimal diferenca = contado.subtract(esperado);
    if (diferenca.signum() != 0 && (motivoDiferenca == null || motivoDiferenca.isBlank())) {
      throw new NegocioException("Há diferença de R$ " + diferenca + " entre o contado e o esperado (R$ " + esperado
          + "): informe o motivo para fechar o caixa.");
    }
    s.setStatus(StatusSessaoCaixa.FECHADA);
    s.setSaldoEsperado(esperado);
    s.setSaldoContado(contado);
    s.setDiferenca(diferenca);
    s.setMotivoDiferenca(diferenca.signum() == 0 || motivoDiferenca == null ? null : motivoDiferenca.trim());
    s.setFechadaEm(relogio.agora());
    s.setFechadaPor(ator);
    auditoria.registrar(AuditoriaTipo.CAIXA_FECHADO, "Caixa fechado: esperado R$ " + esperado + ", contado R$ " + contado
        + (diferenca.signum() == 0 ? "" : ", diferença R$ " + diferenca), "CAIXA", s.getId());
    return mapper.view(s, true);
  }

  @Transactional(readOnly = true)
  public SessaoCaixaView atual() {
    return sessoes.findFirstByStatus(StatusSessaoCaixa.ABERTA).map(s -> mapper.view(s, true)).orElse(null);
  }

  @Transactional(readOnly = true)
  public SessaoCaixaView obter(Long id) {
    return mapper.view(sessoes.findById(id).orElseThrow(() -> new NotFoundException("Sessão de caixa não encontrada")), true);
  }

  @Transactional(readOnly = true)
  public List<SessaoCaixaView> historico(int limite) {
    return sessoes.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(Math.max(limite, 1), 100))).stream()
        .map(s -> mapper.view(s, false)).toList();
  }
}
