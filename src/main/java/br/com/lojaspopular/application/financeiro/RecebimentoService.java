package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.financeiro.LivroService.Lanc;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebimento;
import br.com.lojaspopular.domain.financeiro.enums.TipoCartao;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.StatusRestituicao;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.Recebimento;
import br.com.lojaspopular.domain.financeiro.model.RecebivelCartao;
import br.com.lojaspopular.domain.financeiro.model.TaxaCartao;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebimentoRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebivelCartaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.RestituicaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.TaxaCartaoRepository;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.PagamentoPedido;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RecebimentoView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RegistrarRecebimentoRequest;
import lombok.RequiredArgsConstructor;

/**
 * Recebimentos: o pagamento feito pelo CLIENTE, sempre numa única forma por venda (a da venda).
 *
 * <p>Três coisas distintas, que nunca se somam duas vezes:
 * <ol>
 * <li><b>Recebimento</b> (este serviço): o cliente pagou. Define o status de pagamento da venda;</li>
 * <li><b>Recebível da operadora</b> (só cartão): valor bruto/taxa/líquido a receber e a previsão;</li>
 * <li><b>Entrada efetiva</b> (livro de lançamentos): dinheiro no caixa físico (espécie, no ato), no banco
 * (Pix, na data do pagamento) ou no banco na liquidação do cartão — nunca no recebimento do cartão.</li>
 * </ol>
 * Pagamentos e estornos repetidos são idempotentes (chave); venda e cobrança de cartão exigem a taxa da operadora cadastrada.
 */
@Service
@RequiredArgsConstructor
public class RecebimentoService {

  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final RecebimentoRepository recebimentos;
  private final RecebivelCartaoRepository recebiveis;
  private final TaxaCartaoRepository taxas;
  private final RestituicaoRepository restituicoes;
  private final LancamentoFinanceiroRepository lancamentos;
  private final LivroService livro;
  private final PeriodoService periodo;
  private final VendaAcesso acesso;
  private final ConfiguracaoComercialService config;
  private final ComissaoService comissoes;
  private final FinanceiroMapper mapper;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional
  public RecebimentoView registrar(Long pedidoId, RegistrarRecebimentoRequest req, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do recebimento (cabeçalho Idempotency-Key).");
    Pedido p = acesso.travar(pedidoId, ator);

    var repetido = recebimentos.findByChave(k);
    if (repetido.isPresent()) {
      if (!repetido.get().getPedido().getId().equals(pedidoId)) {
        throw new NegocioException("Esta chave de idempotência já foi usada em outra venda.");
      }
      return mapper.view(repetido.get());
    }
    if (p.getStatusComercial() != StatusComercial.CONFIRMADA) {
      throw new NegocioException("O recebimento só pode ser registrado em venda confirmada.");
    }
    FormaPagamento formaVenda = p.getFormaPagamento();
    FormaPagamento forma = req.forma() != null ? req.forma() : formaVenda;
    if (forma == null) {
      throw new NegocioException("Informe a forma de pagamento (Pix, dinheiro ou cartão).");
    }
    var ativos = recebimentos.findByPedidoIdOrderByIdAsc(pedidoId).stream()
        .filter(x -> x.getStatus() == StatusRecebimento.REGISTRADO).toList();
    if (!ativos.isEmpty() && ativos.get(0).getForma() != forma) {
      throw new NegocioException("Esta venda já tem recebimento em " + ativos.get(0).getForma()
          + ": uma venda tem uma única forma de pagamento. Estorne o recebimento para trocar.");
    }
    TipoCartao tipoCartao = null;
    int parcelasPag = 1;
    if (forma == FormaPagamento.CARTAO) {
      tipoCartao = req.tipoCartao() != null ? req.tipoCartao() : (formaVenda == FormaPagamento.CARTAO ? TipoCartao.CREDITO : null);
      if (tipoCartao == null) {
        throw new NegocioException("Informe se o cartão é crédito ou débito.");
      }
      parcelasPag = tipoCartao == TipoCartao.DEBITO ? 1
          : req.parcelas() != null ? req.parcelas() : (formaVenda == FormaPagamento.CARTAO && p.getParcelas() != null ? p.getParcelas() : 1);
      if (parcelasPag < 1) {
        throw new NegocioException("O número de parcelas deve ser no mínimo 1.");
      }
    }
    int parcelasVenda = p.getParcelas() == null ? 1 : p.getParcelas();
    if (forma != formaVenda || parcelasPag != parcelasVenda) {
      // trocar a forma/parcelas no momento do pagamento só é possível se NÃO muda o preço da venda
      var nova = config.exigirCondicao(forma, parcelasPag);
      BigDecimal atual = p.getAjusteCondicaoPercentual() == null ? BigDecimal.ZERO : p.getAjusteCondicaoPercentual();
      BigDecimal novoAjuste = nova.getAjustePercentual() == null ? BigDecimal.ZERO : nova.getAjustePercentual();
      if (atual.compareTo(novoAjuste) != 0) {
        throw new NegocioException("Trocar para " + forma + " em " + parcelasPag + "x mudaria o preço da venda (ajuste de "
            + atual + "% para " + novoAjuste + "%). Cancele e refaça a venda com a forma correta.");
      }
      p.setFormaPagamento(forma);
      p.setParcelas(parcelasPag);
    }

    BigDecimal valor = req.valor().setScale(2, RoundingMode.HALF_UP);
    BigDecimal recebido = recebimentos.somaAtiva(pedidoId);
    BigDecimal saldo = p.getTotal().subtract(recebido);
    if (valor.compareTo(saldo) > 0) {
      throw new NegocioException("O valor (R$ " + valor + ") excede o saldo a receber da venda (R$ " + saldo + ").");
    }
    LocalDate hoje = relogio.hoje();
    LocalDate data = req.dataPagamento() == null ? hoje : req.dataPagamento();
    if (data.isAfter(hoje)) {
      throw new NegocioException("A data do pagamento não pode ser futura.");
    }
    periodo.exigirAberto(data);

    String operadora = req.operadora() == null || req.operadora().isBlank() ? null : req.operadora().trim();
    if (forma == FormaPagamento.DINHEIRO && !data.equals(hoje)) {
      throw new NegocioException("O dinheiro entra no caixa no dia do recebimento: use a data de hoje.");
    }
    if (forma == FormaPagamento.CARTAO) {
      if (valor.compareTo(p.getTotal()) != 0) {
        throw new NegocioException("O pagamento em cartão é feito pelo valor total da venda (R$ " + p.getTotal() + ").");
      }
    }

    Recebimento r = recebimentos.save(Recebimento.builder().pedido(p).forma(forma).valor(valor)
        .parcelas(forma == FormaPagamento.CARTAO ? parcelasPag : null).tipoCartao(tipoCartao).dataPagamento(data)
        .referencia(limpar(req.referencia())).operadora(operadora).observacao(limpar(req.observacao()))
        .status(StatusRecebimento.REGISTRADO).registradoPor(ator).chave(k).build());

    switch (forma) {
      case DINHEIRO -> livro.lancar(Lanc.builder().conta(ContaLivro.CAIXA).tipo(TipoLancamento.ENTRADA).valor(valor)
          .data(hoje).origem(OrigemLancamento.RECEBIMENTO).descricao("Recebimento em dinheiro da venda #" + pedidoId)
          .pedido(p).recebimento(r).usuario(ator).build());
      case PIX -> livro.lancar(Lanc.builder().conta(ContaLivro.BANCO).tipo(TipoLancamento.ENTRADA).valor(valor)
          .data(data).origem(OrigemLancamento.RECEBIMENTO).descricao("Recebimento via Pix da venda #" + pedidoId)
          .pedido(p).recebimento(r).usuario(ator).build());
      case CARTAO -> gerarRecebiveis(r, p);   // o dinheiro só entra no banco na liquidação da operadora
    }

    atualizarStatusPagamento(p);
    comissoes.reavaliar(p);
    auditoria.registrar(AuditoriaTipo.RECEBIMENTO_REGISTRADO,
        "Recebimento de R$ " + valor + " (" + forma + ") registrado na venda #" + pedidoId, "PEDIDO", pedidoId);
    return mapper.view(r);
  }

  /**
   * Estorno de recebimento (lançamento errado, pagamento desfeito). Não duplica: repetir com a mesma chave devolve o
   * mesmo resultado; outro estorno do mesmo recebimento é recusado. No cartão, só enquanto nenhuma parcela
   * tiver sido liquidada (antes, estorne a liquidação).
   */
  @Transactional
  public RecebimentoView estornar(Long recebimentoId, String motivo, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.ESTORNAR);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do estorno (cabeçalho Idempotency-Key).");
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo do estorno.");

    Long pedidoId = recebimentos.pedidoIdDe(recebimentoId)
        .orElseThrow(() -> new NotFoundException("Recebimento não encontrado"));
    Pedido p = acesso.travar(pedidoId, ator);
    Recebimento r = recebimentos.findByIdForUpdate(recebimentoId).orElseThrow();

    if (r.getStatus() == StatusRecebimento.ESTORNADO) {
      if (k.equals(r.getChaveEstorno())) {
        return mapper.view(r);
      }
      throw new NegocioException("Este recebimento já foi estornado.");
    }
    BigDecimal restituido = restituicoes.somaPorPedido(p.getId(), List.of(StatusRestituicao.EFETIVADA));
    BigDecimal depois = recebimentos.somaAtiva(p.getId()).subtract(r.getValor());
    if (depois.compareTo(restituido) < 0) {
      throw new NegocioException("Não é possível estornar: já foram restituídos R$ " + restituido
          + " ao cliente e o valor recebido restante ficaria abaixo disso.");
    }

    if (p.getStatusPagamento() == StatusPagamento.PAGO) {
      comissoes.exigirEstornoPermitido(p);
    }
    switch (r.getForma()) {
      case DINHEIRO, PIX -> {
        var original = livro.ativo(lancamentos.findByRecebimentoIdOrderByIdAsc(r.getId()))
            .orElseThrow(() -> new NegocioException("Não há lançamento ativo a estornar para este recebimento."));
        livro.estornar(original, "Estorno do recebimento #" + r.getId() + ": " + mot, "estrec:" + k, ator);
      }
      case CARTAO -> {
        var parcelas = recebiveis.findByRecebimentoIdOrderByParcelaAsc(r.getId());
        if (parcelas.stream().anyMatch(x -> x.getStatus() == StatusRecebivel.LIQUIDADO)) {
          throw new NegocioException("Há parcelas já liquidadas pela operadora: estorne a liquidação antes de estornar o recebimento.");
        }
        parcelas.forEach(x -> x.setStatus(StatusRecebivel.CANCELADO));
      }
    }
    r.setStatus(StatusRecebimento.ESTORNADO);
    r.setEstornadoEm(relogio.agora());
    r.setEstornadoPor(ator);
    r.setMotivoEstorno(mot);
    r.setChaveEstorno(k);

    atualizarStatusPagamento(p);
    comissoes.reavaliar(p);
    auditoria.registrar(AuditoriaTipo.RECEBIMENTO_ESTORNADO,
        "Recebimento #" + r.getId() + " (R$ " + r.getValor() + ") estornado na venda #" + p.getId() + ": " + mot, "PEDIDO",
        p.getId());
    return mapper.view(r);
  }

  /** Painel financeiro da venda (usado no detalhe do pedido). */
  @Transactional(readOnly = true)
  public PagamentoPedido painel(Pedido p, User ator) {
    var lista = recebimentos.findByPedidoIdOrderByIdAsc(p.getId()).stream().map(mapper::view).toList();
    BigDecimal recebido = recebimentos.somaAtiva(p.getId());
    boolean consulta = permissoes.pode(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.CONSULTAR);
    BigDecimal restituido = consulta ? restituicoes.somaPorPedido(p.getId(), List.of(StatusRestituicao.EFETIVADA)) : null;
    var rest = restituicoes.findByPedidoIdOrderByIdDesc(p.getId()).stream()
        .map(x -> consulta ? mapper.view(x) : mapper.viewOperacional(x)).toList();
    // lançamentos (livro) só para quem pode consultar o financeiro (D12); o gerente sem essa permissão vê só o pagamento da venda
    var lanc = permissoes.pode(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.CONSULTAR)
        ? lancamentos.findByPedidoIdOrderByIdAsc(p.getId()).stream().map(mapper::view).toList() : List.<br.com.lojaspopular.web.financeiro.FinanceiroDtos.LancamentoView>of();
    BigDecimal total = p.getTotal() == null ? BigDecimal.ZERO : p.getTotal();
    BigDecimal saldo = total.subtract(recebido);

    Map<String, String> bloqueios = new LinkedHashMap<>();
    boolean confirmada = p.getStatusComercial() == StatusComercial.CONFIRMADA;
    boolean podeRegistrar = permissoes.pode(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER) && confirmada && saldo.signum() > 0;
    if (podeRegistrar && taxas.findByAtivaTrueOrderByOperadoraAscParcelasAsc().isEmpty()) {
      // não bloqueia: o cartão é registrado em plano manual (sem taxa) até a D11 ser cadastrada
      bloqueios.put("cartao", "Sem taxa de operadora cadastrada (D11): o cartão é registrado em plano manual, sem taxa e com a "
          + "previsão na data do pagamento; ao liquidar, informe o valor realmente depositado.");
    }
    if (podeRegistrar && p.getFormaPagamento() == FormaPagamento.DINHEIRO) {
      bloqueios.put("caixa", "Pagamento em dinheiro entra no caixa físico: o caixa precisa estar aberto.");
    }
    boolean podeEstornar = permissoes.pode(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.ESTORNAR) && lista.stream().anyMatch(x -> x.status() == StatusRecebimento.REGISTRADO);
    return new PagamentoPedido(p.getFormaPagamento(), p.getParcelas(), total, recebido, saldo, restituido, lista, rest, lanc,
        bloqueios, podeRegistrar, podeEstornar);
  }

  @Transactional(readOnly = true)
  public BigDecimal totalRecebido(Long pedidoId) {
    return recebimentos.somaAtiva(pedidoId);
  }

  /** Status de pagamento da venda: sempre derivado dos recebimentos ativos (nunca "presumido"). */
  public void atualizarStatusPagamento(Pedido p) {
    BigDecimal recebido = recebimentos.somaAtiva(p.getId());
    StatusPagamento novo = recebido.signum() <= 0 ? StatusPagamento.PENDENTE
        : recebido.compareTo(p.getTotal()) >= 0 ? StatusPagamento.PAGO : StatusPagamento.PARCIAL;
    p.setStatusPagamento(novo);
    if (novo == StatusPagamento.PAGO && p.getStatus() == PedidoStatus.CRIADO) {
      p.setStatus(PedidoStatus.PAGO);
    } else if (novo != StatusPagamento.PAGO && p.getStatus() == PedidoStatus.PAGO
        && p.getStatusEntrega() != StatusEntrega.ENTREGUE) {
      p.setStatus(PedidoStatus.CRIADO);
    }
  }

  // ---- cartão: plano de recebíveis ----

  private void gerarRecebiveis(Recebimento r, Pedido p) {
    Arredondamento arred = config.exigirArredondamento();
    int n = r.getParcelas() == null ? 1 : r.getParcelas();
    TaxaCartao taxa = r.getOperadora() == null ? null
        : taxas.findByOperadoraAndParcelas(r.getOperadora(), n).filter(TaxaCartao::isAtiva).orElse(null);
    BigDecimal total = r.getValor();
    if (taxa == null) {
      // Sem taxa cadastrada (D11): registro manual. Um recebível único, sem taxa, previsto para a data do pagamento; quem
      // recebe informa o valor real depositado ao liquidar (a diferença fica registrada). Nada é presumido.
      r.setPlanoManual(true);
      recebiveis.save(RecebivelCartao.builder().recebimento(r).pedido(p)
          .operadora(r.getOperadora() == null ? "(não informada)" : r.getOperadora()).parcela(1).totalParcelas(1)
          .valorBruto(total).taxaPercentual(BigDecimal.ZERO).valorTaxa(BigDecimal.ZERO).valorLiquido(total)
          .dataPrevista(r.getDataPagamento()).status(StatusRecebivel.PREVISTO).build());
      return;
    }
    // Divisão em parcelas: arredonda para baixo e a última parcela absorve a diferença (soma bate com o total).
    BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
    BigDecimal acumulado = BigDecimal.ZERO;
    List<RecebivelCartao> plano = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      BigDecimal bruto = i == n ? total.subtract(acumulado) : base;
      acumulado = acumulado.add(bruto);
      BigDecimal valorTaxa = bruto.multiply(taxa.getTaxaPercentual()).divide(BigDecimal.valueOf(100), 2, arred.mode());
      plano.add(RecebivelCartao.builder().recebimento(r).pedido(p).operadora(r.getOperadora()).parcela(i).totalParcelas(n)
          .valorBruto(bruto).taxaPercentual(taxa.getTaxaPercentual()).valorTaxa(valorTaxa)
          .valorLiquido(bruto.subtract(valorTaxa))
          .dataPrevista(r.getDataPagamento().plusDays(taxa.getPrazoPrimeiraParcelaDias()
              + (long) (i - 1) * taxa.getIntervaloDias()))
          .status(StatusRecebivel.PREVISTO).build());
    }
    recebiveis.saveAll(plano);
  }

  private static String limpar(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
