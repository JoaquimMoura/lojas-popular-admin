package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.comercial.model.ConfiguracaoComercial;
import br.com.lojaspopular.domain.financeiro.enums.OrigemConta;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoConta;
import br.com.lojaspopular.domain.financeiro.enums.TipoEventoConta;
import br.com.lojaspopular.domain.financeiro.model.Comissao;
import br.com.lojaspopular.domain.financeiro.model.ContaEvento;
import br.com.lojaspopular.domain.financeiro.model.ContaFinanceira;
import br.com.lojaspopular.domain.financeiro.model.Restituicao;
import br.com.lojaspopular.domain.financeiro.repository.ComissaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.ContaEventoRepository;
import br.com.lojaspopular.domain.financeiro.repository.ContaFinanceiraRepository;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoResumo;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ContaView;
import lombok.RequiredArgsConstructor;

/**
 * Comissões: um percentual comum sobre o TOTAL COBRADO da venda (D01), sem assumir valor, momento de aquisição (D02)
 * ou data de pagamento.
 *
 * <ul>
 * <li>Sem D01 definido nada é calculado: nem zero, nem estimativa;</li>
 * <li>PREVISTA (previsão) vira DEVIDA só no evento definido em D02 (confirmação, quitação ou entrega); sem D02, permanece
 * previsão e a apuração final é impedida;</li>
 * <li>o percentual fica gravado no lançamento (regra histórica por venda): mudar a regra depois não altera o que já existe;</li>
 * <li>pagar = gerar uma conta a pagar com vencimento informado pelo proprietário (a data de pagamento não é presumida);</li>
 * <li>cancelamento e restituição geram REVERSÃO negativa vinculada, inclusive depois do pagamento (compensada no próximo pagamento).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ComissaoService {

  private final ComissaoRepository repo;
  private final ContaFinanceiraRepository contas;
  private final ContaEventoRepository eventos;
  private final PedidoRepository pedidos;
  private final UserRepository users;
  private final ConfiguracaoComercialService config;
  private final PeriodoService periodo;
  private final FinanceiroMapper mapper;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  // ================================================================== ganchos (dentro das transações da venda)

  public void aoConfirmar(Pedido p) {
    criarPrevisaoSePossivel(p, "Previsão criada na confirmação da venda");
    reavaliar(p);
  }

  /** Reavalia a aquisição (pagamento quitado/estornado, entrega, confirmação) de uma venda. */
  public void reavaliar(Pedido p) {
    repo.previsaoDoPedidoParaAtualizar(p.getId()).ifPresent(c -> avaliarAquisicao(p, c));
  }

  public void aoCancelar(Pedido p) {
    repo.previsaoDoPedidoParaAtualizar(p.getId()).ifPresent(prev -> {
      switch (prev.getStatus()) {
        case PREVISTA, DEVIDA -> {
          prev.setStatus(StatusComissao.REVERTIDA);
          reversao(prev, prev.getBase(), prev.getValor().negate(), StatusComissao.COMPENSADA, null,
              "Venda #" + p.getId() + " cancelada: comissão revertida antes de qualquer pagamento");
        }
        case EM_CONTA, PAGA -> reversao(prev, prev.getBase(), prev.getValor().negate(), StatusComissao.LANCADA, null,
            "Venda #" + p.getId() + " cancelada depois de a comissão entrar em pagamento: reversão a compensar");
        default -> {
        }
      }
      auditoria.registrar(AuditoriaTipo.COMISSAO_REVERTIDA, "Comissão da venda #" + p.getId() + " revertida por cancelamento",
          "PEDIDO", p.getId());
    });
  }

  /** Restituição efetivada: reverte a comissão proporcional ao valor devolvido ao cliente. */
  public void aoRestituicao(Pedido p, Restituicao r) {
    repo.previsaoDoPedidoParaAtualizar(p.getId()).ifPresent(prev -> {
      if (prev.getStatus() == StatusComissao.REVERTIDA) {
        return;
      }
      var arred = config.exigirArredondamento();
      BigDecimal valor = r.getValor().multiply(prev.getPercentual()).divide(BigDecimal.valueOf(100), 2, arred.mode()).negate();
      if (valor.signum() == 0) {
        return;
      }
      var rev = reversao(prev, r.getValor(), valor, StatusComissao.LANCADA, r,
          "Restituição #" + r.getId() + " de R$ " + r.getValor() + ": comissão proporcional revertida");
      auditoria.registrar(AuditoriaTipo.COMISSAO_REVERTIDA, "Reversão de R$ " + rev.getValor().abs() + " na comissão da venda #"
          + p.getId() + " por restituição", "PEDIDO", p.getId());
    });
  }

  public void aoContaPaga(ContaFinanceira conta) {
    for (Comissao c : repo.findByContaId(conta.getId())) {
      c.setStatus(c.getTipo() == TipoComissao.PREVISAO ? StatusComissao.PAGA : StatusComissao.COMPENSADA);
    }
  }

  public void aoContaEstornada(ContaFinanceira conta) {
    for (Comissao c : repo.findByContaId(conta.getId())) {
      c.setStatus(StatusComissao.EM_CONTA);
    }
  }

  public void aoContaCancelada(ContaFinanceira conta) {
    for (Comissao c : repo.findByContaId(conta.getId())) {
      c.setConta(null);
      c.setStatus(c.getTipo() == TipoComissao.PREVISAO ? StatusComissao.DEVIDA : StatusComissao.LANCADA);
    }
  }

  // ================================================================== ações do proprietário

  /**
   * Gera as previsões de vendas confirmadas que ainda não têm (ex.: anteriores à definição de D01), com o percentual
   * VIGENTE agora, e reavalia a aquisição conforme D02. Ação explícita: nada é retroativo sem o proprietário pedir.
   */
  @Transactional
  public ComissaoResumo gerarPrevisoes() {
    User ator = usuarioAtual.get();
    exigirProprietario(ator);
    var cfg = config.obter();
    if (cfg.getComissaoPercentual() == null) {
      throw new ConfiguracaoPendenteException("Configuração pendente (D01): defina o percentual de comissão antes de gerar previsões.",
          config.descricoesFinanceiras("D01", "comiss"));
    }
    int criadas = 0;
    for (Pedido p : pedidos.confirmadasSemComissao()) {
      if (criarPrevisaoSePossivel(p, "Previsão gerada em " + relogio.hoje() + " com o percentual vigente nesta data")) {
        criadas++;
      }
    }
    for (Comissao c : repo.previsoesComStatus(List.of(StatusComissao.PREVISTA, StatusComissao.DEVIDA))) {
      avaliarAquisicao(c.getPedido(), c);
    }
    auditoria.registrar(AuditoriaTipo.COMISSAO_PREVISTA, "Previsões de comissão geradas: " + criadas, "COMISSAO", null);
    return resumo(null, null);
  }

  /**
   * Gera a conta a pagar das comissões devidas de um vendedor (líquidas das reversões lançadas). O vencimento é
   * informado: o sistema não presume a data de pagamento.
   */
  @Transactional
  public ContaView gerarPagamento(Long vendedorId, LocalDate vencimento) {
    User ator = usuarioAtual.get();
    exigirProprietario(ator);
    User vendedor = users.findById(vendedorId).orElseThrow(() -> new NegocioException("Vendedor não encontrado."));
    var itens = repo.aPagar(vendedorId);
    if (itens.stream().noneMatch(i -> i.getTipo() == TipoComissao.PREVISAO)) {
      throw new NegocioException("Não há comissões devidas para este vendedor (previsões ainda não adquiridas não são pagas).");
    }
    BigDecimal liquido = itens.stream().map(Comissao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (liquido.signum() <= 0) {
      throw new NegocioException("O saldo líquido do vendedor é R$ " + liquido + ": as reversões superam as comissões devidas e "
          + "permanecem para compensação futura.");
    }
    LocalDate competencia = Relogio.primeiroDia(relogio.hoje());
    periodo.exigirAberto(competencia);
    ContaFinanceira conta = contas.save(ContaFinanceira.builder().tipo(TipoConta.PAGAR)
        .descricao("Comissões de " + FinanceiroMapper.nome(vendedor) + " (" + itens.size() + " lançamento(s))")
        .categoria("Comissão").competencia(competencia).valor(liquido.setScale(2, RoundingMode.HALF_UP))
        .vencimento(vencimento).situacao(SituacaoConta.ABERTA).origem(OrigemConta.COMISSAO).vendedor(vendedor)
        .criadoPor(ator).build());
    eventos.save(ContaEvento.builder().conta(conta).tipo(TipoEventoConta.CRIADA).motivo("Pagamento de comissões").usuario(ator).build());
    for (Comissao c : itens) {
      c.setStatus(StatusComissao.EM_CONTA);
      c.setConta(conta);
    }
    auditoria.registrar(AuditoriaTipo.COMISSAO_PAGAMENTO_GERADO, "Conta #" + conta.getId() + " gerada para as comissões de "
        + FinanceiroMapper.nome(vendedor) + ": R$ " + conta.getValor(), "CONTA", conta.getId());
    return mapper.view(conta, true);
  }

  // ================================================================== consultas

  @Transactional(readOnly = true)
  public ComissaoResumo listar(Long vendedorId, StatusComissao status) {
    return resumo(vendedorId, status);
  }

  /** Comissões do próprio vendedor autenticado (somente leitura). */
  @Transactional(readOnly = true)
  public ComissaoResumo minhas() {
    return resumo(usuarioAtual.get().getId(), null);
  }

  // ================================================================== internos

  private ComissaoResumo resumo(Long vendedorId, StatusComissao status) {
    var cfg = config.obter();
    List<String> avisos = new ArrayList<>();
    if (cfg.getComissaoPercentual() == null) {
      avisos.add("D01 pendente: o percentual de comissão não foi definido; nenhuma comissão é calculada (nem zero, nem estimada).");
    }
    if (cfg.getComissaoAquisicao() == null) {
      avisos.add("D02 pendente: sem o momento de aquisição, as comissões permanecem como previsão e a apuração final fica bloqueada.");
    }
    var itens = repo.listar(vendedorId, status);
    BigDecimal previstas = soma(itens, TipoComissao.PREVISAO, StatusComissao.PREVISTA);
    BigDecimal devidas = soma(itens, TipoComissao.PREVISAO, StatusComissao.DEVIDA);
    BigDecimal pagas = soma(itens, TipoComissao.PREVISAO, StatusComissao.PAGA);
    BigDecimal emConta = itens.stream().filter(i -> i.getStatus() == StatusComissao.EM_CONTA).map(Comissao::getValor)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal reversoes = soma(itens, TipoComissao.REVERSAO, StatusComissao.LANCADA);
    return new ComissaoResumo(cfg.getComissaoPercentual() != null, cfg.getComissaoAquisicao() != null, avisos, previstas,
        devidas, emConta, pagas, reversoes, devidas.add(reversoes), itens.stream().map(mapper::view).toList());
  }

  private static BigDecimal soma(List<Comissao> itens, TipoComissao tipo, StatusComissao status) {
    return itens.stream().filter(i -> i.getTipo() == tipo && i.getStatus() == status).map(Comissao::getValor)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean criarPrevisaoSePossivel(Pedido p, String motivo) {
    ConfiguracaoComercial cfg = config.obter();
    if (cfg.getComissaoPercentual() == null || p.getVendedor() == null || p.getTotal() == null) {
      return false;
    }
    if (repo.findFirstByPedidoIdAndTipo(p.getId(), TipoComissao.PREVISAO).isPresent()) {
      return false;
    }
    var arred = config.exigirArredondamento();
    BigDecimal base = p.getTotal();
    BigDecimal valor = base.multiply(cfg.getComissaoPercentual()).divide(BigDecimal.valueOf(100), 2, arred.mode());
    repo.save(Comissao.builder().pedido(p).vendedor(p.getVendedor()).tipo(TipoComissao.PREVISAO)
        .status(StatusComissao.PREVISTA).base(base).percentual(cfg.getComissaoPercentual()).valor(valor).motivo(motivo)
        .build());
    auditoria.registrar(AuditoriaTipo.COMISSAO_PREVISTA, "Comissão prevista da venda #" + p.getId() + ": R$ " + valor + " ("
        + cfg.getComissaoPercentual() + "% sobre R$ " + base + ")", "PEDIDO", p.getId());
    return true;
  }

  private void avaliarAquisicao(Pedido p, Comissao c) {
    var aquisicao = config.obter().getComissaoAquisicao();
    if (aquisicao == null) {
      return;   // D02 pendente: continua previsão
    }
    boolean condicao = switch (aquisicao) {
      case CONFIRMACAO -> p.getStatusComercial() == StatusComercial.CONFIRMADA;
      case QUITACAO -> p.getStatusPagamento() == StatusPagamento.PAGO;
      case ENTREGA -> p.getStatusEntrega() == StatusEntrega.ENTREGUE;
    };
    if (c.getStatus() == StatusComissao.PREVISTA && condicao) {
      LocalDate hoje = relogio.hoje();
      c.setStatus(StatusComissao.DEVIDA);
      c.setAdquiridaEm(hoje);
      c.setCompetencia(Relogio.primeiroDia(hoje));
      auditoria.registrar(AuditoriaTipo.COMISSAO_DEVIDA, "Comissão da venda #" + p.getId() + " passou a devida (" + aquisicao
          + "): R$ " + c.getValor(), "PEDIDO", p.getId());
    } else if (c.getStatus() == StatusComissao.DEVIDA && !condicao && c.getConta() == null) {
      c.setStatus(StatusComissao.PREVISTA);
      c.setAdquiridaEm(null);
      c.setCompetencia(null);
    }
  }

  private Comissao reversao(Comissao prev, BigDecimal base, BigDecimal valor, StatusComissao status, Restituicao rest,
      String motivo) {
    return repo.save(Comissao.builder().pedido(prev.getPedido()).vendedor(prev.getVendedor()).tipo(TipoComissao.REVERSAO)
        .status(status).base(base).percentual(prev.getPercentual()).valor(valor).reverte(prev).restituicao(rest)
        .competencia(Relogio.primeiroDia(relogio.hoje())).adquiridaEm(relogio.hoje()).motivo(motivo).build());
  }

  private void exigirProprietario(User ator) {
    if (!UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new org.springframework.security.access.AccessDeniedException("Somente o proprietário gerencia comissões.");
    }
  }
}
