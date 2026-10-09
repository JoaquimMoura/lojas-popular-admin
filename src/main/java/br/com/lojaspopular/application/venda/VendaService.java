package br.com.lojaspopular.application.venda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.encomenda.EncomendaService;
import br.com.lojaspopular.application.financeiro.ComissaoService;
import br.com.lojaspopular.application.financeiro.RecebimentoService;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.expedicao.ExpedicaoService;
import br.com.lojaspopular.application.posvenda.PosVendaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.model.EnderecoCliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.enums.StatusSolicitacaoDesconto;
import br.com.lojaspopular.domain.comercial.model.SolicitacaoDesconto;
import br.com.lojaspopular.domain.comercial.repository.SolicitacaoDescontoRepository;
import br.com.lojaspopular.domain.estoque.enums.StatusReserva;
import br.com.lojaspopular.domain.expedicao.enums.StatusEntregaRegistro;
import br.com.lojaspopular.domain.expedicao.enums.StatusMontagemRegistro;
import br.com.lojaspopular.domain.estoque.model.ReservaEstoque;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.enums.StatusMontagem;
import br.com.lojaspopular.domain.order.enums.StatusPagamento;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.venda.dto.VendaDtos.Acoes;
import br.com.lojaspopular.web.venda.dto.VendaDtos.DescontoResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.EnderecoEntrega;
import br.com.lojaspopular.web.venda.dto.VendaDtos.HistoricoResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ItemResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.Pagina;
import br.com.lojaspopular.web.venda.dto.VendaDtos.PessoaResumo;
import br.com.lojaspopular.web.venda.dto.VendaDtos.ReservaResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaRequest;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaResumoResponse;
import lombok.RequiredArgsConstructor;

/**
 * Ciclo de vida da venda sobre o agregado {@link Pedido}:
 * registrar (rascunho) → aprovar desconto, se necessário → confirmar (reserva de estoque) → cancelar.
 *
 * <p>Todos os valores monetários são calculados no servidor com {@link BigDecimal} e a política de
 * arredondamento configurada. Preços e condição de pagamento ficam gravados no pedido: mudar a tabela
 * depois não altera vendas existentes.
 */
@Service
@RequiredArgsConstructor
public class VendaService {

  private static final BigDecimal CEM = new BigDecimal("100");
  private static final String ENTIDADE = "PEDIDO";
  private static final Set<StatusComercial> EDITAVEIS = EnumSet.of(StatusComercial.RASCUNHO,
      StatusComercial.AGUARDANDO_APROVACAO);

  private final PedidoRepository pedidoRepo;
  private final ClienteRepository clienteRepo;
  private final UserRepository userRepo;
  private final ProdutoRepository produtoRepo;
  private final SolicitacaoDescontoRepository descontoRepo;
  private final ConfiguracaoComercialService config;
  private final EstoqueService estoque;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;
  private final VendaAcesso acesso;
  private final EncomendaService encomendas;
  private final ExpedicaoService expedicao;
  private final PosVendaService posVenda;
  private final ComissaoService comissoes;
  private final RecebimentoService recebimentoService;
  private final br.com.lojaspopular.application.financeiro.CustoService custos;

  // =====================================================================
  // Registro e edição
  // =====================================================================

  @Transactional
  public VendaDetalheResponse registrar(VendaRequest req) {
    User ator = usuarioAtual.get();

    String chave = limpar(req.chaveIdempotencia());
    if (chave != null) {
      var existente = pedidoRepo.findByChaveCriacao(chave);
      if (existente.isPresent()) {
        if (!existente.get().getUsuario().getId().equals(ator.getId())) {
          throw new NegocioException("Chave de idempotência já utilizada por outra venda.");
        }
        return detalhe(existente.get(), ator);
      }
    }

    Pedido p = Pedido.builder()
        .usuario(ator)
        .status(PedidoStatus.CRIADO)
        .statusComercial(StatusComercial.RASCUNHO)
        .statusPagamento(StatusPagamento.PENDENTE)
        .statusEntrega(StatusEntrega.NAO_AGENDADA)
        .statusMontagem(StatusMontagem.NAO_AGENDADA)
        .chaveCriacao(chave)
        .criadoEm(Instant.now())
        .build();
    preencher(p, req, ator);
    p = pedidoRepo.save(p);
    avaliarDesconto(p, ator, req.justificativaDesconto());

    auditoria.registrar(AuditoriaTipo.VENDA_REGISTRADA,
        "Venda #" + p.getId() + " registrada (total " + p.getTotal() + ")", ENTIDADE, p.getId());
    return detalhe(p, ator);
  }

  @Transactional
  public VendaDetalheResponse atualizar(Long id, VendaRequest req) {
    User ator = usuarioAtual.get();
    Pedido p = travar(id, ator);
    exigirEditavel(p);
    preencher(p, req, ator);
    avaliarDesconto(p, ator, req.justificativaDesconto());
    auditoria.registrar(AuditoriaTipo.VENDA_ALTERADA, "Venda #" + p.getId() + " alterada (total " + p.getTotal() + ")",
        ENTIDADE, p.getId());
    return detalhe(p, ator);
  }

  /** Refaz os preços dos itens com o catálogo e a condição de pagamento vigentes. */
  @Transactional
  public VendaDetalheResponse recalcular(Long id) {
    User ator = usuarioAtual.get();
    Pedido p = travar(id, ator);
    exigirEditavel(p);

    Arredondamento arred = config.exigirArredondamento();
    var condicao = config.exigirCondicao(p.getFormaPagamento(), p.getParcelas());
    p.setAjusteCondicaoPercentual(condicao.getAjustePercentual());
    for (ItemPedido i : p.getItens()) {
      BigDecimal base = precoBase(i.getProduto(), i.getVariacao());
      i.setPrecoBase(base);
      i.setPrecoUnitario(aplicar(base, condicao.getAjustePercentual(), arred));
      i.setTotal(i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())));
    }
    totalizar(p);
    avaliarDesconto(p, ator, null);
    auditoria.registrar(AuditoriaTipo.VENDA_ALTERADA, "Preços da venda #" + p.getId() + " recalculados",
        ENTIDADE, p.getId());
    return detalhe(p, ator);
  }

  // =====================================================================
  // Desconto
  // =====================================================================

  @Transactional
  public VendaDetalheResponse decidirDesconto(Long id, boolean aprovar, String motivo) {
    User ator = usuarioAtual.get();
    if (!UsuarioAtual.isGestor(ator)) {
      throw new AccessDeniedException("Somente gerente ou proprietário decide descontos.");
    }
    Pedido p = travar(id, ator);
    if (p.getStatusComercial() != StatusComercial.AGUARDANDO_APROVACAO) {
      throw new NegocioException("Esta venda não está aguardando aprovação de desconto.");
    }
    var pendentes = descontoRepo.findByPedidoIdAndStatusIn(p.getId(), EnumSet.of(StatusSolicitacaoDesconto.PENDENTE));
    if (pendentes.isEmpty()) {
      throw new NegocioException("Não há solicitação de desconto pendente para esta venda.");
    }
    if (!aprovar && (motivo == null || motivo.isBlank())) {
      throw new NegocioException("Informe o motivo da rejeição do desconto.");
    }
    SolicitacaoDesconto s = pendentes.get(0);
    if (s.getSolicitante().getId().equals(ator.getId()) && !UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new NegocioException("O solicitante não pode aprovar o próprio desconto.");
    }
    s.setStatus(aprovar ? StatusSolicitacaoDesconto.APROVADA : StatusSolicitacaoDesconto.REJEITADA);
    s.setDecididoPor(ator);
    s.setDecididoEm(Instant.now());
    s.setMotivoDecisao(limpar(motivo));
    p.setStatusComercial(StatusComercial.RASCUNHO);
    auditoria.registrar(aprovar ? AuditoriaTipo.DESCONTO_APROVADO : AuditoriaTipo.DESCONTO_REJEITADO,
        "Desconto de " + s.getValorDesconto() + " na venda #" + p.getId() + (aprovar ? " aprovado" : " rejeitado"),
        ENTIDADE, p.getId());
    return detalhe(p, ator);
  }

  /**
   * Reavalia o desconto do rascunho. Aprovações/solicitações de valores diferentes são invalidadas;
   * acima do limite gera nova solicitação e a venda aguarda aprovação.
   */
  private void avaliarDesconto(Pedido p, User ator, String justificativa) {
    BigDecimal desconto = p.getDesconto();
    var abertas = descontoRepo.findByPedidoIdAndStatusIn(p.getId(),
        EnumSet.of(StatusSolicitacaoDesconto.PENDENTE, StatusSolicitacaoDesconto.APROVADA));

    if (desconto.signum() == 0) {
      invalidar(abertas, null, p);
      p.setStatusComercial(StatusComercial.RASCUNHO);
      return;
    }

    BigDecimal limite = config.exigirLimiteDesconto();
    BigDecimal pct = percentual(desconto, p.getSubtotal());
    if (pct.compareTo(limite) <= 0) {
      invalidar(abertas, null, p);
      p.setStatusComercial(StatusComercial.RASCUNHO);
      return;
    }

    SolicitacaoDesconto vigente = abertas.stream()
        .filter(s -> s.getValorDesconto().compareTo(desconto) == 0 && s.getSubtotalBase().compareTo(p.getSubtotal()) == 0)
        .findFirst().orElse(null);
    invalidar(abertas, vigente, p);

    if (vigente != null) {
      p.setStatusComercial(vigente.getStatus() == StatusSolicitacaoDesconto.APROVADA ? StatusComercial.RASCUNHO
          : StatusComercial.AGUARDANDO_APROVACAO);
      return;
    }
    descontoRepo.save(SolicitacaoDesconto.builder()
        .pedido(p).solicitante(ator).valorDesconto(desconto).percentual(pct).subtotalBase(p.getSubtotal())
        .status(StatusSolicitacaoDesconto.PENDENTE).justificativa(limpar(justificativa)).build());
    p.setStatusComercial(StatusComercial.AGUARDANDO_APROVACAO);
    auditoria.registrar(AuditoriaTipo.DESCONTO_SOLICITADO,
        "Desconto de " + desconto + " (" + pct + "%) solicitado na venda #" + p.getId() + "; limite " + limite + "%",
        ENTIDADE, p.getId());
  }

  private void invalidar(List<SolicitacaoDesconto> abertas, SolicitacaoDesconto manter, Pedido p) {
    for (SolicitacaoDesconto s : abertas) {
      if (s == manter) {
        continue;
      }
      s.setStatus(StatusSolicitacaoDesconto.INVALIDADA);
      auditoria.registrar(AuditoriaTipo.DESCONTO_INVALIDADO,
          "Solicitação de desconto #" + s.getId() + " invalidada por alteração da venda #" + p.getId(), ENTIDADE,
          p.getId());
    }
  }

  /** Desconto acima do limite só é aceito com aprovação vigente para exatamente estes valores. */
  private void exigirDescontoAutorizado(Pedido p) {
    if (p.getDesconto().signum() == 0) {
      return;
    }
    BigDecimal limite = config.exigirLimiteDesconto();
    if (percentual(p.getDesconto(), p.getSubtotal()).compareTo(limite) <= 0) {
      return;
    }
    boolean aprovado = descontoRepo.findByPedidoIdAndStatusIn(p.getId(), EnumSet.of(StatusSolicitacaoDesconto.APROVADA))
        .stream().anyMatch(s -> s.getValorDesconto().compareTo(p.getDesconto()) == 0
            && s.getSubtotalBase().compareTo(p.getSubtotal()) == 0);
    if (!aprovado) {
      throw new NegocioException("O desconto está acima do limite e não possui aprovação válida para estes valores.");
    }
  }

  // =====================================================================
  // Confirmação (reserva) e cancelamento
  // =====================================================================

  /**
   * Confirma a venda e reserva o estoque de pronta entrega, tudo na mesma transação.
   * Repetir a chamada com a mesma chave devolve a mesma venda sem duplicar reservas.
   */
  @Transactional
  public VendaDetalheResponse confirmar(Long id, String chave) {
    User ator = usuarioAtual.get();
    chave = limpar(chave);
    if (chave == null) {
      throw new NegocioException("Informe a chave de idempotência da confirmação (cabeçalho Idempotency-Key).");
    }
    Pedido p = travar(id, ator);

    if (p.getStatusComercial() == StatusComercial.CONFIRMADA) {
      if (chave.equals(p.getChaveConfirmacao())) {
        return detalhe(p, ator);
      }
      throw new NegocioException("Esta venda já foi confirmada.");
    }
    switch (p.getStatusComercial()) {
      case LEGADO -> throw new NegocioException("Pedido anterior à gestão de vendas: não pode ser confirmado.");
      case CANCELADA -> throw new NegocioException("Esta venda foi cancelada.");
      case AGUARDANDO_APROVACAO -> throw new NegocioException(
          "A venda aguarda a aprovação do desconto e não pode ser confirmada.");
      default -> {
      }
    }

    validarParaConfirmar(p);
    conferirPrecos(p);
    exigirDescontoAutorizado(p);
    estoque.reservar(p);
    encomendas.criarParaPedido(p);

    p.setStatusComercial(StatusComercial.CONFIRMADA);
    p.setChaveConfirmacao(chave);
    p.setConfirmadoEm(Instant.now());
    custos.congelar(p);
    comissoes.aoConfirmar(p);
    auditoria.registrar(AuditoriaTipo.VENDA_CONFIRMADA, "Venda #" + p.getId() + " confirmada (total " + p.getTotal() + ")",
        ENTIDADE, p.getId());
    pedidoRepo.saveAndFlush(p);
    return detalhe(p, ator);
  }

  @Transactional
  public VendaDetalheResponse cancelar(Long id, String motivo) {
    User ator = usuarioAtual.get();
    Pedido p = travar(id, ator);
    if (p.getStatusComercial() == StatusComercial.CANCELADA) {
      return detalhe(p, ator);
    }
    if (p.getStatusComercial() == StatusComercial.LEGADO) {
      throw new NegocioException("Pedido anterior à gestão de vendas: o cancelamento não é feito por esta tela.");
    }
    if (motivo == null || motivo.isBlank()) {
      throw new NegocioException("Informe o motivo do cancelamento.");
    }
    Set<Role> autorizados = config.exigirPerfisCancelamento();
    if (autorizados.stream().noneMatch(r -> UsuarioAtual.tem(ator, r))) {
      throw new AccessDeniedException("Seu perfil não está autorizado a cancelar vendas.");
    }
    if (p.getSaidaRealizadaEm() != null) {
      throw new NegocioException(
          "O produto já saiu para entrega/retirada: não é possível cancelar. Registre uma devolução em Pós-venda.");
    }
    if (recebimentoService.totalRecebido(p.getId()).signum() > 0) {
      throw new NegocioException(
          "Venda com recebimentos registrados: estorne os recebimentos lançados por engano ou trate a restituição conforme "
              + "a política D07/D09 (ainda não definida) antes de cancelar. Cancelamento bloqueado.");
    }

    estoque.liberar(p, "Cancelamento da venda: " + motivo.trim());
    comissoes.aoCancelar(p);
    encomendas.cancelarDoPedido(p);
    expedicao.cancelarDoPedido(p, ator);
    invalidar(descontoRepo.findByPedidoIdAndStatusIn(p.getId(),
        EnumSet.of(StatusSolicitacaoDesconto.PENDENTE, StatusSolicitacaoDesconto.APROVADA)), null, p);
    p.setStatusComercial(StatusComercial.CANCELADA);
    p.setStatus(PedidoStatus.CANCELADO);
    p.setCanceladoEm(Instant.now());
    p.setCanceladoPor(ator);
    p.setMotivoCancelamento(motivo.trim());
    auditoria.registrar(AuditoriaTipo.VENDA_CANCELADA, "Venda #" + p.getId() + " cancelada: " + motivo.trim(), ENTIDADE,
        p.getId());
    return detalhe(p, ator);
  }

  // =====================================================================
  // Consultas
  // =====================================================================

  @Transactional(readOnly = true)
  public VendaDetalheResponse obter(Long id) {
    User ator = usuarioAtual.get();
    Pedido p = pedidoRepo.findById(id).orElseThrow(() -> new NotFoundException("Venda não encontrada"));
    verificarAcesso(p, ator);
    return detalhe(p, ator);
  }

  @Transactional(readOnly = true)
  public Pagina<VendaResumoResponse> listar(StatusComercial status, String q, int pagina, int tamanho) {
    User ator = usuarioAtual.get();
    var pageable = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 50));
    String termo = q == null ? "" : q.trim();
    var page = UsuarioAtual.isGestor(ator) ? pedidoRepo.listar(status, termo, pageable)
        : pedidoRepo.listarDoVendedor(ator.getId(), status, termo, pageable);
    var itens = page.getContent().stream().map(this::resumo).toList();
    return new Pagina<>(itens, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  // =====================================================================
  // Internos: montagem e validação
  // =====================================================================

  private void preencher(Pedido p, VendaRequest req, User ator) {
    Cliente cliente = clienteRepo.findById(req.clienteId())
        .orElseThrow(() -> new NegocioException("Cliente não encontrado."));
    if (!cliente.isAtivo()) {
      throw new NegocioException("O cliente selecionado está inativo.");
    }

    User vendedor = resolverVendedor(req.vendedorId(), ator);

    Arredondamento arred = config.exigirArredondamento();
    var condicao = config.exigirCondicao(req.formaPagamento(), req.parcelas());

    p.setCliente(cliente);
    p.setClienteNomeHist(cliente.getNome());
    p.setClienteCpfHist(cliente.getCpf());
    p.setClienteTelefoneHist(cliente.getTelefone());
    p.setVendedor(vendedor);
    p.setCanal(req.canal());
    p.setFormaPagamento(req.formaPagamento());
    p.setParcelas(req.parcelas());
    p.setAjusteCondicaoPercentual(condicao.getAjustePercentual());
    p.setObservacao(limpar(req.observacao()));
    definirEntrega(p, cliente, req);

    p.getItens().clear();
    for (ItemRequest ir : req.itens()) {
      p.getItens().add(montarItem(p, ir, condicao.getAjustePercentual(), arred));
    }

    BigDecimal desconto = req.desconto() == null ? BigDecimal.ZERO : req.desconto().setScale(2, RoundingMode.HALF_UP);
    p.setDesconto(desconto);
    totalizar(p);
  }

  private User resolverVendedor(Long vendedorId, User ator) {
    if (vendedorId == null || vendedorId.equals(ator.getId())) {
      return ator;
    }
    if (!UsuarioAtual.isGestor(ator)) {
      throw new NegocioException("O vendedor só pode registrar vendas em seu próprio nome.");
    }
    User v = userRepo.findById(vendedorId).orElseThrow(() -> new NegocioException("Vendedor não encontrado."));
    boolean operacional = UsuarioAtual.tem(v, Role.VENDEDOR) || UsuarioAtual.isGestor(v);
    if (!v.isEnabled() || !operacional) {
      throw new NegocioException("O usuário selecionado não pode ser vendedor responsável.");
    }
    return v;
  }

  private void definirEntrega(Pedido p, Cliente cliente, VendaRequest req) {
    p.setTipoEntrega(req.tipoEntrega());
    p.setEntregaCep(null);
    p.setEntregaLogradouro(null);
    p.setEntregaNumero(null);
    p.setEntregaComplemento(null);
    p.setEntregaBairro(null);
    p.setEntregaCidade(null);
    p.setEntregaUf(null);
    if (req.tipoEntrega() != TipoEntrega.ENTREGA) {
      return;
    }
    if (req.enderecoId() == null) {
      throw new NegocioException("Selecione o endereço de entrega.");
    }
    EnderecoCliente e = cliente.getEnderecos().stream().filter(x -> x.getId().equals(req.enderecoId())).findFirst()
        .orElseThrow(() -> new NegocioException("O endereço selecionado não pertence ao cliente."));
    p.setEntregaCep(e.getCep());
    p.setEntregaLogradouro(e.getLogradouro());
    p.setEntregaNumero(e.getNumero());
    p.setEntregaComplemento(e.getComplemento());
    p.setEntregaBairro(e.getBairro());
    p.setEntregaCidade(e.getCidade());
    p.setEntregaUf(e.getUf());
  }

  private ItemPedido montarItem(Pedido p, ItemRequest ir, BigDecimal ajuste, Arredondamento arred) {
    Produto produto = produtoRepo.findById(ir.produtoId())
        .orElseThrow(() -> new NegocioException("Produto não encontrado: " + ir.produtoId()));
    if (!produto.isAtiva()) {
      throw new NegocioException("O produto \"" + produto.getNome() + "\" está inativo.");
    }
    if (ir.quantidade() == null || ir.quantidade() <= 0) {
      throw new NegocioException("A quantidade deve ser maior que zero.");
    }
    if (!produto.getModalidade().aceita(ir.modalidade())) {
      throw new NegocioException("O produto \"" + produto.getNome() + "\" não é vendido em "
          + (ir.modalidade() == ModalidadeItem.ENCOMENDA ? "encomenda" : "pronta entrega") + ".");
    }

    ProdutoVariacao variacao = null;
    if (!produto.getVariacoes().isEmpty()) {
      if (ir.variacaoId() == null) {
        throw new NegocioException("Selecione a variação do produto \"" + produto.getNome() + "\".");
      }
      variacao = produto.getVariacoes().stream().filter(v -> ir.variacaoId().equals(v.getId())).findFirst()
          .orElseThrow(() -> new NegocioException("A variação não pertence ao produto \"" + produto.getNome() + "\"."));
    } else if (ir.variacaoId() != null) {
      throw new NegocioException("O produto \"" + produto.getNome() + "\" não possui variações.");
    }

    BigDecimal base = precoBase(produto, variacao);
    BigDecimal unitario = aplicar(base, ajuste, arred);
    String descricao = variacao == null ? produto.getNome()
        : produto.getNome() + " — " + EstoqueService.descricao(variacao);
    return ItemPedido.builder()
        .pedido(p).produto(produto).variacao(variacao)
        .skuHistorico(variacao != null && variacao.getSku() != null ? variacao.getSku() : produto.getSku())
        .descricaoHistorica(descricao.length() > 300 ? descricao.substring(0, 300) : descricao)
        .quantidade(ir.quantidade())
        .precoBase(base).precoUnitario(unitario)
        .total(unitario.multiply(BigDecimal.valueOf(ir.quantidade())))
        .modalidade(ir.modalidade())
        .build();
  }

  private BigDecimal precoBase(Produto produto, ProdutoVariacao variacao) {
    return Precos.base(produto, variacao);
  }

  static BigDecimal aplicar(BigDecimal base, BigDecimal ajustePercentual, Arredondamento arred) {
    return Precos.aplicar(base, ajustePercentual, arred);
  }

  private static BigDecimal percentual(BigDecimal desconto, BigDecimal subtotal) {
    return desconto.multiply(CEM).divide(subtotal, 4, RoundingMode.HALF_UP);
  }

  private void totalizar(Pedido p) {
    BigDecimal subtotal = p.getItens().stream().map(ItemPedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (p.getDesconto().compareTo(subtotal) > 0) {
      throw new NegocioException("O desconto não pode ser maior que o subtotal da venda.");
    }
    p.setSubtotal(subtotal);
    p.setFrete(BigDecimal.ZERO);
    p.setTotal(subtotal.subtract(p.getDesconto()));
  }

  private void validarParaConfirmar(Pedido p) {
    if (p.getCliente() == null || !p.getCliente().isAtivo()) {
      throw new NegocioException("A venda precisa de um cliente ativo.");
    }
    if (p.getVendedor() == null || !p.getVendedor().isEnabled()) {
      throw new NegocioException("A venda precisa de um vendedor responsável ativo.");
    }
    if (p.getItens().isEmpty()) {
      throw new NegocioException("A venda não possui itens.");
    }
    if (p.getTipoEntrega() == TipoEntrega.ENTREGA && (p.getEntregaLogradouro() == null || p.getEntregaCidade() == null)) {
      throw new NegocioException("Informe o endereço de entrega.");
    }
    if (p.getFormaPagamento() == null || p.getParcelas() == null) {
      throw new NegocioException("Informe a forma de pagamento e as parcelas.");
    }
  }

  /** Falha se o catálogo ou a condição mudaram desde o registro (evita confirmar com preço defasado). */
  private void conferirPrecos(Pedido p) {
    Arredondamento arred = config.exigirArredondamento();
    var condicao = config.exigirCondicao(p.getFormaPagamento(), p.getParcelas());
    boolean divergente = condicao.getAjustePercentual().compareTo(p.getAjusteCondicaoPercentual()) != 0;
    for (ItemPedido i : p.getItens()) {
      BigDecimal base = precoBase(i.getProduto(), i.getVariacao());
      if (base.compareTo(i.getPrecoBase()) != 0
          || aplicar(base, condicao.getAjustePercentual(), arred).compareTo(i.getPrecoUnitario()) != 0) {
        divergente = true;
      }
    }
    if (divergente) {
      throw new NegocioException("Os preços do catálogo ou a condição de pagamento mudaram desde o registro. "
          + "Use \"Recalcular preços\" e revise a venda antes de confirmar.");
    }
  }

  private void exigirEditavel(Pedido p) {
    if (!EDITAVEIS.contains(p.getStatusComercial())) {
      throw new NegocioException("Só é possível alterar vendas em rascunho ou aguardando aprovação.");
    }
  }

  /** Carrega o pedido com lock de escrita (serializa confirmação/cancelamento/edição) e confere o acesso. */
  private Pedido travar(Long id, User ator) {
    return acesso.travar(id, ator);
  }

  /** Gerente/proprietário veem tudo; vendedor só as vendas em que é o responsável. */
  private void verificarAcesso(Pedido p, User ator) {
    acesso.verificar(p, ator);
  }

  private static String limpar(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  // =====================================================================
  // Internos: respostas
  // =====================================================================

  private VendaResumoResponse resumo(Pedido p) {
    return new VendaResumoResponse(p.getId(), p.getStatusComercial(), p.getStatusPagamento(), p.getStatusEntrega(),
        p.clienteNomeHistorico(),
        p.getVendedor() == null ? null : nome(p.getVendedor()), p.getCanal(), p.getTotal(), p.getCriadoEm(),
        p.isRevisaoLegado());
  }

  private VendaDetalheResponse detalhe(Pedido p, User ator) {
    List<ReservaEstoque> reservas = estoque.reservasDoPedido(p.getId());
    Map<Long, StatusReserva> reservaPorItem = new LinkedHashMap<>();
    reservas.forEach(r -> reservaPorItem.merge(r.getItem().getId(), r.getStatus(),
        (a, b) -> a == StatusReserva.ATIVA ? a : b));

    var itens = p.getItens().stream().map(i -> new ItemResponse(i.getId(), i.getProduto().getId(),
        i.getDescricaoHistorica() != null ? i.getDescricaoHistorica() : i.getProduto().getNome(),
        i.getVariacao() == null ? null : i.getVariacao().getId(), i.getSkuHistorico(), i.getQuantidade(),
        i.getPrecoBase(), i.getPrecoUnitario(), i.getTotal(), i.getModalidade(), reservaPorItem.get(i.getId())))
        .toList();

    var reservasDto = reservas.stream().map(r -> new ReservaResponse(r.getId(), r.getItem().getId(),
        r.getQuantidade(), r.getStatus(), r.getCriadaEm(), r.getLiberadaEm(), r.getMotivoLiberacao())).toList();

    var descontos = descontoRepo.findByPedidoIdOrderByIdDesc(p.getId()).stream()
        .map(s -> new DescontoResponse(s.getId(), s.getValorDesconto(), s.getPercentual(), s.getStatus(),
            nome(s.getSolicitante()), s.getDecididoPor() == null ? null : nome(s.getDecididoPor()),
            s.getDecididoEm(), s.getMotivoDecisao(), s.getJustificativa(), s.getCriadoEm()))
        .toList();

    var historico = auditoria.listarPorEntidade(ENTIDADE, p.getId()).stream()
        .map(e -> new HistoricoResponse(e.getTipo().name(), e.getDescricao(),
            e.getUsuario() == null ? null : nome(e.getUsuario()), e.getDataEvento()))
        .toList();

    EnderecoEntrega endereco = p.getEntregaLogradouro() == null ? null
        : new EnderecoEntrega(p.getEntregaCep(), p.getEntregaLogradouro(), p.getEntregaNumero(),
            p.getEntregaComplemento(), p.getEntregaBairro(), p.getEntregaCidade(), p.getEntregaUf());

    var pagamento = recebimentoService.painel(p, ator);
    return new VendaDetalheResponse(p.getId(), p.getVersion(), p.getStatusComercial(), p.getStatusPagamento(),
        p.getStatusEntrega(), p.getStatusMontagem(), p.isRevisaoLegado(), p.getCanal(),
        p.getCliente() == null ? null
            : new PessoaResumo(p.getCliente().getId(), p.clienteNomeHistorico(),
                p.getClienteTelefoneHist() != null ? p.getClienteTelefoneHist() : p.getCliente().getTelefone()),
        p.getVendedor() == null ? null : new PessoaResumo(p.getVendedor().getId(), nome(p.getVendedor()), null),
        p.getTipoEntrega(), endereco, p.getFormaPagamento(), p.getParcelas(), p.getAjusteCondicaoPercentual(),
        p.getSubtotal(), p.getDesconto(), p.getFrete(), p.getTotal(), p.getObservacao(), p.getCriadoEm(),
        p.getConfirmadoEm(), p.getCanceladoEm(), p.getMotivoCancelamento(), itens, reservasDto, descontos, historico,
        acoes(p, ator, pagamento), expedicao.entregaDoPedido(p.getId()), expedicao.montagemDoPedido(p.getId()),
        encomendas.doPedido(p.getId()), posVenda.doPedido(p.getId()), estoque.movimentacoesDoPedido(p.getId()), pagamento);
  }

  private Acoes acoes(Pedido p, User ator, br.com.lojaspopular.web.financeiro.FinanceiroDtos.PagamentoPedido pagamento) {
    Map<String, String> bloqueios = new LinkedHashMap<>();
    StatusComercial s = p.getStatusComercial();

    boolean editar = EDITAVEIS.contains(s);
    boolean confirmar = s == StatusComercial.RASCUNHO;
    if (s == StatusComercial.AGUARDANDO_APROVACAO) {
      bloqueios.put("confirmar", "Aguardando a aprovação do desconto por um gerente ou proprietário.");
    }

    boolean cancelar = s == StatusComercial.RASCUNHO || s == StatusComercial.AGUARDANDO_APROVACAO
        || s == StatusComercial.CONFIRMADA;
    if (cancelar) {
      Set<Role> perfis = config.perfisCancelamento();
      if (perfis.isEmpty()) {
        cancelar = false;
        bloqueios.put("cancelar", "Configuração pendente (D07): perfis autorizados a cancelar não definidos.");
      } else if (perfis.stream().noneMatch(r -> UsuarioAtual.tem(ator, r))) {
        cancelar = false;
        bloqueios.put("cancelar", "Seu perfil não está autorizado a cancelar vendas.");
      } else if (p.getSaidaRealizadaEm() != null) {
        cancelar = false;
        bloqueios.put("cancelar", "Produto já expedido: registre uma devolução em Pós-venda.");
      } else if (p.getStatusPagamento() == StatusPagamento.PAGO || p.getStatusPagamento() == StatusPagamento.PARCIAL) {
        cancelar = false;
        bloqueios.put("cancelar", "Venda com recebimentos: estorne-os ou aguarde a política de restituição (D07/D09).");
      }
    }

    boolean aprovar = s == StatusComercial.AGUARDANDO_APROVACAO && UsuarioAtual.isGestor(ator);
    if (aprovar) {
      var pendentes = descontoRepo.findByPedidoIdAndStatusIn(p.getId(),
          EnumSet.of(StatusSolicitacaoDesconto.PENDENTE));
      if (!pendentes.isEmpty() && pendentes.get(0).getSolicitante().getId().equals(ator.getId())
          && !UsuarioAtual.tem(ator, Role.ADMIN)) {
        aprovar = false;
        bloqueios.put("aprovarDesconto", "O solicitante não pode aprovar o próprio desconto.");
      }
    }
    if (p.isLegado()) {
      bloqueios.put("confirmar", "Pedido anterior à gestão de vendas.");
    }

    // ---- Etapa 2: saída, entrega, montagem e pós-venda (operações do gerente/proprietário)
    boolean gestor = UsuarioAtual.isGestor(ator);
    boolean confirmada = s == StatusComercial.CONFIRMADA;
    var entrega = expedicao.entregaDoPedido(p.getId());
    var stEntrega = entrega == null ? null : entrega.status();
    var montagem = expedicao.montagemDoPedido(p.getId());
    var stMontagem = montagem == null ? null : montagem.status();

    boolean agendarEntrega = gestor && confirmada && entrega == null;
    boolean reagendarEntrega = gestor && confirmada && (stEntrega == StatusEntregaRegistro.AGENDADA
        || stEntrega == StatusEntregaRegistro.TENTATIVA_FRUSTRADA);
    boolean registrarSaida = false;
    if (gestor && confirmada && stEntrega == StatusEntregaRegistro.AGENDADA) {
      String bloqueioSaida = expedicao.bloqueioDaSaida(p);
      registrarSaida = bloqueioSaida == null;
      if (bloqueioSaida != null) {
        bloqueios.put("saida", bloqueioSaida);
      }
    }
    boolean frustrada = gestor && stEntrega == StatusEntregaRegistro.SAIU;
    boolean concluirEntrega = gestor && stEntrega == StatusEntregaRegistro.SAIU;
    boolean agendarMontagem = gestor && confirmada && (montagem == null || stMontagem == StatusMontagemRegistro.AGENDADA);
    boolean concluirMontagem = gestor && stMontagem == StatusMontagemRegistro.AGENDADA
        && p.getStatusEntrega() == StatusEntrega.ENTREGUE;
    if (gestor && stMontagem == StatusMontagemRegistro.AGENDADA && !concluirMontagem) {
      bloqueios.put("concluirMontagem", "A montagem só pode ser concluída depois da entrega.");
    }
    boolean dispensarMontagem = gestor && confirmada && (montagem == null || stMontagem == StatusMontagemRegistro.AGENDADA);
    boolean abrirOcorrencia = gestor && confirmada && p.getStatusEntrega() == StatusEntrega.ENTREGUE;

    // ---- Etapa 3: recebimentos (pagamento do cliente)
    pagamento.bloqueios().forEach(bloqueios::putIfAbsent);

    return new Acoes(editar, confirmar, cancelar, aprovar, bloqueios, agendarEntrega, reagendarEntrega,
        registrarSaida, frustrada, concluirEntrega, agendarMontagem, concluirMontagem, dispensarMontagem,
        abrirOcorrencia, pagamento.podeRegistrar(), pagamento.podeEstornar());
  }

  private static String nome(User u) {
    return u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail();
  }
}
