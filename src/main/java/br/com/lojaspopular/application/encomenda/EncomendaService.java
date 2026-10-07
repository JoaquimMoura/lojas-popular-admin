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
import lombok.RequiredArgsConstructor;

/**
 * Acompanhamento de encomendas. A compra do fornecedor acontece fora do sistema: aqui se registra o pedido
 * (fornecedor, referência, previsão) e o recebimento físico. Nada é enviado ao fornecedor.
 *
 * <p>O recebimento gera a entrada de estoque vinculada à encomenda e, na sequência, a reserva do item
 * (reserva posterior). Para não permitir entrega parcial, o recebimento precisa cobrir a quantidade vendida.
 */
@Service
@RequiredArgsConstructor
public class EncomendaService {

  private final EncomendaRepository repo;
  private final ItemPedidoRepository itemRepo;
  private final PedidoRepository pedidoRepo;
  private final ReservaEstoqueRepository reservaRepo;
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
    e.setFornecedor(limpar(req.fornecedor()));
    e.setReferenciaFornecedor(limpar(req.referenciaFornecedor()));
    e.setPrevisaoChegada(req.previsaoChegada());
    e.setObservacao(limpar(req.observacao()));
    boolean pedidoRegistrado = e.getFornecedor() != null || e.getReferenciaFornecedor() != null
        || e.getPrevisaoChegada() != null;
    e.setStatus(pedidoRegistrado ? StatusEncomenda.PEDIDO_REALIZADO : StatusEncomenda.AGUARDANDO_PEDIDO);
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

    if (e.getStatus() == StatusEncomenda.RECEBIDA) {
      if (k.equals(e.getChaveRecebimento())) {
        return view(e);
      }
      throw new NegocioException("Esta encomenda já foi recebida.");
    }
    if (e.getStatus() == StatusEncomenda.CANCELADA || pedido.getStatusComercial() != StatusComercial.CONFIRMADA) {
      throw new NegocioException("A venda desta encomenda foi cancelada ou não está confirmada.");
    }
    ItemPedido item = e.getItem();
    if (quantidade == null || quantidade < item.getQuantidade()) {
      throw new NegocioException("O recebimento precisa cobrir a quantidade vendida (" + item.getQuantidade()
          + "): não há entrega parcial. Registre o recebimento quando o lote completo chegar.");
    }

    estoque.registrarEntrada(TipoMovimentacao.ENTRADA_ENCOMENDA, item.getProduto(), item.getVariacao(), quantidade,
        pedido.getId(), item.getId(), e.getId(), null,
        "Recebimento da encomenda #" + e.getId() + " (venda #" + pedido.getId() + ")", ator);
    estoque.reservarItem(pedido, item);

    e.setStatus(StatusEncomenda.RECEBIDA);
    e.setQuantidadeRecebida(quantidade);
    e.setRecebidaEm(Instant.now());
    e.setRecebidaPor(ator);
    e.setChaveRecebimento(k);
    auditoria.registrar(AuditoriaTipo.ENCOMENDA_RECEBIDA,
        "Encomenda #" + e.getId() + " recebida (" + quantidade + " un.) e reservada para a venda #" + pedido.getId(),
        "PEDIDO", pedido.getId());
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
    boolean aberta = e.getStatus() == StatusEncomenda.AGUARDANDO_PEDIDO || e.getStatus() == StatusEncomenda.PEDIDO_REALIZADO;
    Integer prazo = i.getProduto().getPrazoEncomendaDias();
    return new EncomendaResponse(e.getId(), p.getId(), p.getCliente() == null ? null : p.getCliente().getNome(),
        i.getId(), i.getDescricaoHistorica(), i.getQuantidade(), e.getStatus(), e.getFornecedor(),
        e.getReferenciaFornecedor(), e.getPrevisaoChegada(),
        aberta && e.getPrevisaoChegada() != null && e.getPrevisaoChegada().isBefore(LocalDate.now()),
        e.getObservacao(), e.getQuantidadeRecebida(), e.getRecebidaEm(), reserva,
        prazo == null ? "A definir (D08)" : prazo + " dias");
  }

  private static String limpar(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
