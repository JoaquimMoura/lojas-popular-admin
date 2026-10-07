package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.financeiro.enums.CompetenciaReceita;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.StatusComissao;
import br.com.lojaspopular.domain.financeiro.enums.StatusFechamento;
import br.com.lojaspopular.domain.financeiro.enums.StatusSessaoCaixa;
import br.com.lojaspopular.domain.financeiro.enums.TipoComissao;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.FechamentoMensal;
import br.com.lojaspopular.domain.financeiro.repository.ComissaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.ContaFinanceiraRepository;
import br.com.lojaspopular.domain.financeiro.repository.FechamentoMensalRepository;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebivelCartaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.RestituicaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.SessaoCaixaRepository;
import br.com.lojaspopular.domain.expedicao.repository.EntregaRepository;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.CaixaFechamento;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.FechamentoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.Pendencia;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.Resultado;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.VersaoFechamento;
import lombok.RequiredArgsConstructor;

/**
 * Fechamento mensal. A PRÉVIA está sempre disponível e mostra, SEPARADAS, a visão de CAIXA (entradas e saídas efetivas,
 * caixa físico x banco) e a de RESULTADO (competência). O resultado só vira "lucro apurado" quando não faltam custos nem
 * critérios (D06, D01/D02); do contrário é apresentado como parcial/provisório, com a lista do que falta.
 *
 * <p>Aprovar (proprietário) grava um instantâneo versionado e BLOQUEIA o período: os serviços recusam lançamentos
 * com data no mês (não é só esconder botão). Reabrir exige perfil autorizado (D07) e justificativa; a nova aprovação gera
 * nova versão. A aprovação depende de D10 e só vale para mês encerrado.
 */
@Service
@RequiredArgsConstructor
public class FechamentoService {

  private final FechamentoMensalRepository fechamentos;
  private final LancamentoFinanceiroRepository lancamentos;
  private final PedidoRepository pedidos;
  private final EntregaRepository entregas;
  private final RestituicaoRepository restituicoes;
  private final RecebivelCartaoRepository recebiveis;
  private final ContaFinanceiraRepository contas;
  private final ComissaoRepository comissoes;
  private final SessaoCaixaRepository sessoes;
  private final ConfiguracaoComercialService config;
  private final PeriodoService periodo;
  private final ObjectMapper json;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional(readOnly = true)
  public FechamentoView previa(String mesTexto) {
    LocalDate mes = Relogio.mes(mesTexto);
    return visao(mes, usuarioAtual.get());
  }

  @Transactional
  public FechamentoView aprovar(String mesTexto) {
    User ator = usuarioAtual.get();
    if (!UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new AccessDeniedException("Somente o proprietário aprova o fechamento mensal.");
    }
    LocalDate mes = Relogio.mes(mesTexto);
    var view = visao(mes, ator);
    if (view.fechado()) {
      throw new NegocioException("O período " + view.mes() + " já está fechado (versão " + view.versao()
          + "). Reabra-o, com justificativa, para aprovar uma nova versão.");
    }
    if (!view.podeAprovar()) {
      if (config.obter().getFechamentoExigeSemPendencias() == null) {
        throw new ConfiguracaoPendenteException("Configuração pendente (D10): defina como as pendências são tratadas antes de "
            + "aprovar o fechamento.", config.descricoesFinanceiras("D10", "fechamento"));
      }
      throw new NegocioException("O fechamento não pode ser aprovado: " + String.join(" ", view.bloqueiosAprovacao()));
    }
    int versao = fechamentos.ultimaVersao(mes) + 1;
    String snapshot;
    try {
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("mes", view.mes());
      s.put("versao", versao);
      s.put("caixa", view.caixa());
      s.put("resultado", view.resultado());
      s.put("pendencias", view.pendencias());
      s.put("aprovadoEm", relogio.agora().toString());
      snapshot = json.writeValueAsString(s);
    } catch (Exception e) {
      throw new NegocioException("Não foi possível gerar o instantâneo do fechamento.");
    }
    fechamentos.save(FechamentoMensal.builder().mes(mes).versao(versao).status(StatusFechamento.APROVADO).snapshot(snapshot)
        .aprovadoPor(ator).aprovadoEm(relogio.agora()).build());
    auditoria.registrar(AuditoriaTipo.FECHAMENTO_APROVADO, "Fechamento de " + view.mes() + " aprovado (versão " + versao
        + "); período bloqueado", "FECHAMENTO", null);
    return visao(mes, ator);
  }

  @Transactional
  public FechamentoView reabrir(String mesTexto, String justificativa) {
    User ator = usuarioAtual.get();
    Set<Role> perfis = config.perfis(config.obter().getPerfisReabertura());
    if (perfis.isEmpty()) {
      throw new ConfiguracaoPendenteException("Configuração pendente (D07): defina quais perfis podem reabrir um período fechado.",
          config.descricoesFinanceiras("D07", "reabertura"));
    }
    if (perfis.stream().noneMatch(r -> UsuarioAtual.tem(ator, r))) {
      throw new AccessDeniedException("Seu perfil não está autorizado a reabrir períodos fechados.");
    }
    String just = VendaAcessoTexto.exigir(justificativa, "Informe a justificativa da reabertura.");
    LocalDate mes = Relogio.mes(mesTexto);
    FechamentoMensal f = fechamentos.findByMesAndStatus(mes, StatusFechamento.APROVADO)
        .orElseThrow(() -> new NegocioException("O período " + Relogio.texto(mes) + " não está fechado."));
    f.setStatus(StatusFechamento.REABERTO);
    f.setReabertoPor(ator);
    f.setReabertoEm(relogio.agora());
    f.setJustificativaReabertura(just);
    auditoria.registrar(AuditoriaTipo.FECHAMENTO_REABERTO, "Fechamento de " + Relogio.texto(mes) + " (versão " + f.getVersao()
        + ") reaberto: " + just, "FECHAMENTO", f.getId());
    return visao(mes, ator);
  }

  // ================================================================== montagem da visão

  private FechamentoView visao(LocalDate mes, User ator) {
    LocalDate fim = mes.plusMonths(1).minusDays(1);
    var caixa = caixa(mes, fim);
    var resultado = resultado(mes, fim);
    var pendencias = pendencias(mes, fim, resultado);

    var versoes = fechamentos.findByMesOrderByVersaoDesc(mes).stream().map(f -> new VersaoFechamento(f.getVersao(), f.getStatus(),
        f.getAprovadoEm(), FinanceiroMapper.nome(f.getAprovadoPor()), f.getReabertoEm(), FinanceiroMapper.nome(f.getReabertoPor()),
        f.getJustificativaReabertura())).toList();
    boolean fechado = fechamentos.existsByMesAndStatus(mes, StatusFechamento.APROVADO);
    Integer versao = versoes.stream().findFirst().map(VersaoFechamento::versao).orElse(null);
    StatusFechamento ultimo = versoes.stream().findFirst().map(VersaoFechamento::status).orElse(null);

    var cfg = config.obter();
    List<String> bloqueios = new ArrayList<>();
    if (fechado) {
      bloqueios.add("Período já fechado.");
    }
    if (!UsuarioAtual.tem(ator, Role.ADMIN)) {
      bloqueios.add("Somente o proprietário aprova o fechamento.");
    }
    if (!mes.isBefore(Relogio.primeiroDia(relogio.hoje()))) {
      bloqueios.add("O mês ainda não terminou.");
    }
    if (cfg.getFechamentoExigeSemPendencias() == null) {
      bloqueios.add("Configuração pendente (D10): a regra de aprovação do fechamento não foi definida.");
    } else if (cfg.getFechamentoExigeSemPendencias()) {
      long n = pendencias.stream().filter(Pendencia::bloqueante).count();
      if (n > 0) {
        bloqueios.add("Há " + n + " tipo(s) de pendência bloqueante (veja a lista).");
      }
    }
    Set<Role> perfisReabertura = config.perfis(cfg.getPerfisReabertura());
    List<String> bloqueiosReabertura = new ArrayList<>();
    if (!fechado) {
      bloqueiosReabertura.add("O período não está fechado.");
    }
    if (perfisReabertura.isEmpty()) {
      bloqueiosReabertura.add("Configuração pendente (D07): perfis autorizados a reabrir não definidos.");
    } else if (perfisReabertura.stream().noneMatch(r -> UsuarioAtual.tem(ator, r))) {
      bloqueiosReabertura.add("Seu perfil não está autorizado a reabrir períodos.");
    }
    return new FechamentoView(Relogio.texto(mes), fechado, versao, ultimo, caixa, resultado, pendencias, bloqueios.isEmpty(),
        bloqueios, bloqueiosReabertura.isEmpty(), bloqueiosReabertura, versoes);
  }

  /** Visão de CAIXA: o que entrou e saiu de fato, caixa físico e banco separados (não é resultado). */
  private CaixaFechamento caixa(LocalDate de, LocalDate ate) {
    BigDecimal ec = BigDecimal.ZERO, sc = BigDecimal.ZERO, eb = BigDecimal.ZERO, sb = BigDecimal.ZERO;
    Map<String, BigDecimal> porOrigem = new LinkedHashMap<>();
    for (Object[] l : lancamentos.totaisDoPeriodo(de, ate)) {
      ContaLivro conta = (ContaLivro) l[0];
      TipoLancamento tipo = (TipoLancamento) l[2];
      BigDecimal v = (BigDecimal) l[3];
      porOrigem.put(conta + ":" + l[1] + ":" + tipo, v);
      if (conta == ContaLivro.CAIXA) {
        if (tipo == TipoLancamento.ENTRADA) ec = ec.add(v); else sc = sc.add(v);
      } else {
        if (tipo == TipoLancamento.ENTRADA) eb = eb.add(v); else sb = sb.add(v);
      }
    }
    return new CaixaFechamento(ec, sc, ec.subtract(sc), eb, sb, eb.subtract(sb), porOrigem);
  }

  /** Visão de RESULTADO (competência). Só vira lucro quando nada falta. */
  private Resultado resultado(LocalDate mes, LocalDate fim) {
    var cfg = config.obter();
    CompetenciaReceita criterio = cfg.getCompetenciaReceita();
    var de = relogio.inicioDoDia(mes);
    var ate = relogio.fimDoMes(mes);
    List<Pedido> vendas = criterio == CompetenciaReceita.ENTREGA
        ? entregas.concluidasNoPeriodo(de, ate).stream().map(e -> e.getPedido()).distinct().toList()
        : pedidos.confirmadasNoPeriodo(de, ate);

    BigDecimal receita = vendas.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal custos = BigDecimal.ZERO;
    long semCusto = 0;
    for (Pedido p : vendas) {
      for (ItemPedido i : p.getItens()) {
        if (i.getCustoUnitario() == null) {
          semCusto++;
        } else {
          custos = custos.add(i.getCustoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())));
        }
      }
    }
    BigDecimal rest = restituicoes.somaEfetivadaNoPeriodo(mes, fim);
    BigDecimal taxas = recebiveis.somaTaxasDoPeriodo(mes, fim);
    BigDecimal despesas = contas.somaDespesasDaCompetencia(mes, fim);
    BigDecimal comissao = comissoes.somaDaCompetencia(mes, fim);
    var ids = vendas.stream().map(Pedido::getId).toList();
    BigDecimal previstas = ids.isEmpty() ? BigDecimal.ZERO : comissoes.previsoesComStatus(Set.of(StatusComissao.PREVISTA)).stream()
        .filter(c -> c.getTipo() == TipoComissao.PREVISAO && ids.contains(c.getPedido().getId()))
        .map(c -> c.getValor()).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal parcial = receita.subtract(rest).subtract(taxas).subtract(despesas).subtract(comissao).subtract(custos);

    List<String> faltantes = new ArrayList<>();
    if (criterio == null) {
      faltantes.add("D06: critério de competência da receita não definido (usada a data de confirmação, apenas de forma provisória).");
    }
    if (semCusto > 0) {
      faltantes.add(semCusto + " item(ns) vendido(s) sem custo histórico: cadastre o custo do produto (vale para vendas futuras) ou informe o custo do item vendido, com motivo. Custos ausentes nunca são tratados como zero.");
    }
    if (cfg.getComissaoPercentual() == null) {
      faltantes.add("D01: percentual de comissão não definido (comissões não apuradas).");
    }
    if (cfg.getComissaoAquisicao() == null) {
      faltantes.add("D02: momento de aquisição da comissão não definido (comissões permanecem previsão).");
    }
    if (previstas.signum() > 0) {
      faltantes.add("Há comissões apenas previstas (R$ " + previstas + ") ainda não apuradas como devidas.");
    }
    boolean definitivo = faltantes.isEmpty();
    return new Resultado(receita, rest, taxas, despesas, comissao, previstas, custos, semCusto, parcial,
        definitivo ? parcial : null, definitivo, criterio == null ? "PROVISORIO_CONFIRMACAO" : criterio.name(), faltantes);
  }

  private List<Pendencia> pendencias(LocalDate mes, LocalDate fim, Resultado resultado) {
    List<Pendencia> p = new ArrayList<>();
    long caixasAbertos = sessoes.findFirstByStatus(StatusSessaoCaixa.ABERTA).filter(s -> !s.getDataReferencia().isAfter(fim)).isPresent() ? 1 : 0;
    p.add(new Pendencia("CAIXA_ABERTO", "Caixa físico aberto (precisa ser contado e fechado).", caixasAbertos, true));
    p.add(new Pendencia("RECEBIVEL_VENCIDO", "Recebíveis de cartão previstos até o fim do mês e ainda não liquidados.",
        recebiveis.contarVencidosNaoLiquidados(fim.plusDays(1)), true));
    p.add(new Pendencia("CONTA_VENCIDA", "Contas em aberto vencidas até o fim do mês.", contas.contarVencidasAbertas(fim.plusDays(1)), true));
    p.add(new Pendencia("RESTITUICAO_EM_ABERTO", "Restituições solicitadas/autorizadas e ainda não efetivadas.",
        restituicoes.contarEmAberto(), true));
    p.add(new Pendencia("DESCONTO_AGUARDANDO", "Vendas aguardando aprovação de desconto.", pedidos.contarAguardandoAprovacao(), false));
    p.add(new Pendencia("VENDA_NAO_QUITADA", "Vendas confirmadas até o fim do mês sem pagamento quitado.",
        pedidos.contarConfirmadasNaoQuitadas(relogio.fimDoMes(mes)), false));
    p.add(new Pendencia("RESULTADO_INCOMPLETO", "Itens do resultado sem definição/dados (veja o resultado).",
        resultado.faltantes().size(), false));
    return p.stream().filter(x -> x.quantidade() > 0).toList();
  }

  /** Auxiliar local: texto obrigatório. */
  private static final class VendaAcessoTexto {
    static String exigir(String v, String msg) {
      if (v == null || v.isBlank()) {
        throw new NegocioException(msg);
      }
      return v.trim();
    }
  }
}
