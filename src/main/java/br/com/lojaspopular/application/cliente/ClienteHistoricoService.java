package br.com.lojaspopular.application.cliente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.financeiro.Relogio;
import br.com.lojaspopular.application.venda.CriteriosVenda;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.order.enums.CanalVenda;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusMontagem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.posvenda.model.OcorrenciaPosVenda;
import br.com.lojaspopular.domain.posvenda.repository.OcorrenciaPosVendaRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Histórico de compras e resumo do relacionamento de um cliente. Escopo: gerente e proprietário veem todas as compras; o
 * vendedor só as suas, a menos que o proprietário tenha liberado (D13). A API aplica o escopo também nos indicadores. Nada de
 * custos, margens, recebimentos ou restituições (valores): só situações operacionais.
 */
@Service
@RequiredArgsConstructor
public class ClienteHistoricoService {

  public static final String OBSERVACAO = "Valor comprado é o das vendas válidas (confirmadas e não canceladas; legadas pagas "
      + "ou entregues), o mesmo critério dos relatórios. Não é dinheiro recebido.";

  public record ItemCompra(Long id, String descricao, Integer quantidade, BigDecimal precoUnitario, BigDecimal total,
      ModalidadeItem modalidade) {
  }

  public record OcorrenciaCompra(Long id, TipoOcorrencia tipo, StatusOcorrencia status, String item, Integer quantidade) {
  }

  public record Compra(Long pedidoId, Instant data, String vendedor, CanalVenda canal, List<ItemCompra> itens,
      BigDecimal subtotal, BigDecimal desconto, BigDecimal total, StatusComercial statusComercial,
      StatusPagamento statusPagamento, StatusEntrega statusEntrega, StatusMontagem statusMontagem,
      boolean contaNosIndicadores, boolean legado, String clienteNaVenda, List<OcorrenciaCompra> ocorrencias) {
  }

  public record PaginaCompras(List<Compra> itens, int pagina, int tamanho, long total, int totalPaginas, String escopo) {
  }

  public record Resumo(String escopo, long compras, BigDecimal valorComprado, BigDecimal valorItensDevolvidos,
      Instant primeiraCompra, Instant ultimaCompra, BigDecimal ticketMedio, long entregasPendentes,
      long montagensPendentes, long canceladas, long trocas, long devolucoes, long assistencias, String observacao) {
  }

  private final ClienteRepository clientes;
  private final PedidoRepository pedidos;
  private final OcorrenciaPosVendaRepository ocorrencias;
  private final ConfiguracaoComercialService config;
  private final UsuarioAtual usuarioAtual;
  private final Relogio relogio;

  @Transactional(readOnly = true)
  public PaginaCompras compras(Long clienteId, LocalDate de, LocalDate ate, StatusComercial situacao, String produto,
      int pagina, int tamanho) {
    User ator = usuarioAtual.get();
    var visiveis = visiveis(clienteId, ator);
    String termo = produto == null ? "" : produto.trim().toLowerCase(Locale.ROOT);
    var filtradas = visiveis.stream()
        .filter(p -> situacao == null || p.getStatusComercial() == situacao)
        .filter(p -> de == null || !dia(p).isBefore(de))
        .filter(p -> ate == null || !dia(p).isAfter(ate))
        .filter(p -> termo.isEmpty() || p.getItens().stream().anyMatch(i -> contem(i.getDescricaoHistorica(), termo)
            || contem(i.getSkuHistorico(), termo)))
        .sorted(Comparator.comparing(this::data).reversed().thenComparing(Pedido::getId, Comparator.reverseOrder()))
        .toList();
    int tam = Math.min(Math.max(tamanho, 1), 50);
    int pag = Math.max(pagina, 0);
    int ini = Math.min(pag * tam, filtradas.size());
    var pagina0 = filtradas.subList(ini, Math.min(ini + tam, filtradas.size()));
    Map<Long, List<OcorrenciaPosVenda>> oc = ocorrenciasDe(pagina0);
    var itens = pagina0.stream().map(p -> compra(p, oc.getOrDefault(p.getId(), List.of()))).toList();
    return new PaginaCompras(itens, pag, tam, filtradas.size(), (int) Math.ceil(filtradas.size() / (double) tam), escopo(ator));
  }

  @Transactional(readOnly = true)
  public Resumo resumo(Long clienteId) {
    User ator = usuarioAtual.get();
    var todas = visiveis(clienteId, ator);
    var validas = todas.stream().filter(CriteriosVenda::valida).toList();
    BigDecimal valor = validas.stream().map(p -> p.getTotal() == null ? BigDecimal.ZERO : p.getTotal())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    var oc = ocorrenciasDe(todas);
    var idsValidos = validas.stream().map(Pedido::getId).collect(Collectors.toSet());
    var vivas = oc.values().stream().flatMap(List::stream).filter(o -> o.getStatus() != StatusOcorrencia.CANCELADA).toList();
    // valor dos ITENS devolvidos fisicamente (preço do item x quantidade): é valor comprado devolvido, não dinheiro restituído
    BigDecimal devolvido = vivas.stream().filter(o -> o.getTipo() == TipoOcorrencia.DEVOLUCAO && o.getDevolucaoRecebidaEm() != null
        && o.getItem() != null && o.getQuantidade() != null && idsValidos.contains(o.getPedido().getId()))
        .map(o -> o.getItem().getPrecoUnitario().multiply(BigDecimal.valueOf(o.getQuantidade())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    Instant primeira = validas.stream().map(this::data).min(Comparator.naturalOrder()).orElse(null);
    Instant ultima = validas.stream().map(this::data).max(Comparator.naturalOrder()).orElse(null);
    BigDecimal ticket = validas.isEmpty() ? BigDecimal.ZERO
        : valor.divide(BigDecimal.valueOf(validas.size()), 2, RoundingMode.HALF_UP);
    long entregas = validas.stream().filter(p -> p.getStatusComercial() == StatusComercial.CONFIRMADA
        && List.of(StatusEntrega.NAO_AGENDADA, StatusEntrega.AGENDADA, StatusEntrega.SAIU, StatusEntrega.TENTATIVA_FRUSTRADA)
            .contains(p.getStatusEntrega())).count();
    long montagens = validas.stream().filter(p -> p.getStatusComercial() == StatusComercial.CONFIRMADA
        && List.of(StatusMontagem.NAO_AGENDADA, StatusMontagem.AGENDADA).contains(p.getStatusMontagem())).count();
    long canceladas = todas.stream().filter(p -> p.getStatusComercial() == StatusComercial.CANCELADA).count();
    return new Resumo(escopo(ator), validas.size(), valor, devolvido, primeira, ultima, ticket, entregas, montagens, canceladas,
        contar(vivas, TipoOcorrencia.TROCA), contar(vivas, TipoOcorrencia.DEVOLUCAO), contar(vivas, TipoOcorrencia.ASSISTENCIA),
        OBSERVACAO);
  }

  // ---- internos ----

  private List<Pedido> visiveis(Long clienteId, User ator) {
    clientes.findById(clienteId).orElseThrow(() -> new NotFoundException("Cliente não encontrado"));
    var todos = pedidos.findByClienteIdOrderByIdDesc(clienteId);
    if (UsuarioAtual.isGestor(ator) || Boolean.TRUE.equals(config.obter().getVendedorVeHistoricoCliente())) {
      return todos;
    }
    return todos.stream().filter(p -> p.getVendedor() != null && p.getVendedor().getId().equals(ator.getId())).toList();
  }

  private String escopo(User ator) {
    return UsuarioAtual.isGestor(ator) || Boolean.TRUE.equals(config.obter().getVendedorVeHistoricoCliente())
        ? "COMPLETO" : "SOMENTE_SUAS_VENDAS";
  }

  private Map<Long, List<OcorrenciaPosVenda>> ocorrenciasDe(List<Pedido> lista) {
    if (lista.isEmpty()) {
      return Map.of();
    }
    return ocorrencias.findByPedidoIdIn(lista.stream().map(Pedido::getId).toList()).stream()
        .collect(Collectors.groupingBy(o -> o.getPedido().getId()));
  }

  private Compra compra(Pedido p, List<OcorrenciaPosVenda> oc) {
    var itens = p.getItens().stream().map(i -> new ItemCompra(i.getId(), i.getDescricaoHistorica(), i.getQuantidade(),
        i.getPrecoUnitario(), i.getTotal(), i.getModalidade())).toList();
    var ocs = oc.stream().map(o -> new OcorrenciaCompra(o.getId(), o.getTipo(), o.getStatus(),
        o.getItem() == null ? null : o.getItem().getDescricaoHistorica(), o.getQuantidade())).toList();
    return new Compra(p.getId(), data(p), p.getVendedor() == null ? null : nome(p.getVendedor()), p.getCanal(), itens,
        p.getSubtotal(), p.getDesconto(), p.getTotal(), p.getStatusComercial(), p.getStatusPagamento(), p.getStatusEntrega(),
        p.getStatusMontagem(), CriteriosVenda.valida(p), p.getStatusComercial() == StatusComercial.LEGADO,
        p.clienteNomeHistorico(), ocs);
  }

  private Instant data(Pedido p) {
    return p.getConfirmadoEm() != null ? p.getConfirmadoEm() : p.getCriadoEm();
  }

  private LocalDate dia(Pedido p) {
    return data(p).atZone(relogio.zona()).toLocalDate();
  }

  private static boolean contem(String texto, String termo) {
    return texto != null && texto.toLowerCase(Locale.ROOT).contains(termo);
  }

  private static long contar(List<OcorrenciaPosVenda> l, TipoOcorrencia t) {
    return l.stream().filter(o -> o.getTipo() == t).count();
  }

  private static String nome(User u) {
    return u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail();
  }
}
