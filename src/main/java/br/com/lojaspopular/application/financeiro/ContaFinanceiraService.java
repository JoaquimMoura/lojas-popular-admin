package br.com.lojaspopular.application.financeiro;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.financeiro.LivroService.Lanc;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemConta;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoEventoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.ContaEvento;
import br.com.lojaspopular.domain.financeiro.model.ContaFinanceira;
import br.com.lojaspopular.domain.financeiro.repository.ContaEventoRepository;
import br.com.lojaspopular.domain.financeiro.repository.ContaFinanceiraRepository;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaView;
import lombok.RequiredArgsConstructor;

/**
 * Contas a pagar e a receber. Cada conta tem competência (mês do resultado), vencimento, situação e histórico.
 * Baixa e estorno geram lançamentos no livro (nunca alteram o passado) e são idempotentes por chave:
 * pagar duas vezes a mesma conta não duplica saída/entrada, e o estorno preserva a trilha e o saldo correto.
 */
@Service
@RequiredArgsConstructor
public class ContaFinanceiraService {

  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final ContaFinanceiraRepository contas;
  private final ContaEventoRepository eventos;
  private final LancamentoFinanceiroRepository lancamentos;
  private final LivroService livro;
  private final PeriodoService periodo;
  private final ComissaoService comissoes;
  private final FinanceiroMapper mapper;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional(readOnly = true)
  public List<ContaView> listar(TipoConta tipo, SituacaoConta situacao, LocalDate de, LocalDate ate) {
    return contas.listar(tipo, situacao, de, ate).stream().map(c -> mapper.view(c, false)).toList();
  }

  @Transactional(readOnly = true)
  public ContaView obter(Long id) {
    return mapper.view(contas.findById(id).orElseThrow(() -> new NotFoundException("Conta não encontrada")), true);
  }

  @Transactional
  public ContaView criar(ContaRequest req) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.PAGAR);
    LocalDate competencia = Relogio.primeiroDia(req.competencia());
    periodo.exigirAberto(competencia);
    ContaFinanceira c = contas.save(ContaFinanceira.builder().tipo(req.tipo()).descricao(req.descricao().trim())
        .categoria(req.categoria().trim()).competencia(competencia).valor(escala(req.valor()))
        .vencimento(req.vencimento()).situacao(SituacaoConta.ABERTA).origem(OrigemConta.MANUAL)
        .observacao(limpar(req.observacao())).criadoPor(ator).build());
    evento(c, TipoEventoConta.CRIADA, null, ator);
    auditoria.registrar(AuditoriaTipo.CONTA_CRIADA, "Conta " + c.getTipo() + " criada: " + c.getDescricao() + " R$ "
        + c.getValor() + " (venc. " + c.getVencimento() + ")", "CONTA", c.getId());
    return mapper.view(c, true);
  }

  /** Só contas manuais e ainda abertas podem ser alteradas. */
  @Transactional
  public ContaView atualizar(Long id, ContaRequest req) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.PAGAR);
    ContaFinanceira c = contas.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    if (c.getSituacao() != SituacaoConta.ABERTA) {
      throw new NegocioException("Só é possível alterar uma conta em aberto.");
    }
    if (c.getOrigem() != OrigemConta.MANUAL) {
      throw new NegocioException("Contas geradas pelo sistema (comissão, diferença de troca) não são editáveis.");
    }
    LocalDate competencia = Relogio.primeiroDia(req.competencia());
    periodo.exigirAberto(competencia);
    periodo.exigirAberto(c.getCompetencia());
    c.setTipo(req.tipo());
    c.setDescricao(req.descricao().trim());
    c.setCategoria(req.categoria().trim());
    c.setCompetencia(competencia);
    c.setValor(escala(req.valor()));
    c.setVencimento(req.vencimento());
    c.setObservacao(limpar(req.observacao()));
    evento(c, TipoEventoConta.ALTERADA, null, ator);
    auditoria.registrar(AuditoriaTipo.CONTA_ALTERADA, "Conta #" + c.getId() + " alterada", "CONTA", c.getId());
    return mapper.view(c, true);
  }

  /** Paga (PAGAR) ou recebe (RECEBER) a conta, pelo caixa físico ou pelo banco. */
  @Transactional
  public ContaView baixar(Long id, ContaLivro meio, LocalDate data, String chave) {
    User ator = usuarioAtual.get();
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência da baixa (cabeçalho Idempotency-Key).");
    ContaFinanceira c = contas.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    permissoes.exigir(ator, c.getTipo() == br.com.lojaspopular.domain.financeiro.enums.TipoConta.PAGAR ? br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.PAGAR : br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER);
    String chaveLivro = "conta:" + k;
    if (lancamentos.findByChave(chaveLivro).isPresent()) {
      return mapper.view(c, true);
    }
    if (c.getSituacao() != SituacaoConta.ABERTA) {
      throw new NegocioException(c.getSituacao() == SituacaoConta.PAGA ? "Esta conta já foi baixada."
          : "Esta conta foi cancelada.");
    }
    LocalDate hoje = relogio.hoje();
    LocalDate dia = data == null ? hoje : data;
    if (dia.isAfter(hoje)) {
      throw new NegocioException("A data da baixa não pode ser futura.");
    }
    boolean pagar = c.getTipo() == TipoConta.PAGAR;
    livro.lancar(Lanc.builder().conta(meio).tipo(pagar ? TipoLancamento.SAIDA : TipoLancamento.ENTRADA).valor(c.getValor())
        .data(dia).origem(pagar ? OrigemLancamento.PAGAMENTO_CONTA : OrigemLancamento.RECEBIMENTO_CONTA)
        .descricao((pagar ? "Pagamento: " : "Recebimento: ") + c.getDescricao()).pedido(c.getPedido()).contaFinanceira(c)
        .chave(chaveLivro).usuario(ator).build());
    c.setSituacao(SituacaoConta.PAGA);
    c.setPagoEm(dia);
    c.setMeio(meio);
    evento(c, TipoEventoConta.PAGA, meio + " em " + dia, ator);
    if (c.getOrigem() == OrigemConta.COMISSAO) {
      comissoes.aoContaPaga(c);
    }
    auditoria.registrar(AuditoriaTipo.CONTA_PAGA, "Conta #" + c.getId() + " (" + c.getDescricao() + ") baixada: R$ "
        + c.getValor() + " via " + meio, "CONTA", c.getId());
    return mapper.view(c, true);
  }

  /** Estorna a baixa: lançamento oposto na data atual, conta volta a ficar em aberto; o histórico permanece. */
  @Transactional
  public ContaView estornar(Long id, String motivo, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.ESTORNAR);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do estorno (cabeçalho Idempotency-Key).");
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo do estorno.");
    ContaFinanceira c = contas.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    String chaveLivro = "estconta:" + k;
    if (lancamentos.findByChave(chaveLivro).isPresent()) {
      return mapper.view(c, true);
    }
    if (c.getSituacao() != SituacaoConta.PAGA) {
      throw new NegocioException("Só é possível estornar a baixa de uma conta paga/recebida.");
    }
    var original = livro.ativo(lancamentos.findByContaFinanceiraIdOrderByIdAsc(c.getId()))
        .orElseThrow(() -> new NegocioException("Não há lançamento ativo para estornar."));
    livro.estornar(original, "Estorno da baixa da conta #" + c.getId() + ": " + mot, chaveLivro, ator);
    c.setSituacao(SituacaoConta.ABERTA);
    c.setPagoEm(null);
    c.setMeio(null);
    evento(c, TipoEventoConta.ESTORNADA, mot, ator);
    if (c.getOrigem() == OrigemConta.COMISSAO) {
      comissoes.aoContaEstornada(c);
    }
    auditoria.registrar(AuditoriaTipo.CONTA_ESTORNADA, "Baixa da conta #" + c.getId() + " estornada: " + mot, "CONTA",
        c.getId());
    return mapper.view(c, true);
  }

  @Transactional
  public ContaView cancelar(Long id, String motivo) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.PAGAR);
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo do cancelamento da conta.");
    ContaFinanceira c = contas.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    if (c.getSituacao() == SituacaoConta.CANCELADA) {
      return mapper.view(c, true);
    }
    if (c.getSituacao() != SituacaoConta.ABERTA) {
      throw new NegocioException("Conta paga: estorne a baixa antes de cancelar.");
    }
    periodo.exigirAberto(c.getCompetencia());
    c.setSituacao(SituacaoConta.CANCELADA);
    evento(c, TipoEventoConta.CANCELADA, mot, ator);
    if (c.getOrigem() == OrigemConta.COMISSAO) {
      comissoes.aoContaCancelada(c);
    }
    auditoria.registrar(AuditoriaTipo.CONTA_CANCELADA, "Conta #" + c.getId() + " cancelada: " + mot, "CONTA", c.getId());
    return mapper.view(c, true);
  }

  private void evento(ContaFinanceira c, TipoEventoConta tipo, String motivo, User ator) {
    eventos.save(ContaEvento.builder().conta(c).tipo(tipo).motivo(motivo).usuario(ator).build());
  }

  private static java.math.BigDecimal escala(java.math.BigDecimal v) {
    return v.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private static String limpar(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
