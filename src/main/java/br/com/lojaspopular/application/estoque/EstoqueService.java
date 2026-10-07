package br.com.lojaspopular.application.estoque;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoVariacaoRepository;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.estoque.model.ReservaEstoque;
import br.com.lojaspopular.domain.estoque.repository.ReservaEstoqueRepository;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.exception.NegocioException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Saldos e reservas de estoque.
 *
 * <p>Regra de saldo autoritativo (uma única fonte por unidade física):
 * <ul>
 * <li>produto COM variações: o saldo vale na variação ({@code ProdutoVariacao.estoque});
 * o {@code Produto.estoque} é apenas informativo e não é usado em vendas;</li>
 * <li>produto SEM variações: o saldo vale no produto.</li>
 * </ul>
 * Variação sem saldo informado não pode ser reservada (evita dois saldos para a mesma unidade).
 *
 * <p>Disponível = físico - reservas ATIVAS. A reserva não baixa o físico (a baixa ocorre
 * na saída para entrega/retirada, Etapa 2). A serialização por unidade é feita com lock
 * pessimista na linha da variação/produto, em ordem determinística (sem deadlock).
 */
@Service
@RequiredArgsConstructor
public class EstoqueService {

  public record Saldo(Long produtoId, String produtoNome, Long variacaoId, String variacaoDescricao, String sku,
      Integer fisico, long reservado, Integer disponivel, String alerta) {
  }

  private final ProdutoRepository produtoRepo;
  private final ProdutoVariacaoRepository variacaoRepo;
  private final ReservaEstoqueRepository reservaRepo;
  private final AuditoriaService auditoria;
  private final EntityManager em;

  /** Chave ordenável de uma unidade de estoque: variações antes de produtos, por id. */
  private record Unidade(boolean variacao, Long id) implements Comparable<Unidade> {
    @Override
    public int compareTo(Unidade o) {
      int c = Boolean.compare(o.variacao, variacao);
      return c != 0 ? c : id.compareTo(o.id);
    }
  }

  @Transactional(readOnly = true)
  public List<Saldo> listarSaldos() {
    Map<String, Long> reservas = new HashMap<>();
    for (Object[] r : reservaRepo.somaPorUnidade(StatusReserva.ATIVA)) {
      reservas.put(r[0] + ":" + r[1], ((Number) r[2]).longValue());
    }

    List<Saldo> saldos = new ArrayList<>();
    for (Produto p : produtoRepo.findAll()) {
      if (p.getVariacoes().isEmpty()) {
        long reservado = reservas.getOrDefault(p.getId() + ":null", 0L);
        saldos.add(new Saldo(p.getId(), p.getNome(), null, null, p.getSku(), p.getEstoque(), reservado,
            p.getEstoque() == null ? null : (int) (p.getEstoque() - reservado), null));
        continue;
      }
      boolean algumNulo = p.getVariacoes().stream().anyMatch(v -> v.getEstoque() == null);
      boolean algumInformado = p.getVariacoes().stream().anyMatch(v -> v.getEstoque() != null);
      int soma = p.getVariacoes().stream().mapToInt(v -> v.getEstoque() == null ? 0 : v.getEstoque()).sum();
      for (ProdutoVariacao v : p.getVariacoes()) {
        long reservado = reservas.getOrDefault(p.getId() + ":" + v.getId(), 0L);
        String alerta = null;
        if (v.getEstoque() == null) {
          alerta = algumInformado
              ? "Variação sem saldo, mas outras variações têm: cadastro inconsistente."
              : "Produto com variações sem saldo por variação: informe o estoque de cada variação.";
        } else if (!algumNulo && p.getEstoque() != null && soma != p.getEstoque()) {
          alerta = "Soma das variações (" + soma + ") difere do estoque do produto (" + p.getEstoque()
              + "). Vale o saldo da variação.";
        }
        saldos.add(new Saldo(p.getId(), p.getNome(), v.getId(), descricao(v), v.getSku(), v.getEstoque(), reservado,
            v.getEstoque() == null ? null : (int) (v.getEstoque() - reservado), alerta));
      }
    }
    return saldos;
  }

  /**
   * Cria as reservas dos itens de pronta entrega do pedido, de forma atômica: se qualquer
   * unidade não tiver saldo disponível, nada é reservado (a transação é desfeita).
   */
  @Transactional
  public List<ReservaEstoque> reservar(Pedido pedido) {
    if (!reservaRepo.findByPedidoIdAndStatus(pedido.getId(), StatusReserva.ATIVA).isEmpty()) {
      throw new NegocioException("Este pedido já possui reservas ativas.");
    }

    Map<Unidade, Integer> demanda = new TreeMap<>();
    Map<Unidade, String> nomes = new HashMap<>();
    List<ItemPedido> itens = pedido.getItens().stream()
        .filter(i -> i.getModalidade() == ModalidadeItem.PRONTA_ENTREGA).toList();
    for (ItemPedido i : itens) {
      Unidade u = unidade(i);
      demanda.merge(u, i.getQuantidade(), Integer::sum);
      nomes.putIfAbsent(u, i.getDescricaoHistorica());
    }

    // Trava e confere em ordem determinística (variações por id, depois produtos por id).
    for (var e : demanda.entrySet()) {
      Unidade u = e.getKey();
      Integer fisico;
      long reservado;
      if (u.variacao()) {
        ProdutoVariacao v = variacaoRepo.findById(u.id())
            .orElseThrow(() -> new NegocioException("Variação não encontrada."));
        em.refresh(v, LockModeType.PESSIMISTIC_WRITE);
        fisico = v.getEstoque();
        if (fisico == null) {
          throw new NegocioException("A variação \"" + nomes.get(u)
              + "\" não tem saldo de estoque cadastrado; informe o estoque da variação no cadastro do produto.");
        }
        reservado = reservaRepo.somaPorVariacao(u.id(), StatusReserva.ATIVA);
      } else {
        Produto p = produtoRepo.findById(u.id()).orElseThrow(() -> new NegocioException("Produto não encontrado."));
        em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        fisico = p.getEstoque();
        reservado = reservaRepo.somaPorProdutoSemVariacao(u.id(), StatusReserva.ATIVA);
      }
      long disponivel = fisico - reservado;
      if (e.getValue() > disponivel) {
        throw new NegocioException("Estoque insuficiente para \"" + nomes.get(u) + "\": disponível "
            + Math.max(disponivel, 0) + ", solicitado " + e.getValue() + ".");
      }
    }

    List<ReservaEstoque> criadas = new ArrayList<>();
    for (ItemPedido i : itens) {
      criadas.add(reservaRepo.save(ReservaEstoque.builder()
          .pedido(pedido).item(i).produto(i.getProduto()).variacao(i.getVariacao())
          .quantidade(i.getQuantidade()).status(StatusReserva.ATIVA).build()));
    }
    if (!criadas.isEmpty()) {
      auditoria.registrar(AuditoriaTipo.RESERVA_CRIADA,
          "Reserva de estoque criada para o pedido #" + pedido.getId() + " (" + criadas.size() + " item(ns))",
          "PEDIDO", pedido.getId());
    }
    return criadas;
  }

  /** Libera as reservas ativas do pedido (cancelamento). */
  @Transactional
  public int liberar(Pedido pedido, String motivo) {
    var ativas = reservaRepo.findByPedidoIdAndStatus(pedido.getId(), StatusReserva.ATIVA);
    Instant agora = Instant.now();
    for (ReservaEstoque r : ativas) {
      r.setStatus(StatusReserva.LIBERADA);
      r.setLiberadaEm(agora);
      r.setMotivoLiberacao(motivo);
    }
    if (!ativas.isEmpty()) {
      auditoria.registrar(AuditoriaTipo.RESERVA_LIBERADA,
          "Reserva liberada do pedido #" + pedido.getId() + ": " + motivo, "PEDIDO", pedido.getId());
    }
    return ativas.size();
  }

  @Transactional(readOnly = true)
  public List<ReservaEstoque> reservasDoPedido(Long pedidoId) {
    return reservaRepo.findByPedidoIdOrderByIdAsc(pedidoId);
  }

  private Unidade unidade(ItemPedido i) {
    return i.getVariacao() != null ? new Unidade(true, i.getVariacao().getId())
        : new Unidade(false, i.getProduto().getId());
  }

  public static String descricao(ProdutoVariacao v) {
    StringBuilder sb = new StringBuilder();
    if (v.getCor() != null && !v.getCor().isBlank()) {
      sb.append(v.getCor());
    }
    if (v.getTamanho() != null && !v.getTamanho().isBlank()) {
      if (sb.length() > 0) {
        sb.append(" / ");
      }
      sb.append(v.getTamanho());
    }
    return sb.length() == 0 ? "Variação #" + v.getId() : sb.toString();
  }
}
