package br.com.lojaspopular.application.encomenda;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.encomenda.enums.StatusEncomenda;
import br.com.lojaspopular.domain.encomenda.model.Encomenda;
import br.com.lojaspopular.domain.encomenda.model.EncomendaRecebimento;
import br.com.lojaspopular.domain.encomenda.repository.EncomendaRecebimentoRepository;
import br.com.lojaspopular.domain.encomenda.repository.EncomendaRepository;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.estoque.enums.TipoMovimentacao;
import br.com.lojaspopular.domain.estoque.model.ReservaEstoque;
import br.com.lojaspopular.domain.estoque.repository.ReservaEstoqueRepository;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.ItemPedidoRepository;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AtualizarEncomendaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EncomendaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.RecebimentoEncomenda;
import lombok.RequiredArgsConstructor;

/**
 * Acompanhamento de encomendas. A compra do fornecedor acontece fora do sistema: aqui se registra o pedido
 * (fornecedor, referência, previsão) e o recebimento físico. Nada é enviado ao fornecedor.
 *
 * <p>O recebimento gera a entrada de estoque vinculada à encomenda e, na sequência, a reserva do item
 * (reserva posterior), de forma progressiva: o fornecedor pode entregar em partes (a entrega parcial ao cliente
 * é que continua proibida, e é verificada na saída).
 */
@Service
@RequiredArgsConstructor
public class EncomendaService {

  private final EncomendaRepository repo;
  private final ItemPedidoRepository itemRepo;
  private final PedidoRepository pedidoRepo;
  private final ReservaEstoqueRepository reservaRepo;
  private final EncomendaRecebimentoRepository recebimentos;
  private final EstoqueService estoque;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  /** Abre o acompanhamento dos itens de encomenda de uma venda recém-confirmada. */
  @Transactional
  public void criarParaPedido(Pedido pedido) {
    for (ItemPedido i : pedido.getItens()) {
      if (i.getModalidade() == ModalidadeItem.ENCOMENDA && !repo.existsByItemId(i.getId())) {
        repo.save(Encomenda.builder().pedido(pedido).item(i).status(StatusEncomenda.AGUARDANDO_PEDIDO).build());
      }
    }
  }

  /** Cria o acompanhamento de vendas confirmadas antes da Etapa 2 (idempotente). */
  @Transactional
  public List<EncomendaResponse> listar(StatusEncomenda status) {
    for (ItemPedido i : itemRepo.encomendasSemAcompanhamento()) {
      repo.save(Encomenda.builder().pedido(i.getPedido()).item(i).status(StatusEncomenda.AGUARDANDO_PEDIDO).build());
    }
    return repo.listar(status).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<EncomendaResponse> doPedido(Long pedidoId) {
    return repo.findByPedidoIdOrderByIdAsc(pedidoId).stream().map(this::view).toList();
  }

  @Transactional
  public EncomendaResponse atualizar(Long id, AtualizarEncomendaRequest req) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Encomenda e = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Encomenda não encontrada"));
    if (e.getStatus() == StatusEncomenda.RECEBIDA || e.getStatus() == StatusEncomenda.CANCELADA) {
      throw new NegocioException("Esta encomenda já está " + (e.getStatus() == StatusEncomenda.RECEBIDA ? "recebida"
          : "cancelada") + " e não pode ser alterada.");
    }
    boolean parcial = e.getStatus() == StatusEncomenda.PARCIALMENTE_RECEBIDA;
    e.setFornecedor(limpar(req.fornecedor()));
    e.setReferenciaFornecedor(limpar(req.referenciaFornecedor()));
    e.setPrevisaoChegada(req.previsaoChegada());
    e.setObservacao(limpar(req.observacao()));
    boolean pedidoRegistrado = e.getFornecedor() != null || e.getReferenciaFornecedor() != null
        || e.getPrevisaoChegada() != null;
    if (!parcial) {
      e.setStatus(pedidoRegistrado ? StatusEncomenda.PEDIDO_REALIZADO : StatusEncomenda.AGUARDANDO_PEDIDO);
    }
    auditoria.registrar(AuditoriaTipo.ENCOMENDA_ATUALIZADA,
        "Encomenda #" + e.getId() + " atualizada (previsão " + e.getPrevisaoChegada() + ")", "PEDIDO",
        e.getPedido().getId());
    return view(e);
  }

  /**
   * Recebimento físico: entrada de estoque + reserva do item. Repetir com a mesma chave devolve o mesmo
   * resultado sem nova entrada.
   */
  @Transactional
  public EncomendaResponse receber(Long id, Integer quantidade, String chave) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do recebimento (cabeçalho Idempotency-Key).");

    Encomenda previa = repo.findById(id).orElseThrow(() -> new NotFoundException("Encomenda não encontrada"));
    Pedido pedido = pedidoRepo.findByIdForUpdate(previa.getPedido().getId())
        .orElseThrow(() -> new NotFoundException("Venda não encontrada"));
    Encomenda e = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Encomenda não encontrada"));

    var repetido = recebimentos.findByChave(k);
    if (repetido.isPresent()) {
      if (repetido.get().getEncomenda().getId().equals(e.getId())) {
        return view(e);   // mesmo recebimento repetido: sem nova entrada
      }
      throw new NegocioException("Esta chave de idempotência já foi usada em outra encomenda.");
    }
    if (e.getStatus() == StatusEncomenda.CANCELADA || pedido.getStatusComercial() != StatusComercial.CONFIRMADA) {
      throw new NegocioException("A venda desta encomenda foi cancelada ou não está confirmada.");
    }
    if (e.getStatus() == StatusEncomenda.RECEBIDA) {
      throw new NegocioException("Esta encomenda já foi recebida por completo.");
    }
    ItemPedido item = e.getItem();
    if (quantidade == null || quantidade <= 0) {
      throw new NegocioException("Informe a quantidade recebida do fornecedor.");
    }

    // Recebimento do FORNECEDOR (pode ser parcial): entrada de estoque + reserva progressiva ao cliente.
    // A entrega ao CLIENTE continua indivisível: a saída só ocorre com todos os itens reservados por inteiro.
    var mov = estoque.registrarEntrada(TipoMovimentacao.ENTRADA_ENCOMENDA, item.getProduto(), item.getVariacao(), quantidade,
        pedido.getId(), item.getId(), e.getId(), null,
        "Recebimento da encomenda #" + e.getId() + " (venda #" + pedido.getId() + ")", ator);
    recebimentos.save(EncomendaRecebimento.builder().encomenda(e).quantidade(quantidade).movimentacao(mov).usuario(ator)
        .chave(k).build());
    int acumulado = (e.getQuantidadeRecebida() == null ? 0 : e.getQuantidadeRecebida()) + quantidade;
    estoque.reservarAteQuantidade(pedido, item, acumulado);

    e.setQuantidadeRecebida(acumulado);
    e.setRecebidaPor(ator);
    e.setChaveRecebimento(k);
    boolean completa = acumulado >= item.getQuantidade();
    e.setStatus(completa ? StatusEncomenda.RECEBIDA : StatusEncomenda.PARCIALMENTE_RECEBIDA);
    if (completa) {
      e.setRecebidaEm(Instant.now());
    }
    auditoria.registrar(AuditoriaTipo.ENCOMENDA_RECEBIDA, "Encomenda #" + e.getId() + ": recebidas " + quantidade + " un. ("
        + acumulado + "/" + item.getQuantidade() + ") para a venda #" + pedido.getId()
        + (completa ? " — completa" : " — parcial; a entrega ao cliente aguarda o restante"), "PEDIDO", pedido.getId());
    return view(e);
  }

  /** Cancelamento da venda: encomendas ainda não recebidas deixam de ser acompanhadas. */
  @Transactional
  public void cancelarDoPedido(Pedido pedido) {
    for (Encomenda e : repo.findByPedidoIdOrderByIdAsc(pedido.getId())) {
      if (e.getStatus() != StatusEncomenda.RECEBIDA) {
        e.setStatus(StatusEncomenda.CANCELADA);
      }
    }
  }

  // ---- internos ----

  private EncomendaResponse view(Encomenda e) {
    ItemPedido i = e.getItem();
    Pedido p = e.getPedido();
    StatusReserva reserva = reservaRepo.findByPedidoIdOrderByIdAsc(p.getId()).stream()
        .filter(r -> r.getItem().getId().equals(i.getId())).map(ReservaEstoque::getStatus)
        .reduce((a, b) -> a == StatusReserva.ATIVA ? a : b).orElse(null);
    boolean aberta = e.getStatus() == StatusEncomenda.AGUARDANDO_PEDIDO || e.getStatus() == StatusEncomenda.PEDIDO_REALIZADO
        || e.getStatus() == StatusEncomenda.PARCIALMENTE_RECEBIDA;
    int reservada = reservaRepo.findByPedidoIdOrderByIdAsc(p.getId()).stream()
        .filter(r -> r.getItem().getId().equals(i.getId()) && r.getStatus() == StatusReserva.ATIVA)
        .mapToInt(ReservaEstoque::getQuantidade).sum();
    int recebida = e.getQuantidadeRecebida() == null ? 0 : e.getQuantidadeRecebida();
    var historico = recebimentos.findByEncomendaIdOrderByIdAsc(e.getId()).stream().map(x -> new RecebimentoEncomenda(x.getId(),
        x.getQuantidade(), x.getRecebidoEm(), x.getUsuario() == null ? null : (x.getUsuario().getNome() != null
            ? x.getUsuario().getNome() : x.getUsuario().getEmail()), x.getObservacao())).toList();
    Integer prazo = i.getProduto().getPrazoEncomendaDias();
    return new EncomendaResponse(e.getId(), p.getId(), p.clienteNomeHistorico(),
        i.getId(), i.getDescricaoHistorica(), i.getQuantidade(), e.getStatus(), e.getFornecedor(),
        e.getReferenciaFornecedor(), e.getPrevisaoChegada(),
        aberta && e.getPrevisaoChegada() != null && e.getPrevisaoChegada().isBefore(LocalDate.now()),
        e.getObservacao(), e.getQuantidadeRecebida(), e.getRecebidaEm(), reserva,
        prazo == null ? "A definir (D08)" : prazo + " dias", reservada, Math.max(i.getQuantidade() - recebida, 0), historico);
  }

  private static String limpar(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
