package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.financeiro.LivroService.Lanc;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemConta;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoEventoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.ContaEvento;
import br.com.lojaspopular.domain.financeiro.model.ContaFinanceira;
import br.com.lojaspopular.domain.financeiro.model.Restituicao;
import br.com.lojaspopular.domain.financeiro.repository.ContaEventoRepository;
import br.com.lojaspopular.domain.financeiro.repository.ContaFinanceiraRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebimentoRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebivelCartaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.RestituicaoRepository;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.posvenda.model.OcorrenciaPosVenda;
import br.com.lojaspopular.domain.posvenda.repository.OcorrenciaPosVendaRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RestituicaoView;
import lombok.RequiredArgsConstructor;

/**
 * Devolução FINANCEIRA ao cliente, com controles próprios (a devolução física é da ocorrência de pós-venda e exige
 * recebimento físico antes). Restituição e cobrança de diferença de troca só existem conforme a política D09; quem
 * autoriza vem de D07. Fluxo: solicitar → autorizar → efetivar (ou cancelar); efetivar é idempotente e é a única
 * etapa que movimenta dinheiro (caixa, banco ou recebíveis de cartão). Nada é movimentado automaticamente.
 */
@Service
@RequiredArgsConstructor
public class RestituicaoService {

  private static final Set<StatusRestituicao> EM_USO = Set.of(StatusRestituicao.SOLICITADA, StatusRestituicao.AUTORIZADA,
      StatusRestituicao.EFETIVADA);

  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final RestituicaoRepository restituicoes;
  private final OcorrenciaPosVendaRepository ocorrencias;
  private final RecebimentoRepository recebimentos;
  private final RecebivelCartaoRepository recebiveis;
  private final ContaFinanceiraRepository contas;
  private final ContaEventoRepository eventos;
  private final PedidoRepository pedidos;
  private final VendaAcesso acesso;
  private final LivroService livro;
  private final PeriodoService periodo;
  private final ComissaoService comissoes;
  private final ConfiguracaoComercialService config;
  private final FinanceiroMapper mapper;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional
  public RestituicaoView solicitar(Long ocorrenciaId, BigDecimal valorSolicitado, String motivo) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RESTITUIR);
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo da restituição.");
    exigirPoliticaRestituicao();
    OcorrenciaPosVenda o = ocorrencias.findById(ocorrenciaId).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada"));
    Pedido p = acesso.travar(o.getPedido().getId(), ator);
    BigDecimal valor = valorSolicitado.setScale(2, RoundingMode.HALF_UP);

    if (o.getTipo() == TipoOcorrencia.ASSISTENCIA) {
      throw new NegocioException("Assistência não gera restituição.");
    }
    if (o.getDevolucaoRecebidaEm() == null) {
      throw new NegocioException("A devolução física precisa ser recebida e avaliada antes da restituição financeira "
          + "(são controles distintos).");
    }
    BigDecimal recebido = recebimentos.somaAtiva(p.getId());
    BigDecimal comprometido = restituicoes.somaPorPedido(p.getId(), EM_USO);
    if (valor.compareTo(recebido.subtract(comprometido)) > 0) {
      throw new NegocioException("O valor excede o que o cliente pagou e ainda não foi restituído (R$ "
          + recebido.subtract(comprometido) + ").");
    }
    BigDecimal teto = tetoDaOcorrencia(o);
    BigDecimal jaNaOcorrencia = restituicoes.somaPorOcorrencia(o.getId(), EM_USO);
    if (valor.compareTo(teto.subtract(jaNaOcorrencia)) > 0) {
      throw new NegocioException("O valor excede o limite desta ocorrência (R$ " + teto.subtract(jaNaOcorrencia) + ").");
    }
    Restituicao r = restituicoes.save(Restituicao.builder().pedido(p).ocorrencia(o).valor(valor).forma(p.getFormaPagamento())
        .status(StatusRestituicao.SOLICITADA).motivo(mot).solicitadaPor(ator).solicitadaEm(relogio.agora()).build());
    auditoria.registrar(AuditoriaTipo.RESTITUICAO_SOLICITADA, "Restituição de R$ " + valor + " solicitada (ocorrência #"
        + o.getId() + ", venda #" + p.getId() + ")", "PEDIDO", p.getId());
    return mapper.view(r);
  }

  @Transactional
  public RestituicaoView autorizar(Long id) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RESTITUIR);
    Set<Role> perfis = config.perfis(config.obter().getPerfisRestituicao());
    if (perfis.isEmpty()) {
      throw new ConfiguracaoPendenteException("Configuração pendente (D07): defina quais perfis podem autorizar restituições.",
          config.descricoesFinanceiras("D07", "restitui"));
    }
    Restituicao r = restituicoes.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Restituição não encontrada"));
    if (perfis.stream().noneMatch(x -> UsuarioAtual.tem(ator, x))) {
      throw new AccessDeniedException("Seu perfil não está autorizado a aprovar restituições.");
    }
    if (r.getStatus() != StatusRestituicao.SOLICITADA) {
      throw new NegocioException("A restituição não está aguardando autorização.");
    }
    if (r.getSolicitadaPor().getId().equals(ator.getId()) && !UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new NegocioException("O solicitante não pode autorizar a própria restituição.");
    }
    r.setStatus(StatusRestituicao.AUTORIZADA);
    r.setAutorizadaPor(ator);
    r.setAutorizadaEm(relogio.agora());
    auditoria.registrar(AuditoriaTipo.RESTITUICAO_AUTORIZADA, "Restituição #" + r.getId() + " autorizada", "PEDIDO",
        r.getPedido().getId());
    return mapper.view(r);
  }

  /**
   * Efetiva a restituição (única etapa que movimenta dinheiro) na data de hoje ou numa data passada de período aberto.
   * Dinheiro: saída do caixa; Pix: saída do banco; cartão: reduz primeiro os recebíveis ainda previstos e, se já liquidados,
   * lança a saída bancária da parte excedente. Repetir com a mesma chave não duplica.
   */
  @Transactional
  public RestituicaoView efetivar(Long id, LocalDate data, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RESTITUIR);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência da restituição (cabeçalho Idempotency-Key).");
    Long pedidoId = restituicoes.pedidoIdDe(id).orElseThrow(() -> new NotFoundException("Restituição não encontrada"));
    Pedido p = acesso.travar(pedidoId, ator);
    Restituicao r = restituicoes.findByIdForUpdate(id).orElseThrow();
    if (r.getStatus() == StatusRestituicao.EFETIVADA) {
      if (k.equals(r.getChaveEfetivacao())) {
        return mapper.view(r);
      }
      throw new NegocioException("Esta restituição já foi efetivada.");
    }
    if (r.getStatus() != StatusRestituicao.AUTORIZADA) {
      throw new NegocioException("A restituição precisa estar autorizada para ser efetivada.");
    }
    exigirPoliticaRestituicao();
    LocalDate hoje = relogio.hoje();
    LocalDate dia = data == null ? hoje : data;
    if (dia.isAfter(hoje)) {
      throw new NegocioException("A data da restituição não pode ser futura.");
    }
    periodo.exigirAberto(dia);
    BigDecimal recebido = recebimentos.somaAtiva(p.getId());
    BigDecimal efetivado = restituicoes.somaPorPedido(p.getId(), Set.of(StatusRestituicao.EFETIVADA));
    if (r.getValor().compareTo(recebido.subtract(efetivado)) > 0) {
      throw new NegocioException("O valor excede o saldo pago pelo cliente ainda não restituído (R$ "
          + recebido.subtract(efetivado) + ").");
    }

    switch (r.getForma()) {
      case DINHEIRO -> livro.lancar(Lanc.builder().conta(ContaLivro.CAIXA).tipo(TipoLancamento.SAIDA).valor(r.getValor())
          .data(dia).origem(OrigemLancamento.RESTITUICAO).descricao("Restituição em dinheiro #" + r.getId() + " da venda #"
              + p.getId()).pedido(p).restituicao(r).chave("rest:" + k).usuario(ator).build());
      case PIX -> livro.lancar(Lanc.builder().conta(ContaLivro.BANCO).tipo(TipoLancamento.SAIDA).valor(r.getValor())
          .data(dia).origem(OrigemLancamento.RESTITUICAO).descricao("Restituição via Pix #" + r.getId() + " da venda #"
              + p.getId()).pedido(p).restituicao(r).chave("rest:" + k).usuario(ator).build());
      case CARTAO -> restituirCartao(r, p, dia, k, ator);
    }
    r.setStatus(StatusRestituicao.EFETIVADA);
    r.setEfetivadaPor(ator);
    r.setEfetivadaEm(relogio.agora());
    r.setDataEfetiva(dia);
    r.setChaveEfetivacao(k);
    comissoes.aoRestituicao(p, r);
    auditoria.registrar(AuditoriaTipo.RESTITUICAO_EFETIVADA, "Restituição #" + r.getId() + " de R$ " + r.getValor()
        + " efetivada (" + r.getForma() + ")", "PEDIDO", p.getId());
    return mapper.view(r);
  }

  @Transactional
  public RestituicaoView cancelar(Long id, String motivo) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RESTITUIR);
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo do cancelamento da restituição.");
    Restituicao r = restituicoes.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Restituição não encontrada"));
    if (r.getStatus() == StatusRestituicao.CANCELADA) {
      return mapper.view(r);
    }
    if (r.getStatus() == StatusRestituicao.EFETIVADA) {
      throw new NegocioException("Restituição já efetivada não pode ser cancelada.");
    }
    r.setStatus(StatusRestituicao.CANCELADA);
    r.setMotivoCancelamento(mot);
    auditoria.registrar(AuditoriaTipo.RESTITUICAO_CANCELADA, "Restituição #" + r.getId() + " cancelada: " + mot, "PEDIDO",
        r.getPedido().getId());
    return mapper.view(r);
  }

  @Transactional(readOnly = true)
  public List<RestituicaoView> listar(StatusRestituicao status) {
    return restituicoes.listar(status).stream().map(mapper::view).toList();
  }

  @Transactional(readOnly = true)
  public List<RestituicaoView> daOcorrencia(Long ocorrenciaId) {
    return restituicoes.findByOcorrenciaIdOrderByIdDesc(ocorrenciaId).stream().map(mapper::view).toList();
  }

  /**
   * Cobrança da diferença de uma troca (valor calculado positivo): gera uma conta a receber, que é baixada pelo caminho
   * normal. Só com a política D09 definida. Uma conta por ocorrência.
   */
  @Transactional
  public ContaView cobrarDiferenca(Long ocorrenciaId, LocalDate vencimento) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RESTITUIR);
    var cfg = config.obter();
    if (cfg.getPermiteCobrancaDiferenca() == null) {
      throw new ConfiguracaoPendenteException("Configuração pendente (D09): a política de cobrança da diferença de troca não foi definida.",
          config.descricoesFinanceiras("D09", "diferença"));
    }
    if (!cfg.getPermiteCobrancaDiferenca()) {
      throw new NegocioException("A política da loja não permite cobrar a diferença de troca (D09).");
    }
    OcorrenciaPosVenda o = ocorrencias.findById(ocorrenciaId).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada"));
    Pedido p = acesso.travar(o.getPedido().getId(), ator);
    if (o.getTipo() != TipoOcorrencia.TROCA || o.getDiferencaCalculada() == null || o.getDiferencaCalculada().signum() <= 0) {
      throw new NegocioException("Só há diferença a cobrar em troca por item de maior valor.");
    }
    if (o.getDevolucaoRecebidaEm() == null) {
      throw new NegocioException("Receba e avalie a devolução física antes de cobrar a diferença.");
    }
    if (!contas.findByOcorrenciaIdAndOrigemAndSituacaoNot(o.getId(), OrigemConta.DIFERENCA_TROCA, SituacaoConta.CANCELADA).isEmpty()) {
      throw new NegocioException("A diferença desta troca já foi cobrada.");
    }
    LocalDate competencia = Relogio.primeiroDia(relogio.hoje());
    periodo.exigirAberto(competencia);
    ContaFinanceira c = contas.save(ContaFinanceira.builder().tipo(TipoConta.RECEBER)
        .descricao("Diferença da troca (ocorrência #" + o.getId() + ", venda #" + p.getId() + ")").categoria("Diferença de troca")
        .competencia(competencia).valor(o.getDiferencaCalculada().setScale(2, RoundingMode.HALF_UP)).vencimento(vencimento)
        .situacao(SituacaoConta.ABERTA).origem(OrigemConta.DIFERENCA_TROCA).pedido(p).ocorrencia(o).criadoPor(ator).build());
    eventos.save(ContaEvento.builder().conta(c).tipo(TipoEventoConta.CRIADA).motivo("Cobrança da diferença de troca").usuario(ator).build());
    auditoria.registrar(AuditoriaTipo.COBRANCA_DIFERENCA_GERADA, "Cobrança de R$ " + c.getValor() + " gerada para a troca #"
        + o.getId(), "PEDIDO", p.getId());
    return mapper.view(c, true);
  }

  /** Quanto ainda pode ser restituído nesta ocorrência (limite econômico menos o que já está solicitado/efetivado). */
  @Transactional(readOnly = true)
  public BigDecimal restituivel(OcorrenciaPosVenda o) {
    try {
      BigDecimal teto = tetoDaOcorrencia(o).subtract(restituicoes.somaPorOcorrencia(o.getId(), EM_USO));
      BigDecimal disponivel = recebimentos.somaAtiva(o.getPedido().getId())
          .subtract(restituicoes.somaPorPedido(o.getPedido().getId(), EM_USO));
      return teto.min(disponivel).max(BigDecimal.ZERO);
    } catch (NegocioException e) {
      return BigDecimal.ZERO;
    }
  }

  // ---- internos ----

  private void exigirPoliticaRestituicao() {
    Boolean permite = config.obter().getPermiteRestituicao();
    if (permite == null) {
      throw new ConfiguracaoPendenteException("Configuração pendente (D09): a política de restituição ao cliente não foi definida.",
          config.descricoesFinanceiras("D09", "restitui"));
    }
    if (!permite) {
      throw new NegocioException("A política da loja não permite restituir valores ao cliente (D09).");
    }
  }

  /** Limite econômico da ocorrência (não é política): devolução = valor do item devolvido; troca = diferença a favor do cliente. */
  private BigDecimal tetoDaOcorrencia(OcorrenciaPosVenda o) {
    if (o.getTipo() == TipoOcorrencia.DEVOLUCAO) {
      return o.getItem().getPrecoUnitario().multiply(BigDecimal.valueOf(o.getQuantidade()));
    }
    BigDecimal dif = o.getDiferencaCalculada();
    if (dif == null || dif.signum() >= 0) {
      throw new NegocioException("Esta troca não gera valor a restituir (a diferença não é a favor do cliente).");
    }
    return dif.negate();
  }

  private void restituirCartao(Restituicao r, Pedido p, LocalDate dia, String k, User ator) {
    BigDecimal restante = r.getValor();
    for (var parcela : recebiveis.findByPedidoIdAndStatusOrderByParcelaDesc(p.getId(), StatusRecebivel.PREVISTO)) {
      if (restante.signum() <= 0) {
        break;
      }
      BigDecimal tira = parcela.getValorBruto().min(restante);
      BigDecimal novoBruto = parcela.getValorBruto().subtract(tira);
      var arred = config.exigirArredondamento();
      BigDecimal taxa = novoBruto.multiply(parcela.getTaxaPercentual()).divide(BigDecimal.valueOf(100), 2, arred.mode());
      parcela.setValorBruto(novoBruto);
      parcela.setValorTaxa(taxa);
      parcela.setValorLiquido(novoBruto.subtract(taxa));
      if (novoBruto.signum() == 0) {
        parcela.setStatus(StatusRecebivel.CANCELADO);
      }
      restante = restante.subtract(tira);
    }
    if (restante.signum() > 0) {
      // a parte já liquidada pela operadora sai do banco
      livro.lancar(Lanc.builder().conta(ContaLivro.BANCO).tipo(TipoLancamento.SAIDA).valor(restante).data(dia)
          .origem(OrigemLancamento.RESTITUICAO).descricao("Restituição de cartão #" + r.getId() + " da venda #" + p.getId()
              + " (parte já liquidada)").pedido(p).restituicao(r).chave("rest:" + k).usuario(ator).build());
    }
  }
}
