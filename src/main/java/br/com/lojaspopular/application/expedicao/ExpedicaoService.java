package br.com.lojaspopular.application.expedicao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.application.arquivo.ArquivoService;
import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.expedicao.enums.StatusEntregaRegistro;
import br.com.lojaspopular.domain.expedicao.enums.StatusMontagemRegistro;
import br.com.lojaspopular.domain.expedicao.enums.TipoEventoEntrega;
import br.com.lojaspopular.domain.expedicao.model.Entrega;
import br.com.lojaspopular.domain.expedicao.model.EntregaEvento;
import br.com.lojaspopular.domain.expedicao.model.Montagem;
import br.com.lojaspopular.domain.expedicao.repository.EntregaRepository;
import br.com.lojaspopular.domain.expedicao.repository.MontagemRepository;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusMontagem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AgendaItem;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EntregaEventoResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EntregaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.MontagemResponse;
import lombok.RequiredArgsConstructor;

/**
 * Saída (baixa de estoque), entrega/retirada e montagem de uma venda confirmada.
 *
 * <p>Regras fixas: frete gratuito (não há valor de frete); montagem inclusa, sem cobrança adicional; uma
 * única entrega por pedido cobrindo todos os itens (não há entrega parcial). A saída é bloqueada enquanto
 * a decisão D05 (pagamento exigido para expedir) estiver pendente.
 */
@Service
@RequiredArgsConstructor
public class ExpedicaoService {

  private final VendaAcesso acesso;
  private final EntregaRepository entregaRepo;
  private final MontagemRepository montagemRepo;
  private final EstoqueService estoque;
  private final ConfiguracaoComercialService config;
  private final ArquivoService arquivos;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;
  private final br.com.lojaspopular.application.financeiro.ComissaoService comissoes;

  // =====================================================================
  // Entrega / retirada
  // =====================================================================

  @Transactional
  public void agendarEntrega(Long pedidoId, LocalDate data, PeriodoAgenda periodo, String equipe,
      String observacao) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    exigirConfirmada(p);
    if (entregaRepo.findByPedidoId(pedidoId).isPresent()) {
      throw new NegocioException("Esta venda já tem entrega agendada: use o reagendamento.");
    }
    exigirDataValida(data);
    Entrega e = Entrega.builder().pedido(p).tipo(p.getTipoEntrega()).status(StatusEntregaRegistro.AGENDADA)
        .dataPrevista(data).periodo(periodo).equipe(limpar(equipe)).observacao(limpar(observacao)).criadaPor(ator)
        .build();
    e.getEventos().add(evento(e, TipoEventoEntrega.AGENDADA, data, periodo, e.getEquipe(), null, null, ator));
    entregaRepo.save(e);
    p.setStatusEntrega(StatusEntrega.AGENDADA);
    auditoria.registrar(AuditoriaTipo.ENTREGA_AGENDADA,
        "Entrega da venda #" + pedidoId + " agendada para " + data + " (" + periodo + ")", "PEDIDO", pedidoId);
  }

  @Transactional
  public void reagendarEntrega(Long pedidoId, LocalDate data, PeriodoAgenda periodo, String equipe, String motivo) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    exigirConfirmada(p);
    Entrega e = entregaDe(pedidoId);
    if (e.getStatus() != StatusEntregaRegistro.AGENDADA && e.getStatus() != StatusEntregaRegistro.TENTATIVA_FRUSTRADA) {
      throw new NegocioException("Só é possível reagendar uma entrega agendada ou com tentativa frustrada.");
    }
    String m = VendaAcesso.exigirTexto(motivo, "Informe o motivo do reagendamento.");
    exigirDataValida(data);
    e.setDataPrevista(data);
    e.setPeriodo(periodo);
    if (equipe != null && !equipe.isBlank()) {
      e.setEquipe(equipe.trim());
    }
    e.setStatus(StatusEntregaRegistro.AGENDADA);
    e.getEventos().add(evento(e, TipoEventoEntrega.REAGENDADA, data, periodo, e.getEquipe(), m, null, ator));
    p.setStatusEntrega(StatusEntrega.AGENDADA);
    auditoria.registrar(AuditoriaTipo.ENTREGA_REAGENDADA,
        "Entrega da venda #" + pedidoId + " reagendada para " + data + ": " + m, "PEDIDO", pedidoId);
  }

  /**
   * Registra a saída: converte as reservas em baixa física (uma única vez, tudo ou nada). Exige a regra D05
   * definida e, se ela exigir pagamento quitado, o pagamento do pedido. Repetir a chamada com a mesma chave
   * devolve o mesmo resultado sem nova baixa.
   */
  @Transactional
  public void registrarSaida(Long pedidoId, String chave) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência da saída (cabeçalho Idempotency-Key).");
    Pedido p = acesso.travar(pedidoId, ator);
    Entrega e = entregaRepo.findByPedidoId(pedidoId).orElse(null);

    if (e != null && (e.getStatus() == StatusEntregaRegistro.SAIU || e.getStatus() == StatusEntregaRegistro.ENTREGUE)
        && k.equals(p.getChaveSaida())) {
      return; // repetição idempotente
    }
    exigirConfirmada(p);
    if (e == null) {
      throw new NegocioException("Agende a entrega/retirada antes de registrar a saída.");
    }
    if (e.getStatus() != StatusEntregaRegistro.AGENDADA) {
      throw new NegocioException(e.getStatus() == StatusEntregaRegistro.TENTATIVA_FRUSTRADA
          ? "A última tentativa foi frustrada: reagende a entrega antes de nova saída."
          : "A saída já foi registrada para esta venda.");
    }

    boolean exigePagamento = config.exigirRegraPagamentoExpedir();
    if (exigePagamento && p.getStatusPagamento() != StatusPagamento.PAGO) {
      throw new NegocioException(
          "A regra definida exige pagamento quitado para a saída e esta venda ainda não está paga.");
    }

    if (p.getSaidaRealizadaEm() == null) {
      estoque.baixarPorSaida(p, ator, k);
      p.setSaidaRealizadaEm(Instant.now());
    }
    p.setChaveSaida(k);
    e.setStatus(StatusEntregaRegistro.SAIU);
    e.setSaidaEm(Instant.now());
    e.getEventos().add(evento(e, TipoEventoEntrega.SAIDA, e.getDataPrevista(), e.getPeriodo(), e.getEquipe(), null,
        null, ator));
    p.setStatusEntrega(StatusEntrega.SAIU);
    auditoria.registrar(AuditoriaTipo.SAIDA_REGISTRADA,
        "Saída da venda #" + pedidoId + " registrada (baixa de estoque)", "PEDIDO", pedidoId);
  }

  /** Tentativa frustrada: a venda continua pendente e precisa ser reagendada. */
  @Transactional
  public void registrarTentativaFrustrada(Long pedidoId, String motivo) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    Entrega e = entregaDe(pedidoId);
    if (e.getStatus() != StatusEntregaRegistro.SAIU) {
      throw new NegocioException("Só é possível registrar tentativa frustrada de uma entrega que já saiu.");
    }
    String m = VendaAcesso.exigirTexto(motivo, "Informe o motivo da tentativa frustrada.");
    e.setStatus(StatusEntregaRegistro.TENTATIVA_FRUSTRADA);
    e.getEventos().add(evento(e, TipoEventoEntrega.TENTATIVA_FRUSTRADA, e.getDataPrevista(), e.getPeriodo(),
        e.getEquipe(), m, null, ator));
    p.setStatusEntrega(StatusEntrega.TENTATIVA_FRUSTRADA);
    auditoria.registrar(AuditoriaTipo.ENTREGA_FRUSTRADA,
        "Tentativa de entrega da venda #" + pedidoId + " frustrada: " + m, "PEDIDO", pedidoId);
  }

  /** Conclusão com comprovação: nome de quem recebeu e/ou arquivo (foto ou PDF). */
  @Transactional
  public void concluirEntrega(Long pedidoId, String recebedorNome, String observacao, MultipartFile arquivo) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    Entrega e = entregaDe(pedidoId);
    if (e.getStatus() != StatusEntregaRegistro.SAIU) {
      throw new NegocioException("Só é possível concluir uma entrega que já saiu.");
    }
    boolean temArquivo = arquivo != null && !arquivo.isEmpty();
    if ((recebedorNome == null || recebedorNome.isBlank()) && !temArquivo) {
      throw new NegocioException("Informe a comprovação: o nome de quem recebeu e/ou anexe a foto ou o documento.");
    }
    String caminho = temArquivo ? arquivos.salvar(arquivo, "entregas/" + pedidoId) : null;
    e.setStatus(StatusEntregaRegistro.ENTREGUE);
    e.setConcluidaEm(Instant.now());
    e.setRecebedorNome(limpar(recebedorNome));
    e.setComprovanteArquivo(caminho);
    e.setObservacaoConclusao(limpar(observacao));
    e.getEventos().add(evento(e, TipoEventoEntrega.ENTREGUE, e.getDataPrevista(), e.getPeriodo(), e.getEquipe(),
        limpar(observacao), caminho, ator));
    p.setStatusEntrega(StatusEntrega.ENTREGUE);
    comissoes.reavaliar(p);
    // Status legado acompanha a entrega concluída (sem presumir quitação: o pagamento tem status próprio).
    if (p.getStatus() == PedidoStatus.CRIADO) {
      p.setStatus(PedidoStatus.ENTREGUE);
    }
    auditoria.registrar(AuditoriaTipo.ENTREGA_CONCLUIDA,
        "Entrega da venda #" + pedidoId + " concluída (recebido por " + (e.getRecebedorNome() == null ? "—"
            : e.getRecebedorNome()) + ")", "PEDIDO", pedidoId);
  }

  // =====================================================================
  // Montagem (inclusa, sem cobrança adicional)
  // =====================================================================

  @Transactional
  public void agendarMontagem(Long pedidoId, LocalDate data, PeriodoAgenda periodo, String responsavel,
      String observacao) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    exigirConfirmada(p);
    exigirDataValida(data);
    Montagem m = montagemRepo.findByPedidoId(pedidoId).orElse(null);
    if (m != null && m.getStatus() != StatusMontagemRegistro.AGENDADA) {
      throw new NegocioException(m.getStatus() == StatusMontagemRegistro.CONCLUIDA
          ? "A montagem desta venda já foi concluída." : "A montagem foi marcada como não necessária.");
    }
    if (m == null) {
      m = Montagem.builder().pedido(p).build();
    }
    m.setStatus(StatusMontagemRegistro.AGENDADA);
    m.setDataPrevista(data);
    m.setPeriodo(periodo);
    m.setResponsavel(limpar(responsavel));
    m.setObservacao(limpar(observacao));
    m.setAtualizadaPor(ator);
    montagemRepo.save(m);
    p.setStatusMontagem(StatusMontagem.AGENDADA);
    auditoria.registrar(AuditoriaTipo.MONTAGEM_AGENDADA,
        "Montagem da venda #" + pedidoId + " agendada para " + data + " (" + periodo + ")", "PEDIDO", pedidoId);
  }

  /** Conclusão exige entrega concluída e evidência (arquivo e/ou observação). */
  @Transactional
  public void concluirMontagem(Long pedidoId, String observacao, MultipartFile arquivo) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    Montagem m = montagemRepo.findByPedidoId(pedidoId)
        .orElseThrow(() -> new NegocioException("Esta venda não tem montagem agendada."));
    if (m.getStatus() != StatusMontagemRegistro.AGENDADA) {
      throw new NegocioException("A montagem não está agendada.");
    }
    if (p.getStatusEntrega() != StatusEntrega.ENTREGUE) {
      throw new NegocioException("A montagem só pode ser concluída depois da entrega.");
    }
    boolean temArquivo = arquivo != null && !arquivo.isEmpty();
    if ((observacao == null || observacao.isBlank()) && !temArquivo) {
      throw new NegocioException("Registre a evidência da montagem: anexe a foto ou descreva o serviço realizado.");
    }
    m.setEvidenciaArquivo(temArquivo ? arquivos.salvar(arquivo, "montagens/" + pedidoId) : null);
    m.setObservacao(limpar(observacao) != null ? limpar(observacao) : m.getObservacao());
    m.setStatus(StatusMontagemRegistro.CONCLUIDA);
    m.setConcluidaEm(Instant.now());
    m.setAtualizadaPor(ator);
    p.setStatusMontagem(StatusMontagem.CONCLUIDA);
    auditoria.registrar(AuditoriaTipo.MONTAGEM_CONCLUIDA, "Montagem da venda #" + pedidoId + " concluída", "PEDIDO",
        pedidoId);
  }

  /** Marca a montagem como não necessária (ex.: produto que não precisa de montagem). Exige motivo. */
  @Transactional
  public void dispensarMontagem(Long pedidoId, String motivo) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    exigirConfirmada(p);
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo para dispensar a montagem.");
    Montagem m = montagemRepo.findByPedidoId(pedidoId).orElse(null);
    if (m != null && m.getStatus() == StatusMontagemRegistro.CONCLUIDA) {
      throw new NegocioException("A montagem já foi concluída.");
    }
    if (m == null) {
      m = Montagem.builder().pedido(p).build();
    }
    m.setStatus(StatusMontagemRegistro.NAO_NECESSARIA);
    m.setMotivoDispensa(mot);
    m.setDataPrevista(null);
    m.setPeriodo(null);
    m.setAtualizadaPor(ator);
    montagemRepo.save(m);
    p.setStatusMontagem(StatusMontagem.NAO_NECESSARIA);
    auditoria.registrar(AuditoriaTipo.MONTAGEM_DISPENSADA,
        "Montagem da venda #" + pedidoId + " marcada como não necessária: " + mot, "PEDIDO", pedidoId);
  }

  // =====================================================================
  // Cancelamento da venda e consultas
  // =====================================================================

  /** Chamado pelo cancelamento da venda (antes da saída): encerra entrega e montagem pendentes. */
  @Transactional
  public void cancelarDoPedido(Pedido pedido, User ator) {
    entregaRepo.findByPedidoId(pedido.getId()).ifPresent(e -> {
      if (e.getStatus() == StatusEntregaRegistro.AGENDADA) {
        e.setStatus(StatusEntregaRegistro.CANCELADA);
        e.getEventos().add(evento(e, TipoEventoEntrega.CANCELADA, e.getDataPrevista(), e.getPeriodo(), e.getEquipe(),
            "Venda cancelada", null, ator));
        pedido.setStatusEntrega(StatusEntrega.NAO_AGENDADA);
      }
    });
    montagemRepo.findByPedidoId(pedido.getId()).ifPresent(m -> {
      if (m.getStatus() == StatusMontagemRegistro.AGENDADA) {
        m.setStatus(StatusMontagemRegistro.NAO_NECESSARIA);
        m.setMotivoDispensa("Venda cancelada");
        pedido.setStatusMontagem(StatusMontagem.NAO_NECESSARIA);
      }
    });
  }

  @Transactional(readOnly = true)
  public EntregaResponse entregaDoPedido(Long pedidoId) {
    return entregaRepo.findByPedidoId(pedidoId).map(this::view).orElse(null);
  }

  @Transactional(readOnly = true)
  public MontagemResponse montagemDoPedido(Long pedidoId) {
    return montagemRepo.findByPedidoId(pedidoId).map(this::view).orElse(null);
  }

  /** Entregas/retiradas e montagens agendadas no período. */
  @Transactional(readOnly = true)
  public List<AgendaItem> agenda(LocalDate de, LocalDate ate) {
    if (de == null || ate == null || ate.isBefore(de)) {
      throw new NegocioException("Informe um período válido (data final não pode ser anterior à inicial).");
    }
    if (de.plusDays(92).isBefore(ate)) {
      throw new NegocioException("O período da agenda é limitado a 92 dias.");
    }
    var itens = new java.util.ArrayList<AgendaItem>();
    for (Entrega e : entregaRepo.agenda(de, ate)) {
      Pedido p = e.getPedido();
      itens.add(new AgendaItem(e.getTipo().name(), p.getId(), p.getCliente() == null ? null : p.getCliente().getNome(),
          e.getDataPrevista(), e.getPeriodo(), e.getEquipe(), e.getStatus().name(), endereco(p), e.getTipo().name()));
    }
    for (Montagem m : montagemRepo.agenda(de, ate)) {
      Pedido p = m.getPedido();
      itens.add(new AgendaItem("MONTAGEM", p.getId(), p.getCliente() == null ? null : p.getCliente().getNome(),
          m.getDataPrevista(), m.getPeriodo(), m.getResponsavel(), m.getStatus().name(), endereco(p), null));
    }
    itens.sort(java.util.Comparator.comparing(AgendaItem::data).thenComparing(i -> i.periodo().ordinal())
        .thenComparing(AgendaItem::pedidoId));
    return itens;
  }

  /** Motivo pelo qual a saída está bloqueada (para a interface), ou {@code null} se pode sair. */
  @Transactional(readOnly = true)
  public String bloqueioDaSaida(Pedido p) {
    if (p.getStatusComercial() != StatusComercial.CONFIRMADA) {
      return null;
    }
    Entrega e = entregaRepo.findByPedidoId(p.getId()).orElse(null);
    if (e == null) {
      return "Agende a entrega/retirada antes de registrar a saída.";
    }
    if (e.getStatus() != StatusEntregaRegistro.AGENDADA) {
      return null;
    }
    var regra = config.obter().getExigePagamentoExpedir();
    if (regra == null) {
      return "Configuração pendente (D05): defina se o pagamento precisa estar quitado para a saída.";
    }
    if (regra && p.getStatusPagamento() != StatusPagamento.PAGO) {
      return "A regra exige pagamento quitado para a saída e esta venda ainda não está paga.";
    }
    if (p.getSaidaRealizadaEm() == null) {
      var sem = p.getItens().stream().filter(i -> estoque.reservasDoPedido(p.getId()).stream()
          .noneMatch(r -> r.getItem().getId().equals(i.getId())
              && r.getStatus() == br.com.lojaspopular.domain.estoque.enums.StatusReserva.ATIVA))
          .map(i -> i.getDescricaoHistorica()).toList();
      if (!sem.isEmpty()) {
        return "Itens sem reserva ativa (não há entrega parcial): " + String.join("; ", sem) + ".";
      }
    }
    return null;
  }

  // ---- internos ----

  private Entrega entregaDe(Long pedidoId) {
    return entregaRepo.findByPedidoId(pedidoId)
        .orElseThrow(() -> new NegocioException("Esta venda não tem entrega agendada."));
  }

  private void exigirConfirmada(Pedido p) {
    if (p.getStatusComercial() != StatusComercial.CONFIRMADA) {
      throw new NegocioException("A venda precisa estar confirmada.");
    }
  }

  private void exigirDataValida(LocalDate data) {
    if (data == null || data.isBefore(LocalDate.now())) {
      throw new NegocioException("A data não pode estar no passado.");
    }
  }

  private EntregaEvento evento(Entrega e, TipoEventoEntrega tipo, LocalDate data, PeriodoAgenda periodo,
      String equipe, String motivo, String arquivo, User usuario) {
    return EntregaEvento.builder().entrega(e).tipo(tipo).dataPrevista(data).periodo(periodo).equipe(equipe)
        .motivo(motivo).arquivo(arquivo).usuario(usuario).build();
  }

  private EntregaResponse view(Entrega e) {
    var eventos = e.getEventos().stream().map(ev -> new EntregaEventoResponse(ev.getId(), ev.getTipo(),
        ev.getDataPrevista(), ev.getPeriodo(), ev.getEquipe(), ev.getMotivo(), ev.getArquivo(),
        ev.getUsuario() == null ? null : nome(ev.getUsuario()), ev.getCriadoEm())).toList();
    int frustradas = (int) e.getEventos().stream().filter(x -> x.getTipo() == TipoEventoEntrega.TENTATIVA_FRUSTRADA)
        .count();
    return new EntregaResponse(e.getId(), e.getTipo(), e.getStatus(), e.getDataPrevista(), e.getPeriodo(), e.getEquipe(),
        e.getObservacao(), e.getSaidaEm(), e.getConcluidaEm(), e.getRecebedorNome(), e.getComprovanteArquivo(),
        e.getObservacaoConclusao(), e.getPedido().getSaidaRealizadaEm() != null, frustradas, "Gratuito", eventos);
  }

  private MontagemResponse view(Montagem m) {
    return new MontagemResponse(m.getId(), m.getStatus(), m.getDataPrevista(), m.getPeriodo(), m.getResponsavel(),
        m.getObservacao(), m.getConcluidaEm(), m.getEvidenciaArquivo(), m.getMotivoDispensa(),
        "Inclusa, sem cobrança adicional");
  }

  private static String endereco(Pedido p) {
    if (p.getEntregaLogradouro() == null) {
      return null;
    }
    return p.getEntregaLogradouro() + (p.getEntregaNumero() == null ? "" : ", " + p.getEntregaNumero())
        + (p.getEntregaBairro() == null ? "" : " - " + p.getEntregaBairro())
        + (p.getEntregaCidade() == null ? "" : " - " + p.getEntregaCidade())
        + (p.getEntregaUf() == null ? "" : "/" + p.getEntregaUf());
  }

  private static String nome(User u) {
    return u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail();
  }

  private static String limpar(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
