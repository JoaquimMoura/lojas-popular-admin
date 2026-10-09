package br.com.lojaspopular.application.posvenda;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.application.arquivo.ArquivoService;
import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.estoque.EstoqueService;
import br.com.lojaspopular.application.venda.Precos;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.estoque.enums.TipoMovimentacao;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.StatusEntrega;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.posvenda.enums.CondicaoFisica;
import br.com.lojaspopular.domain.posvenda.enums.StatusOcorrencia;
import br.com.lojaspopular.domain.posvenda.enums.TipoOcorrencia;
import br.com.lojaspopular.domain.posvenda.model.OcorrenciaEvidencia;
import br.com.lojaspopular.domain.posvenda.model.OcorrenciaPosVenda;
import br.com.lojaspopular.domain.posvenda.repository.OcorrenciaPosVendaRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.AbrirOcorrenciaRequest;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.EvidenciaResponse;
import br.com.lojaspopular.web.expedicao.AtendimentoDtos.OcorrenciaResponse;
import lombok.RequiredArgsConstructor;

/**
 * Pós-venda: assistência, troca e devolução.
 *
 * <p>O sistema registra a ocorrência, as evidências, a avaliação física e a entrada de estoque. A solução
 * financeira (restituição, cobrança ou crédito da diferença de uma troca) depende da política D09, ainda não
 * definida: a diferença é apenas calculada e informada, e nenhum valor é movimentado.
 * A devolução só volta ao saldo disponível se a condição física avaliada for APTA_REVENDA.
 */
@Service
@RequiredArgsConstructor
public class PosVendaService {

  public static final String BLOQUEIO_FINANCEIRO =
      "Restituição, cobrança ou crédito da diferença dependem da política de troca/devolução (D09), ainda não "
          + "definida: o sistema não movimenta valores.";

  private final OcorrenciaPosVendaRepository repo;
  private final ProdutoRepository produtoRepo;
  private final VendaAcesso acesso;
  private final EstoqueService estoque;
  private final ConfiguracaoComercialService config;
  private final ArquivoService arquivos;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;
  private final br.com.lojaspopular.application.financeiro.RestituicaoService restituicaoService;
  private final br.com.lojaspopular.domain.financeiro.repository.ContaFinanceiraRepository contasRepo;
  private final br.com.lojaspopular.application.financeiro.FinanceiroMapper financeiroMapper;
  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;

  @Transactional
  public OcorrenciaResponse abrir(Long pedidoId, AbrirOcorrenciaRequest req) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    Pedido p = acesso.travar(pedidoId, ator);
    if (p.getStatusComercial() != StatusComercial.CONFIRMADA || p.getStatusEntrega() != StatusEntrega.ENTREGUE) {
      throw new NegocioException("O pós-venda só pode ser aberto para vendas já entregues.");
    }

    OcorrenciaPosVenda o = OcorrenciaPosVenda.builder().pedido(p).tipo(req.tipo()).status(StatusOcorrencia.ABERTA)
        .descricao(req.descricao().trim()).abertaPor(ator).build();

    if (req.tipo() == TipoOcorrencia.ASSISTENCIA) {
      if (req.itemId() != null) {
        o.setItem(itemDoPedido(p, req.itemId()));
      }
    } else {
      if (req.itemId() == null || req.quantidade() == null) {
        throw new NegocioException("Informe o item e a quantidade da " + (req.tipo() == TipoOcorrencia.TROCA
            ? "troca" : "devolução") + ".");
      }
      ItemPedido item = itemDoPedido(p, req.itemId());
      long jaEmDevolucao = repo.quantidadeEmDevolucao(item.getId());
      if (req.quantidade() + jaEmDevolucao > item.getQuantidade()) {
        throw new NegocioException("Quantidade acima do vendido: o item tem " + item.getQuantidade()
            + " un. e já há " + jaEmDevolucao + " em troca/devolução.");
      }
      o.setItem(item);
      o.setQuantidade(req.quantidade());
      if (req.tipo() == TipoOcorrencia.TROCA) {
        preencherTroca(o, p, item, req);
      }
    }
    o = repo.save(o);
    auditoria.registrar(AuditoriaTipo.OCORRENCIA_ABERTA,
        "Ocorrência #" + o.getId() + " (" + o.getTipo() + ") aberta para a venda #" + pedidoId, "PEDIDO", pedidoId);
    return view(o);
  }

  @Transactional
  public OcorrenciaResponse anexarEvidencia(Long id, MultipartFile arquivo, String descricao) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    OcorrenciaPosVenda o = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada"));
    if (o.getStatus() == StatusOcorrencia.CANCELADA) {
      throw new NegocioException("A ocorrência está cancelada.");
    }
    String caminho = arquivos.salvar(arquivo, "ocorrencias/" + id);
    o.getEvidencias().add(OcorrenciaEvidencia.builder().ocorrencia(o).arquivo(caminho)
        .descricao(descricao == null || descricao.isBlank() ? null : descricao.trim()).enviadoPor(ator).build());
    auditoria.registrar(AuditoriaTipo.OCORRENCIA_ATUALIZADA, "Evidência anexada à ocorrência #" + id, "PEDIDO",
        o.getPedido().getId());
    return view(o);
  }

  /**
   * Recebimento físico da devolução com avaliação da condição. Apta para revenda: entrada de estoque;
   * não apta: fica registrada, sem voltar ao saldo disponível.
   */
  @Transactional
  public OcorrenciaResponse receberDevolucao(Long id, CondicaoFisica condicao, String avaliacao, String chave) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do recebimento (cabeçalho Idempotency-Key).");
    OcorrenciaPosVenda o = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada"));
    if (o.getTipo() == TipoOcorrencia.ASSISTENCIA) {
      throw new NegocioException("Assistência não envolve devolução física de item.");
    }
    if (o.getStatus() == StatusOcorrencia.DEVOLUCAO_RECEBIDA && k.equals(o.getChaveDevolucao())) {
      return view(o);
    }
    if (o.getStatus() != StatusOcorrencia.ABERTA) {
      throw new NegocioException("A devolução desta ocorrência já foi recebida ou a ocorrência foi encerrada.");
    }
    if (condicao == null) {
      throw new NegocioException("Informe a condição física do item devolvido.");
    }
    if (condicao == CondicaoFisica.NAO_APTA) {
      VendaAcesso.exigirTexto(avaliacao, "Descreva a avaliação: por que o item não está apto para revenda.");
    }
    ItemPedido item = o.getItem();
    boolean repor = condicao == CondicaoFisica.APTA_REVENDA;
    if (repor) {
      estoque.registrarEntrada(TipoMovimentacao.ENTRADA_DEVOLUCAO, item.getProduto(), item.getVariacao(),
          o.getQuantidade(), o.getPedido().getId(), item.getId(), null, o.getId(),
          "Devolução da ocorrência #" + o.getId() + " (venda #" + o.getPedido().getId() + ")", ator);
    }
    o.setCondicaoFisica(condicao);
    o.setAvaliacao(avaliacao == null || avaliacao.isBlank() ? null : avaliacao.trim());
    o.setDevolucaoRecebidaEm(Instant.now());
    o.setDevolucaoRecebidaPor(ator);
    o.setEstoqueReposto(repor);
    o.setChaveDevolucao(k);
    o.setStatus(StatusOcorrencia.DEVOLUCAO_RECEBIDA);
    auditoria.registrar(AuditoriaTipo.DEVOLUCAO_RECEBIDA,
        "Devolução da ocorrência #" + id + " recebida: " + condicao + (repor ? " (voltou ao estoque)" : " (não voltou ao estoque)"),
        "PEDIDO", o.getPedido().getId());
    return view(o);
  }

  @Transactional
  public OcorrenciaResponse resolver(Long id, String solucao) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    OcorrenciaPosVenda o = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada"));
    String s = VendaAcesso.exigirTexto(solucao, "Descreva a solução.");
    if (o.getTipo() == TipoOcorrencia.ASSISTENCIA) {
      if (o.getStatus() != StatusOcorrencia.ABERTA) {
        throw new NegocioException("A ocorrência não está aberta.");
      }
    } else if (o.getStatus() != StatusOcorrencia.DEVOLUCAO_RECEBIDA) {
      throw new NegocioException("Receba e avalie a devolução física antes de resolver a ocorrência.");
    }
    o.setSolucao(s);
    o.setStatus(StatusOcorrencia.RESOLVIDA);
    o.setResolvidaEm(Instant.now());
    o.setResolvidaPor(ator);
    auditoria.registrar(AuditoriaTipo.OCORRENCIA_RESOLVIDA, "Ocorrência #" + id + " resolvida", "PEDIDO",
        o.getPedido().getId());
    return view(o);
  }

  @Transactional
  public OcorrenciaResponse cancelar(Long id, String motivo) {
    User ator = usuarioAtual.get();
    VendaAcesso.exigirGestor(ator);
    OcorrenciaPosVenda o = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada"));
    String m = VendaAcesso.exigirTexto(motivo, "Informe o motivo do cancelamento.");
    if (o.getStatus() != StatusOcorrencia.ABERTA) {
      throw new NegocioException("Só é possível cancelar uma ocorrência aberta (sem devolução recebida).");
    }
    o.setStatus(StatusOcorrencia.CANCELADA);
    o.setMotivoCancelamento(m);
    auditoria.registrar(AuditoriaTipo.OCORRENCIA_CANCELADA, "Ocorrência #" + id + " cancelada: " + m, "PEDIDO",
        o.getPedido().getId());
    return view(o);
  }

  @Transactional(readOnly = true)
  public List<OcorrenciaResponse> listar(StatusOcorrencia status, TipoOcorrencia tipo) {
    return repo.listar(status, tipo).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public OcorrenciaResponse obter(Long id) {
    return view(repo.findById(id).orElseThrow(() -> new NotFoundException("Ocorrência não encontrada")));
  }

  @Transactional(readOnly = true)
  public List<OcorrenciaResponse> doPedido(Long pedidoId) {
    return repo.findByPedidoIdOrderByIdDesc(pedidoId).stream().map(this::view).toList();
  }

  // ---- internos ----

  private void preencherTroca(OcorrenciaPosVenda o, Pedido p, ItemPedido item, AbrirOcorrenciaRequest req) {
    if (req.troca() == null) {
      throw new NegocioException("Informe o produto da troca.");
    }
    Produto novo = produtoRepo.findById(req.troca().produtoId())
        .orElseThrow(() -> new NegocioException("Produto da troca não encontrado."));
    ProdutoVariacao variacao = null;
    if (!novo.getVariacoes().isEmpty()) {
      if (req.troca().variacaoId() == null) {
        throw new NegocioException("Selecione a variação do produto da troca.");
      }
      variacao = novo.getVariacoes().stream().filter(v -> req.troca().variacaoId().equals(v.getId())).findFirst()
          .orElseThrow(() -> new NegocioException("A variação não pertence ao produto da troca."));
    } else if (req.troca().variacaoId() != null) {
      throw new NegocioException("O produto da troca não possui variações.");
    }
    // A diferença usa a mesma condição de pagamento da venda original (sem inventar regra nova).
    var arred = config.exigirArredondamento();
    BigDecimal ajuste = p.getAjusteCondicaoPercentual() == null ? BigDecimal.ZERO : p.getAjusteCondicaoPercentual();
    BigDecimal novoUnitario = Precos.aplicar(Precos.base(novo, variacao), ajuste, arred);
    BigDecimal entra = novoUnitario.multiply(BigDecimal.valueOf(req.troca().quantidade()));
    BigDecimal sai = item.getPrecoUnitario().multiply(BigDecimal.valueOf(req.quantidade()));
    o.setTrocaProduto(novo);
    o.setTrocaVariacao(variacao);
    o.setTrocaQuantidade(req.troca().quantidade());
    o.setDiferencaCalculada(entra.subtract(sai));
  }

  private ItemPedido itemDoPedido(Pedido p, Long itemId) {
    return p.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
        .orElseThrow(() -> new NegocioException("O item não pertence a esta venda."));
  }

  private OcorrenciaResponse view(OcorrenciaPosVenda o) {
    Pedido p = o.getPedido();
    String trocaItem = o.getTrocaProduto() == null ? null
        : o.getTrocaProduto().getNome() + (o.getTrocaVariacao() == null ? "" : " — "
            + EstoqueService.descricao(o.getTrocaVariacao()));
    var evidencias = o.getEvidencias().stream().map(e -> new EvidenciaResponse(e.getId(), e.getArquivo(),
        e.getDescricao(), e.getEnviadoPor() == null ? null : nome(e.getEnviadoPor()), e.getCriadoEm())).toList();
    Map<String, String> bloqueios = new LinkedHashMap<>();
    var cfg = config.obter();
    boolean physical = o.getDevolucaoRecebidaEm() != null;
    BigDecimal restituivel = BigDecimal.ZERO;
    var ator = usuarioAtual.get();
    boolean consulta = permissoes.pode(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.CONSULTAR);
    boolean restitui = permissoes.pode(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RESTITUIR);
    boolean podeSolicitar = false;
    boolean podeCobrar = false;
    if (o.getTipo() != TipoOcorrencia.ASSISTENCIA) {
      // A solução financeira só existe conforme a política D09; a devolução física tem controles próprios.
      boolean aFavorDoCliente = o.getTipo() == TipoOcorrencia.DEVOLUCAO
          || (o.getDiferencaCalculada() != null && o.getDiferencaCalculada().signum() < 0);
      boolean cobranca = o.getTipo() == TipoOcorrencia.TROCA && o.getDiferencaCalculada() != null
          && o.getDiferencaCalculada().signum() > 0;
      Boolean politica = cobranca ? cfg.getPermiteCobrancaDiferenca() : cfg.getPermiteRestituicao();
      if (politica == null) {
        bloqueios.put("solucaoFinanceira", BLOQUEIO_FINANCEIRO);
      } else if (!politica) {
        bloqueios.put("solucaoFinanceira", "A política da loja não permite " + (cobranca ? "cobrar a diferença de troca"
            : "restituir valores ao cliente") + " (D09): a ocorrência segue só com o controle físico.");
      }
      if (aFavorDoCliente) {
        restituivel = restituicaoService.restituivel(o);
      }
      boolean gestor = restitui;   // D12: sem a permissão de restituir não há ação financeira
      podeSolicitar = gestor && physical && aFavorDoCliente && Boolean.TRUE.equals(cfg.getPermiteRestituicao())
          && restituivel.signum() > 0;
      podeCobrar = gestor && physical && cobranca && Boolean.TRUE.equals(cfg.getPermiteCobrancaDiferenca())
          && contasRepo.findByOcorrenciaIdAndOrigemAndSituacaoNot(o.getId(),
              br.com.lojaspopular.domain.financeiro.enums.OrigemConta.DIFERENCA_TROCA,
              br.com.lojaspopular.domain.financeiro.enums.SituacaoConta.CANCELADA).isEmpty();
      if (!physical) {
        bloqueios.put("financeiro", "Receba e avalie a devolução física antes de qualquer ação financeira.");
      }
    }
    var contaEntidade = contasRepo.findByOcorrenciaIdAndOrigemAndSituacaoNot(o.getId(),
        br.com.lojaspopular.domain.financeiro.enums.OrigemConta.DIFERENCA_TROCA,
        br.com.lojaspopular.domain.financeiro.enums.SituacaoConta.CANCELADA).stream().findFirst();
    // D12: valores e detalhes financeiros só para quem pode consultar; os demais veem apenas a situação operacional
    var contaDiferenca = consulta ? contaEntidade.map(c -> financeiroMapper.view(c, false)).orElse(null) : null;
    var listaRestituicoes = consulta ? restituicaoService.daOcorrencia(o.getId())
        : restituicaoService.daOcorrenciaOperacional(o.getId());
    String situacaoFinanceira = null;
    if (o.getTipo() != TipoOcorrencia.ASSISTENCIA) {
      var st = listaRestituicoes.stream().map(r -> r.status().name()).toList();
      if (st.contains("EFETIVADA")) {
        situacaoFinanceira = "RESTITUICAO_CONCLUIDA";
      } else if (st.contains("SOLICITADA") || st.contains("AUTORIZADA")) {
        situacaoFinanceira = "RESTITUICAO_PENDENTE";
      } else if (contaEntidade.isPresent()) {
        situacaoFinanceira = contaEntidade.get().getSituacao() == br.com.lojaspopular.domain.financeiro.enums.SituacaoConta.PAGA
            ? "DIFERENCA_RECEBIDA" : "DIFERENCA_A_RECEBER";
      }
    }
    if (!consulta) {
      restituivel = null;
    }
    return new OcorrenciaResponse(o.getId(), p.getId(), p.clienteNomeHistorico(),
        o.getTipo(), o.getStatus(), o.getDescricao(), o.getItem() == null ? null : o.getItem().getId(),
        o.getItem() == null ? null : o.getItem().getDescricaoHistorica(), o.getQuantidade(), trocaItem,
        o.getTrocaQuantidade(), o.getDiferencaCalculada(), o.getCondicaoFisica(), o.getAvaliacao(),
        o.getDevolucaoRecebidaEm(), o.isEstoqueReposto(), o.getSolucao(), o.getResolvidaEm(),
        o.getMotivoCancelamento(), nome(o.getAbertaPor()), o.getCriadaEm(), evidencias, bloqueios,
        listaRestituicoes, contaDiferenca, restituivel, podeSolicitar, podeCobrar, situacaoFinanceira);
  }

  private static String nome(User u) {
    return u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail();
  }
}
