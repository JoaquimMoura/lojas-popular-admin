package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.expedicao.repository.EntregaRepository;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.SituacaoConta;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebimento;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebimentoRepository;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.ComissaoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.MetaView;
import lombok.RequiredArgsConstructor;

/**
 * Relatórios gerenciais (somente leitura). Regra de ouro: <b>venda realizada ≠ dinheiro recebido</b>. As vendas contam
 * pela confirmação (nenhuma competência definitiva: D06); recebimentos, recebíveis de cartão e caixa/banco são
 * apresentados em blocos próprios. Margem só aparece quando todos os itens do grupo têm custo histórico; nunca é "lucro".
 */
@Service
@RequiredArgsConstructor
public class RelatorioService {

  public static final String AVISO_VENDAS = "Vendas realizadas (confirmadas e não canceladas, pela data de confirmação) não são "
      + "dinheiro recebido: veja o relatório de recebimentos. A competência definitiva da receita depende da D06.";

  public record LinhaVenda(String chave, long vendas, BigDecimal totalVendido, BigDecimal ticketMedio, long unidades,
      BigDecimal custoConhecido, long itensSemCusto, boolean margemCompleta, BigDecimal margemBrutaItens, long vendasLegadas) {
  }

  public record Cobertura(long vendasGestao, long legadasIncluidas, BigDecimal totalLegadoIncluido, long legadasNaoPagasExcluidas,
      BigDecimal totalLegadoNaoPagoExcluido, long legadasCanceladasExcluidas, String observacao) {
  }

  public record RelatorioVendas(LocalDate de, LocalDate ate, String agrupadoPor, String aviso, List<LinhaVenda> linhas,
      LinhaVenda total, Cobertura cobertura) {
  }

  public record LinhaRecebimento(String chave, long recebimentos, BigDecimal registrado, BigDecimal estornado, BigDecimal liquido) {
  }

  public record RelatorioRecebimentos(LocalDate de, LocalDate ate, String aviso, List<LinhaRecebimento> pagamentosDoCliente,
      Map<String, BigDecimal> entradaEfetivaPorConta, Map<String, BigDecimal> saidaEfetivaPorConta,
      BigDecimal cartaoPrevistoBruto, BigDecimal cartaoPrevistoTaxa, BigDecimal cartaoPrevistoLiquido, long cartaoPrevistoParcelas,
      BigDecimal cartaoLiquidadoValor, long cartaoLiquidadoParcelas, String cobertura) {
  }

  public record GrupoConta(String tipo, long contas, BigDecimal total, long vencidas, BigDecimal totalVencido, long vencemEm7Dias,
      BigDecimal totalEm7Dias) {
  }

  public record RelatorioContas(LocalDate referencia, List<GrupoConta> grupos) {
  }

  public record RelatorioEstoque(long itens, long semSaldo, long indisponiveis, long unidadesFisicas, long unidadesReservadas,
      List<EstoqueService.Saldo> saldos) {
  }

  public record EntregaAtrasada(Long pedidoId, LocalDate dataPrevista, String equipe, String status) {
  }

  public record RelatorioEntregas(LocalDate de, LocalDate ate, Map<String, Long> pedidosPorStatusEntrega,
      Map<String, Long> pedidosPorStatusMontagem, Map<String, Long> agendadasNoPeriodoPorStatus, long concluidasNoPeriodo,
      List<EntregaAtrasada> atrasadas) {
  }

  public record LinhaComissao(Long vendedorId, String vendedor, BigDecimal previstas, BigDecimal devidas, BigDecimal emConta,
      BigDecimal pagas, BigDecimal reversoes) {
  }

  public record RelatorioComissoes(LocalDate de, LocalDate ate, boolean percentualDefinido, boolean aquisicaoDefinida,
      List<String> avisos, List<LinhaComissao> linhas) {
  }

  public record RelatorioMetas(String mes, List<MetaView> metas, BigDecimal totalMeta, BigDecimal totalVendido) {
  }

  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final PedidoRepository pedidos;
  private final RecebimentoRepository recebimentos;
  private final LancamentoFinanceiroRepository lancamentos;
  private final br.com.lojaspopular.domain.financeiro.repository.RecebivelCartaoRepository recebiveis;
  private final ContaFinanceiraService contas;
  private final EstoqueService estoque;
  private final EntregaRepository entregas;
  private final ComissaoService comissoes;
  private final MetaService metas;
  private final UsuarioAtual usuarioAtual;
  private final Relogio relogio;

  // ------------------------------------------------------------------ vendas realizadas

  @Transactional(readOnly = true)
  public RelatorioVendas vendas(LocalDate de, LocalDate ate, String agrupar) {
    gestor();
    LocalDate[] p = periodo(de, ate);
    String por = agrupar == null || agrupar.isBlank() ? "VENDEDOR" : agrupar.trim().toUpperCase();
    if (!List.of("VENDEDOR", "CANAL", "DIA", "MES").contains(por)) {
      throw new NegocioException("Agrupamento inválido. Use VENDEDOR, CANAL, DIA ou MES.");
    }
    var inicio = relogio.inicioDoDia(p[0]);
    var fim = relogio.inicioDoDia(p[1].plusDays(1));
    List<Pedido> lista = new ArrayList<>(pedidos.confirmadasNoPeriodo(inicio, fim));
    long vendasGestao = lista.size();
    // Vendas legadas (checkout online anterior): entram quando pagas/entregues, com vendedor e canal DESCONHECIDOS
    // (nunca inventados) e data = criação do pedido. Não pagas e canceladas ficam de fora, mas são contadas na cobertura.
    var legados = pedidos.legadosNoPeriodo(inicio, fim);
    var legPagos = legados.stream().filter(x -> x.getStatus() == br.com.lojaspopular.domain.catalog.enums.PedidoStatus.PAGO
        || x.getStatus() == br.com.lojaspopular.domain.catalog.enums.PedidoStatus.ENTREGUE).toList();
    var legNaoPagos = legados.stream().filter(x -> x.getStatus() == br.com.lojaspopular.domain.catalog.enums.PedidoStatus.CRIADO).toList();
    long legCancel = legados.stream().filter(x -> x.getStatus() == br.com.lojaspopular.domain.catalog.enums.PedidoStatus.CANCELADO).count();
    lista.addAll(legPagos);
    Map<String, List<Pedido>> grupos = new TreeMap<>();
    for (Pedido ped : lista) {
      boolean legado = ped.getStatusComercial() == br.com.lojaspopular.domain.order.enums.StatusComercial.LEGADO;
      var dia = (legado ? ped.getCriadoEm() : ped.getConfirmadoEm()).atZone(relogio.zona()).toLocalDate();
      String k = switch (por) {
        case "VENDEDOR" -> ped.getVendedor() != null ? FinanceiroMapper.nome(ped.getVendedor())
            : legado ? "(vendedor desconhecido — venda legada)" : "(sem vendedor)";
        case "CANAL" -> ped.getCanal() != null ? ped.getCanal().name()
            : legado ? "LEGADO_ONLINE (canal desconhecido)" : "(sem canal)";
        case "DIA" -> dia.toString();
        default -> dia.toString().substring(0, 7);
      };
      grupos.computeIfAbsent(k, x -> new ArrayList<>()).add(ped);
    }
    List<LinhaVenda> linhas = grupos.entrySet().stream().map(e -> linha(e.getKey(), e.getValue())).toList();
    BigDecimal totLeg = legPagos.stream().map(x -> x.getTotal() == null ? BigDecimal.ZERO : x.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totNaoPago = legNaoPagos.stream().map(x -> x.getTotal() == null ? BigDecimal.ZERO : x.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
    String obs = "Cobertura: " + vendasGestao + " venda(s) da gestão (data de confirmação) + " + legPagos.size()
        + " venda(s) legada(s) do checkout online pagas/entregues (data de criação do pedido; vendedor e canal desconhecidos, sem custo). "
        + "Fora do total: " + legNaoPagos.size() + " pedido(s) legado(s) não pago(s) (R$ " + totNaoPago + ") e " + legCancel
        + " cancelado(s).";
    return new RelatorioVendas(p[0], p[1], por, AVISO_VENDAS, linhas, linha("TOTAL", lista),
        new Cobertura(vendasGestao, legPagos.size(), totLeg, legNaoPagos.size(), totNaoPago, legCancel, obs));
  }

  private LinhaVenda linha(String chave, List<Pedido> lista) {
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal custo = BigDecimal.ZERO;
    BigDecimal vendidoComCusto = BigDecimal.ZERO;
    long unidades = 0;
    long semCusto = 0;
    for (Pedido ped : lista) {
      total = total.add(ped.getTotal() == null ? BigDecimal.ZERO : ped.getTotal());
      for (ItemPedido i : ped.getItens()) {
        unidades += i.getQuantidade();
        if (i.getCustoUnitario() == null) {
          semCusto++;
        } else {
          custo = custo.add(i.getCustoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())));
          vendidoComCusto = vendidoComCusto.add(i.getTotal() == null ? BigDecimal.ZERO : i.getTotal());
        }
      }
    }
    long legadas = lista.stream().filter(x -> x.getStatusComercial() == br.com.lojaspopular.domain.order.enums.StatusComercial.LEGADO).count();
    boolean completa = !lista.isEmpty() && semCusto == 0;
    BigDecimal ticket = lista.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(lista.size()), 2, RoundingMode.HALF_UP);
    return new LinhaVenda(chave, lista.size(), total, ticket, unidades, custo, semCusto, completa,
        completa ? vendidoComCusto.subtract(custo) : null, legadas);
  }

  // ------------------------------------------------------------------ dinheiro recebido

  @Transactional(readOnly = true)
  public RelatorioRecebimentos recebimentos(LocalDate de, LocalDate ate) {
    gestor();
    LocalDate[] p = periodo(de, ate);
    Map<String, BigDecimal[]> acc = new LinkedHashMap<>();
    long[] n = new long[1];
    Map<String, long[]> cont = new LinkedHashMap<>();
    for (var r : recebimentos.findByDataPagamentoBetweenOrderByIdAsc(p[0], p[1])) {
      String k = r.getForma().name();
      BigDecimal[] v = acc.computeIfAbsent(k, x -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
      cont.computeIfAbsent(k, x -> new long[1])[0]++;
      if (r.getStatus() == StatusRecebimento.ESTORNADO) {
        v[1] = v[1].add(r.getValor());
      } else {
        v[0] = v[0].add(r.getValor());
      }
    }
    List<LinhaRecebimento> pag = acc.entrySet().stream().map(e -> new LinhaRecebimento(e.getKey(), cont.get(e.getKey())[0],
        e.getValue()[0], e.getValue()[1], e.getValue()[0])).toList();

    Map<String, BigDecimal> entradas = new LinkedHashMap<>();
    Map<String, BigDecimal> saidas = new LinkedHashMap<>();
    for (var l : lancamentos.periodo(p[0], p[1], "", null)) {
      (l.getTipo() == TipoLancamento.ENTRADA ? entradas : saidas).merge(l.getConta().name(), l.getValor(), BigDecimal::add);
    }
    for (ContaLivro c : ContaLivro.values()) {
      entradas.putIfAbsent(c.name(), BigDecimal.ZERO);
      saidas.putIfAbsent(c.name(), BigDecimal.ZERO);
    }

    BigDecimal bruto = BigDecimal.ZERO;
    BigDecimal taxa = BigDecimal.ZERO;
    BigDecimal liquido = BigDecimal.ZERO;
    long prevN = 0;
    BigDecimal liquidado = BigDecimal.ZERO;
    long liqN = 0;
    for (var r : recebiveis.listar(null, "", null, null)) {
      if (r.getStatus() == StatusRecebivel.PREVISTO && !r.getDataPrevista().isBefore(p[0]) && !r.getDataPrevista().isAfter(p[1])) {
        bruto = bruto.add(r.getValorBruto());
        taxa = taxa.add(r.getValorTaxa());
        liquido = liquido.add(r.getValorLiquido());
        prevN++;
      }
      if (r.getStatus() == StatusRecebivel.LIQUIDADO && r.getDataLiquidacao() != null && !r.getDataLiquidacao().isBefore(p[0])
          && !r.getDataLiquidacao().isAfter(p[1])) {
        liquidado = liquidado.add(r.getValorLiquidado() == null ? r.getValorLiquido() : r.getValorLiquidado());
        liqN++;
      }
    }
    var legPagosPeriodo = pedidos.legadosNoPeriodo(relogio.inicioDoDia(p[0]), relogio.inicioDoDia(p[1].plusDays(1))).stream()
        .filter(x -> x.getStatus() == br.com.lojaspopular.domain.catalog.enums.PedidoStatus.PAGO
            || x.getStatus() == br.com.lojaspopular.domain.catalog.enums.PedidoStatus.ENTREGUE).toList();
    BigDecimal totLeg = legPagosPeriodo.stream().map(x -> x.getTotal() == null ? BigDecimal.ZERO : x.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
    String cob = "Cobertura: só pagamentos registrados no financeiro da gestão. " + legPagosPeriodo.size()
        + " pedido(s) legado(s) do checkout online marcados como pagos no período (R$ " + totLeg
        + ", pelo gateway) NÃO aparecem aqui: não há recebimento, caixa nem recebível registrado para eles.";
    return new RelatorioRecebimentos(p[0], p[1], "Pagamento do cliente ≠ recebível da operadora ≠ entrada efetiva. O cartão só entra "
        + "no banco na liquidação; o caixa físico recebe apenas dinheiro.", pag, entradas, saidas, bruto, taxa, liquido, prevN,
        liquidado, liqN, cob);
  }

  // ------------------------------------------------------------------ contas pendentes

  @Transactional(readOnly = true)
  public RelatorioContas contasPendentes() {
    gestor();
    LocalDate hoje = relogio.hoje();
    List<GrupoConta> grupos = new ArrayList<>();
    for (String tipo : List.of("PAGAR", "RECEBER")) {
      long n = 0;
      long venc = 0;
      long prox = 0;
      BigDecimal t = BigDecimal.ZERO;
      BigDecimal tv = BigDecimal.ZERO;
      BigDecimal tp = BigDecimal.ZERO;
      for (var c : contas.listar(br.com.lojaspopular.domain.financeiro.enums.TipoConta.valueOf(tipo), SituacaoConta.ABERTA, null, null)) {
        n++;
        t = t.add(c.valor());
        if (c.vencimento().isBefore(hoje)) {
          venc++;
          tv = tv.add(c.valor());
        } else if (!c.vencimento().isAfter(hoje.plusDays(7))) {
          prox++;
          tp = tp.add(c.valor());
        }
      }
      grupos.add(new GrupoConta(tipo, n, t, venc, tv, prox, tp));
    }
    return new RelatorioContas(hoje, grupos);
  }

  // ------------------------------------------------------------------ estoque e entregas

  @Transactional(readOnly = true)
  public RelatorioEstoque estoque() {
    gestor();
    var saldos = estoque.listarSaldos();
    long semSaldo = saldos.stream().filter(s -> s.fisico() == null).count();
    long indisp = saldos.stream().filter(s -> s.fisico() != null && s.disponivel() != null && s.disponivel() <= 0).count();
    long fis = saldos.stream().filter(s -> s.fisico() != null).mapToLong(s -> s.fisico()).sum();
    long res = saldos.stream().mapToLong(s -> s.reservado()).sum();
    return new RelatorioEstoque(saldos.size(), semSaldo, indisp, fis, res, saldos);
  }

  @Transactional(readOnly = true)
  public RelatorioEntregas entregas(LocalDate de, LocalDate ate) {
    gestor();
    LocalDate[] p = periodo(de, ate);
    LocalDate hoje = relogio.hoje();
    Map<String, Long> porEntrega = new TreeMap<>();
    Map<String, Long> porMontagem = new TreeMap<>();
    for (Pedido ped : pedidos.confirmadasNoPeriodo(Instant0(), relogio.inicioDoDia(hoje.plusDays(1)))) {
      porEntrega.merge(ped.getStatusEntrega().name(), 1L, Long::sum);
      porMontagem.merge(ped.getStatusMontagem().name(), 1L, Long::sum);
    }
    Map<String, Long> agendadas = new TreeMap<>();
    for (var e : entregas.agenda(p[0], p[1])) {
      agendadas.merge(e.getStatus().name(), 1L, Long::sum);
    }
    long concl = entregas.concluidasNoPeriodo(relogio.inicioDoDia(p[0]), relogio.inicioDoDia(p[1].plusDays(1))).size();
    List<EntregaAtrasada> atrasadas = entregas.agenda(LocalDate.of(2000, 1, 1), hoje.minusDays(1)).stream()
        .filter(e -> e.getStatus().name().equals("AGENDADA") || e.getStatus().name().equals("TENTATIVA_FRUSTRADA"))
        .map(e -> new EntregaAtrasada(e.getPedido().getId(), e.getDataPrevista(), e.getEquipe(), e.getStatus().name())).toList();
    return new RelatorioEntregas(p[0], p[1], porEntrega, porMontagem, agendadas, concl, atrasadas);
  }

  private static java.time.Instant Instant0() {
    return java.time.Instant.EPOCH;
  }

  // ------------------------------------------------------------------ comissões e metas

  @Transactional(readOnly = true)
  public RelatorioComissoes comissoes(LocalDate de, LocalDate ate) {
    gestor();
    LocalDate[] p = periodo(de, ate);
    var resumo = comissoes.listar(null, null);
    Map<Long, BigDecimal[]> acc = new LinkedHashMap<>();
    Map<Long, String> nomes = new LinkedHashMap<>();
    for (ComissaoView c : resumo.itens()) {
      if (c.competencia() != null && (c.competencia().isBefore(LocalDate.of(p[0].getYear(), p[0].getMonth(), 1))
          || c.competencia().isAfter(p[1]))) {
        continue;
      }
      BigDecimal[] v = acc.computeIfAbsent(c.vendedorId(), x -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO});
      nomes.put(c.vendedorId(), c.vendedor());
      switch (c.status()) {
        case PREVISTA -> v[0] = v[0].add(c.valor());
        case DEVIDA -> v[1] = v[1].add(c.valor());
        case EM_CONTA -> v[2] = v[2].add(c.valor());
        case PAGA -> v[3] = v[3].add(c.valor());
        case LANCADA -> v[4] = v[4].add(c.valor());
        default -> {
        }
      }
    }
    List<LinhaComissao> linhas = acc.entrySet().stream().map(e -> new LinhaComissao(e.getKey(), nomes.get(e.getKey()),
        e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4]))
        .sorted(Comparator.comparing(LinhaComissao::vendedor, Comparator.nullsLast(String::compareTo))).toList();
    return new RelatorioComissoes(p[0], p[1], resumo.percentualDefinido(), resumo.aquisicaoDefinida(), resumo.avisos(), linhas);
  }

  @Transactional(readOnly = true)
  public RelatorioMetas metas(String mes) {
    gestor();
    var lista = metas.listar(mes);
    BigDecimal tm = lista.stream().map(MetaView::meta).filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal tv = lista.stream().map(MetaView::vendido).filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new RelatorioMetas(mes, lista, tm, tv);
  }

  // ------------------------------------------------------------------ util

  private void gestor() {
    permissoes.exigir(usuarioAtual.get(), br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.CONSULTAR);
  }

  private LocalDate[] periodo(LocalDate de, LocalDate ate) {
    LocalDate hoje = relogio.hoje();
    LocalDate d = de == null ? hoje.withDayOfMonth(1) : de;
    LocalDate a = ate == null ? hoje : ate;
    if (a.isBefore(d)) {
      throw new NegocioException("A data final não pode ser anterior à inicial.");
    }
    if (d.plusYears(2).isBefore(a)) {
      throw new NegocioException("O período máximo do relatório é de 2 anos.");
    }
    return new LocalDate[] {d, a};
  }
}
