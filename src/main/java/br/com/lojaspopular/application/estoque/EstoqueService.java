package br.com.lojaspopular.application.estoque;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoVariacaoRepository;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.estoque.enums.TipoMovimentacao;
import br.com.lojaspopular.domain.estoque.model.MovimentacaoEstoque;
import br.com.lojaspopular.domain.estoque.model.ReservaEstoque;
import br.com.lojaspopular.domain.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.lojaspopular.domain.estoque.repository.ReservaEstoqueRepository;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Saldos, reservas e movimentações de estoque.
 *
 * <p>Regra de saldo autoritativo (uma única fonte por unidade física):
 * <ul>
 * <li>produto COM variações: o saldo vale na variação ({@code ProdutoVariacao.estoque}); o
 * {@code Produto.estoque} é um agregado mantido pelo sistema (soma das variações, quando todas têm saldo)
 * e não é usado em vendas;</li>
 * <li>produto SEM variações: o saldo vale no produto.</li>
 * </ul>
 * Variação sem saldo informado não pode ser reservada nem movimentada (evita dois saldos para a mesma
 * unidade); o primeiro saldo é definido por contagem de inventário.
 *
 * <p>Disponível = físico - reservas ATIVAS. A reserva não baixa o físico: a baixa ocorre na saída
 * (entrega/retirada) e consome a reserva. Toda alteração do físico gera uma {@link MovimentacaoEstoque}.
 * A serialização por unidade usa lock pessimista na linha da variação/produto, em ordem determinística.
 */
@Service
@RequiredArgsConstructor
public class EstoqueService {

  public record Saldo(Long produtoId, String produtoNome, Long variacaoId, String variacaoDescricao, String sku,
      Integer fisico, long reservado, Integer disponivel, String alerta) {
  }

  public record MovimentacaoView(Long id, TipoMovimentacao tipo, Long produtoId, String produto, Long variacaoId,
      String variacao, Integer quantidade, Integer saldoAnterior, Integer saldoPosterior, Long pedidoId,
      String motivo, String usuario, Instant criadoEm) {
  }

  public record PaginaMovimentacoes(List<MovimentacaoView> conteudo, int pagina, int tamanho, long total,
      int totalPaginas) {
  }

  private final ProdutoRepository produtoRepo;
  private final ProdutoVariacaoRepository variacaoRepo;
  private final ReservaEstoqueRepository reservaRepo;
  private final MovimentacaoEstoqueRepository movimentacaoRepo;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;
  private final EntityManager em;

  /** Chave ordenável de uma unidade de estoque: variações antes de produtos, por id. */
  private record Unidade(boolean variacao, Long id) implements Comparable<Unidade> {
    @Override
    public int compareTo(Unidade o) {
      int c = Boolean.compare(o.variacao, variacao);
      return c != 0 ? c : id.compareTo(o.id);
    }
  }

  /** Unidade já travada para escrita, com o saldo físico lido sob lock. */
  private record Travada(Produto produto, ProdutoVariacao variacao, Integer fisico) {
  }

  // =====================================================================
  // Consultas
  // =====================================================================

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

  @Transactional(readOnly = true)
  public PaginaMovimentacoes listarMovimentacoes(Long produtoId, Long variacaoId, Long pedidoId, int pagina,
      int tamanho) {
    var pageable = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100));
    var page = movimentacaoRepo.buscar(produtoId, variacaoId, pedidoId, pageable);
    return new PaginaMovimentacoes(page.getContent().stream().map(this::view).toList(), page.getNumber(),
        page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<MovimentacaoView> movimentacoesDoPedido(Long pedidoId) {
    return movimentacaoRepo.findByPedidoIdOrderByIdAsc(pedidoId).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<ReservaEstoque> reservasDoPedido(Long pedidoId) {
    return reservaRepo.findByPedidoIdOrderByIdAsc(pedidoId);
  }

  // =====================================================================
  // Reserva
  // =====================================================================

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
      Travada t = travar(u, nomes.get(u));
      long disponivel = t.fisico() - reservado(u);
      if (e.getValue() > disponivel) {
        throw new NegocioException("Estoque insuficiente para \"" + nomes.get(u) + "\": disponível "
            + Math.max(disponivel, 0) + ", solicitado " + e.getValue() + ".");
      }
    }

    List<ReservaEstoque> criadas = new ArrayList<>();
    for (ItemPedido i : itens) {
      criadas.add(novaReserva(pedido, i));
    }
    if (!criadas.isEmpty()) {
      auditoria.registrar(AuditoriaTipo.RESERVA_CRIADA,
          "Reserva de estoque criada para o pedido #" + pedido.getId() + " (" + criadas.size() + " item(ns))",
          "PEDIDO", pedido.getId());
    }
    return criadas;
  }

  /**
   * Reserva progressiva de um item de encomenda: eleva a reserva ativa do item até {@code quantidadeDesejada} (no máximo a
   * quantidade vendida), à medida que a mercadoria chega do fornecedor. Assim as unidades recebidas ficam guardadas para o
   * cliente e não são vendidas a outro, mesmo antes de o lote completo chegar. A SAÍDA continua exigindo a reserva integral.
   */
  @Transactional
  public ReservaEstoque reservarAteQuantidade(Pedido pedido, ItemPedido item, int quantidadeDesejada) {
    int alvo = Math.min(quantidadeDesejada, item.getQuantidade());
    Unidade u = unidade(item);
    Travada t = travar(u, item.getDescricaoHistorica());
    var existente = reservaRepo.findByPedidoIdAndStatus(pedido.getId(), StatusReserva.ATIVA).stream()
        .filter(r -> r.getItem().getId().equals(item.getId())).findFirst();
    int atual = existente.map(ReservaEstoque::getQuantidade).orElse(0);
    int delta = alvo - atual;
    if (delta <= 0) {
      return existente.orElse(null);
    }
    long disponivel = t.fisico() - reservado(u);
    if (delta > disponivel) {
      throw new NegocioException("Estoque disponível insuficiente para reservar mais " + delta + " un. de \""
          + item.getDescricaoHistorica() + "\": disponível " + Math.max(disponivel, 0) + ".");
    }
    ReservaEstoque reserva;
    if (existente.isPresent()) {
      reserva = existente.get();
      reserva.setQuantidade(alvo);
    } else {
      reserva = reservaRepo.save(ReservaEstoque.builder().pedido(pedido).item(item).produto(item.getProduto())
          .variacao(item.getVariacao()).quantidade(alvo).status(StatusReserva.ATIVA).build());
    }
    auditoria.registrar(AuditoriaTipo.RESERVA_CRIADA, "Reserva de " + alvo + "/" + item.getQuantidade() + " un. de \""
        + item.getDescricaoHistorica() + "\" no pedido #" + pedido.getId(), "PEDIDO", pedido.getId());
    return reserva;
  }

  /** Reserva um único item (usado depois do recebimento de uma encomenda). */
  @Transactional
  public ReservaEstoque reservarItem(Pedido pedido, ItemPedido item) {
    Unidade u = unidade(item);
    Travada t = travar(u, item.getDescricaoHistorica());
    long disponivel = t.fisico() - reservado(u);
    if (item.getQuantidade() > disponivel) {
      throw new NegocioException("Estoque disponível insuficiente para reservar \"" + item.getDescricaoHistorica()
          + "\": disponível " + Math.max(disponivel, 0) + ", necessário " + item.getQuantidade() + ".");
    }
    var reserva = novaReserva(pedido, item);
    auditoria.registrar(AuditoriaTipo.RESERVA_CRIADA,
        "Reserva criada para \"" + item.getDescricaoHistorica() + "\" no pedido #" + pedido.getId(), "PEDIDO",
        pedido.getId());
    return reserva;
  }

  /** Libera as reservas ativas do pedido (cancelamento). Reservas já consumidas por saída não são tocadas. */
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

  // =====================================================================
  // Movimentações do saldo físico
  // =====================================================================

  /**
   * Saída do pedido (entrega/retirada): converte as reservas ATIVAS em baixa física, uma única vez.
   * Exige que TODOS os itens tenham reserva ativa integral (não existe saída parcial) e impede saldo
   * negativo. Tudo ou nada: qualquer falha desfaz a transação.
   */
  @Transactional
  public List<MovimentacaoEstoque> baixarPorSaida(Pedido pedido, User usuario, String chave) {
    var ativas = reservaRepo.findByPedidoIdAndStatus(pedido.getId(), StatusReserva.ATIVA);

    List<String> faltantes = new ArrayList<>();
    for (ItemPedido item : pedido.getItens()) {
      boolean coberto = ativas.stream().anyMatch(r -> r.getItem().getId().equals(item.getId())
          && r.getQuantidade().equals(item.getQuantidade()));
      if (!coberto) {
        faltantes.add(item.getDescricaoHistorica()
            + (item.getModalidade() == ModalidadeItem.ENCOMENDA ? " (encomenda ainda não recebida por completo)" : ""));
      }
    }
    if (!faltantes.isEmpty()) {
      throw new NegocioException("A saída exige todos os itens disponíveis e reservados (não há entrega parcial). "
          + "Sem reserva ativa: " + String.join("; ", faltantes) + ".");
    }

    Map<Unidade, List<ReservaEstoque>> porUnidade = new TreeMap<>();
    for (ReservaEstoque r : ativas) {
      Unidade u = r.getVariacao() != null ? new Unidade(true, r.getVariacao().getId())
          : new Unidade(false, r.getProduto().getId());
      porUnidade.computeIfAbsent(u, k -> new ArrayList<>()).add(r);
    }

    List<MovimentacaoEstoque> movimentos = new ArrayList<>();
    for (var e : porUnidade.entrySet()) {
      List<ReservaEstoque> reservas = e.getValue();
      String nome = reservas.get(0).getItem().getDescricaoHistorica();
      Travada t = travar(e.getKey(), nome);
      int saldo = t.fisico();
      int total = reservas.stream().mapToInt(ReservaEstoque::getQuantidade).sum();
      if (total > saldo) {
        throw new NegocioException("Saldo físico insuficiente para \"" + nome + "\": físico " + saldo
            + ", necessário " + total + ". A saída não foi registrada.");
      }
      for (ReservaEstoque r : reservas) {
        int anterior = saldo;
        saldo -= r.getQuantidade();
        movimentos.add(movimentacaoRepo.save(MovimentacaoEstoque.builder()
            .tipo(TipoMovimentacao.SAIDA_VENDA).produto(t.produto()).variacao(t.variacao())
            .quantidade(-r.getQuantidade()).saldoAnterior(anterior).saldoPosterior(saldo)
            .pedidoId(pedido.getId()).itemId(r.getItem().getId())
            .motivo("Saída da venda #" + pedido.getId()).usuario(usuario).build()));
        r.setStatus(StatusReserva.CONSUMIDA);
        r.setLiberadaEm(Instant.now());
        r.setMotivoLiberacao("Consumida pela saída");
      }
      aplicar(t, saldo);
    }
    return movimentos;
  }

  /** Entrada física (recebimento de encomenda, devolução apta para revenda). */
  @Transactional
  public MovimentacaoEstoque registrarEntrada(TipoMovimentacao tipo, Produto produto, ProdutoVariacao variacao,
      int quantidade, Long pedidoId, Long itemId, Long encomendaId, Long ocorrenciaId, String motivo,
      User usuario) {
    if (quantidade <= 0) {
      throw new NegocioException("A quantidade de entrada deve ser maior que zero.");
    }
    Unidade u = variacao != null ? new Unidade(true, variacao.getId()) : new Unidade(false, produto.getId());
    Travada t = travar(u, nomeUnidade(produto, variacao));
    int anterior = t.fisico();
    int posterior = anterior + quantidade;
    aplicar(t, posterior);
    return movimentacaoRepo.save(MovimentacaoEstoque.builder()
        .tipo(tipo).produto(t.produto()).variacao(t.variacao()).quantidade(quantidade)
        .saldoAnterior(anterior).saldoPosterior(posterior).pedidoId(pedidoId).itemId(itemId)
        .encomendaId(encomendaId).ocorrenciaId(ocorrenciaId).motivo(motivo).usuario(usuario).build());
  }

  /**
   * Contagem de inventário: compara o físico contado com o saldo do sistema e ajusta a diferença.
   * Motivo obrigatório; o saldo não pode ficar abaixo do que já está reservado; repetir a mesma chave
   * não duplica o ajuste.
   */
  @Transactional
  public MovimentacaoView ajustarContagem(Long produtoId, Long variacaoId, Integer contado, String motivo,
      String chave) {
    if (chave == null || chave.isBlank()) {
      throw new NegocioException("Informe a chave de idempotência do ajuste (cabeçalho Idempotency-Key).");
    }
    var existente = movimentacaoRepo.findByChave(chave.trim());
    if (existente.isPresent()) {
      return view(existente.get());
    }
    if (motivo == null || motivo.isBlank()) {
      throw new NegocioException("Informe o motivo do ajuste de inventário.");
    }
    if (contado == null || contado < 0) {
      throw new NegocioException("A quantidade contada não pode ser negativa.");
    }
    User ator = usuarioAtual.get();

    Produto produto = produtoRepo.findById(produtoId)
        .orElseThrow(() -> new NegocioException("Produto não encontrado."));
    ProdutoVariacao variacao = null;
    if (variacaoId != null) {
      variacao = produto.getVariacoes().stream().filter(v -> variacaoId.equals(v.getId())).findFirst()
          .orElseThrow(() -> new NegocioException("A variação não pertence ao produto."));
    } else if (!produto.getVariacoes().isEmpty()) {
      throw new NegocioException("O produto possui variações: informe a variação contada.");
    }

    Unidade u = variacao != null ? new Unidade(true, variacao.getId()) : new Unidade(false, produto.getId());
    Travada t = travarPermitindoNulo(u);
    long reservado = reservado(u);
    if (contado < reservado) {
      throw new NegocioException("A contagem (" + contado + ") é menor que o total já reservado (" + reservado
          + "). Cancele ou ajuste as reservas antes de reduzir o estoque.");
    }
    Integer anterior = t.fisico();
    if (anterior != null && anterior.equals(contado)) {
      throw new NegocioException("A contagem é igual ao saldo do sistema (" + anterior + "): nada a ajustar.");
    }
    int delta = anterior == null ? 0 : contado - anterior;
    aplicar(t, contado);
    var mov = movimentacaoRepo.save(MovimentacaoEstoque.builder()
        .tipo(TipoMovimentacao.AJUSTE_INVENTARIO).produto(t.produto()).variacao(t.variacao()).quantidade(delta)
        .saldoAnterior(anterior).saldoPosterior(contado).motivo(motivo.trim()).usuario(ator)
        .chave(chave.trim()).build());
    auditoria.registrar(AuditoriaTipo.ESTOQUE_AJUSTADO,
        "Inventário de \"" + nomeUnidade(t.produto(), t.variacao()) + "\": "
            + (anterior == null ? "saldo definido" : "de " + anterior) + " para " + contado + " (" + motivo.trim() + ")",
        "PRODUTO", t.produto().getId());
    return view(mov);
  }

  // =====================================================================
  // Internos
  // =====================================================================

  private ReservaEstoque novaReserva(Pedido pedido, ItemPedido item) {
    return reservaRepo.save(ReservaEstoque.builder()
        .pedido(pedido).item(item).produto(item.getProduto()).variacao(item.getVariacao())
        .quantidade(item.getQuantidade()).status(StatusReserva.ATIVA).build());
  }

  private long reservado(Unidade u) {
    return u.variacao() ? reservaRepo.somaPorVariacao(u.id(), StatusReserva.ATIVA)
        : reservaRepo.somaPorProdutoSemVariacao(u.id(), StatusReserva.ATIVA);
  }

  /** Trava a unidade e exige saldo informado. */
  private Travada travar(Unidade u, String nome) {
    Travada t = travarPermitindoNulo(u);
    if (t.fisico() == null) {
      throw new NegocioException("A unidade \"" + nome
          + "\" não tem saldo de estoque cadastrado; faça a contagem de inventário para definir o saldo.");
    }
    return t;
  }

  private Travada travarPermitindoNulo(Unidade u) {
    // Grava alterações pendentes antes de reler a linha sob lock; sem isto, o refresh as descartaria
    // (ex.: entrada de encomenda seguida da reserva do item na mesma transação).
    em.flush();
    if (u.variacao()) {
      ProdutoVariacao v = variacaoRepo.findById(u.id())
          .orElseThrow(() -> new NegocioException("Variação não encontrada."));
      em.refresh(v, LockModeType.PESSIMISTIC_WRITE);
      return new Travada(v.getProduto(), v, v.getEstoque());
    }
    Produto p = produtoRepo.findById(u.id()).orElseThrow(() -> new NegocioException("Produto não encontrado."));
    em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
    return new Travada(p, null, p.getEstoque());
  }

  /** Grava o novo saldo na fonte autoritativa e atualiza o agregado do produto quando houver variações. */
  private void aplicar(Travada t, int novoSaldo) {
    if (t.variacao() != null) {
      t.variacao().setEstoque(novoSaldo);
      var variacoes = t.produto().getVariacoes();
      if (variacoes.stream().allMatch(v -> v.getEstoque() != null)) {
        t.produto().setEstoque(variacoes.stream().mapToInt(ProdutoVariacao::getEstoque).sum());
      }
    } else {
      t.produto().setEstoque(novoSaldo);
    }
  }

  private Unidade unidade(ItemPedido i) {
    return i.getVariacao() != null ? new Unidade(true, i.getVariacao().getId())
        : new Unidade(false, i.getProduto().getId());
  }

  private MovimentacaoView view(MovimentacaoEstoque m) {
    return new MovimentacaoView(m.getId(), m.getTipo(), m.getProduto().getId(), m.getProduto().getNome(),
        m.getVariacao() == null ? null : m.getVariacao().getId(),
        m.getVariacao() == null ? null : descricao(m.getVariacao()), m.getQuantidade(), m.getSaldoAnterior(),
        m.getSaldoPosterior(), m.getPedidoId(), m.getMotivo(),
        m.getUsuario() == null ? null : (m.getUsuario().getNome() != null ? m.getUsuario().getNome()
            : m.getUsuario().getEmail()),
        m.getCriadoEm());
  }

  private static String nomeUnidade(Produto p, ProdutoVariacao v) {
    return v == null ? p.getNome() : p.getNome() + " — " + descricao(v);
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
