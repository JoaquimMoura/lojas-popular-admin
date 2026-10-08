package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.financeiro.LivroService.Lanc;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.financeiro.enums.ContaLivro;
import br.com.lojaspopular.domain.financeiro.enums.OrigemLancamento;
import br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel;
import br.com.lojaspopular.domain.financeiro.enums.TipoLancamento;
import br.com.lojaspopular.domain.financeiro.model.RecebivelCartao;
import br.com.lojaspopular.domain.financeiro.model.TaxaCartao;
import br.com.lojaspopular.domain.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.lojaspopular.domain.financeiro.repository.RecebivelCartaoRepository;
import br.com.lojaspopular.domain.financeiro.repository.TaxaCartaoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.RecebivelView;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoRequest;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.TaxaCartaoView;
import lombok.RequiredArgsConstructor;

/**
 * Cartão: taxas/prazos da operadora e liquidação dos recebíveis. A venda no cartão gera RECEBÍVEIS (bruto, taxa,
 * líquido, previsão); o dinheiro só entra no banco, UMA vez por parcela, quando a liquidação é registrada.
 * Sem conciliação automática: o valor efetivamente liquidado é informado a partir do extrato da operadora.
 */
@Service
@RequiredArgsConstructor
public class CartaoService {

  private final br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService permissoes;
  private final RecebivelCartaoRepository recebiveis;
  private final TaxaCartaoRepository taxas;
  private final LancamentoFinanceiroRepository lancamentos;
  private final LivroService livro;
  private final FinanceiroMapper mapper;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  // ---------------------------------------------------------------- taxas e prazos (cadastro da loja)

  @Transactional(readOnly = true)
  public List<TaxaCartaoView> listarTaxas() {
    return taxas.findAllByOrderByOperadoraAscParcelasAsc().stream().map(mapper::view).toList();
  }

  @Transactional
  public TaxaCartaoView salvarTaxa(Long id, TaxaCartaoRequest req) {
    User ator = usuarioAtual.get();
    if (!br.com.lojaspopular.application.auth.UsuarioAtual.tem(ator, br.com.lojaspopular.domain.user.Role.ADMIN)) {
      throw new org.springframework.security.access.AccessDeniedException("Somente o proprietário cadastra taxas de cartão.");
    }
    BigDecimal taxa = req.taxaPercentual().setScale(4, RoundingMode.HALF_UP);
    if (taxa.signum() < 0 || taxa.compareTo(new BigDecimal("100")) >= 0) {
      throw new NegocioException("A taxa deve estar entre 0% e 100% (exclusive).");
    }
    String operadora = req.operadora().trim();
    TaxaCartao t;
    if (id == null) {
      if (taxas.findByOperadoraAndParcelas(operadora, req.parcelas()).isPresent()) {
        throw new NegocioException("Já existe taxa cadastrada para " + operadora + " em " + req.parcelas() + "x.");
      }
      t = TaxaCartao.builder().operadora(operadora).parcelas(req.parcelas()).build();
    } else {
      t = taxas.findById(id).orElseThrow(() -> new NotFoundException("Taxa não encontrada"));
    }
    t.setTaxaPercentual(taxa);
    t.setPrazoPrimeiraParcelaDias(req.prazoPrimeiraParcelaDias());
    t.setIntervaloDias(req.intervaloDias());
    t.setAtiva(req.ativa() == null || req.ativa());
    t = taxas.save(t);
    auditoria.registrar(AuditoriaTipo.TAXA_CARTAO_ALTERADA, "Taxa " + t.getOperadora() + " " + t.getParcelas() + "x: "
        + t.getTaxaPercentual() + "%, prazo " + t.getPrazoPrimeiraParcelaDias() + "d + " + t.getIntervaloDias()
        + "d/parcela (vendas já registradas mantêm o plano gravado)", "TAXA_CARTAO", t.getId());
    return mapper.view(t);
  }

  // ---------------------------------------------------------------- recebíveis

  @Transactional(readOnly = true)
  public List<RecebivelView> listar(StatusRecebivel status, String operadora, LocalDate de, LocalDate ate) {
    return recebiveis.listar(status, operadora == null ? "" : operadora.trim(), de, ate).stream().map(mapper::view).toList();
  }

  /**
   * Registra a liquidação de uma parcela: entrada EFETIVA no banco (uma vez). Repetir com a mesma chave devolve o
   * mesmo resultado; liquidar de novo uma parcela já liquidada é recusado.
   */
  @Transactional
  public RecebivelView liquidar(Long id, LocalDate dataLiquidacao, BigDecimal valorLiquidado, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.RECEBER);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência da liquidação (cabeçalho Idempotency-Key).");
    RecebivelCartao r = recebiveis.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Recebível não encontrado"));
    if (r.getStatus() == StatusRecebivel.LIQUIDADO) {
      if (k.equals(r.getChaveLiquidacao())) {
        return mapper.view(r);
      }
      throw new NegocioException("Esta parcela já foi liquidada.");
    }
    if (r.getStatus() == StatusRecebivel.CANCELADO) {
      throw new NegocioException("Esta parcela foi cancelada (recebimento estornado ou restituído).");
    }
    LocalDate hoje = relogio.hoje();
    LocalDate data = dataLiquidacao == null ? hoje : dataLiquidacao;
    if (data.isAfter(hoje)) {
      throw new NegocioException("A data da liquidação não pode ser futura.");
    }
    BigDecimal valor = (valorLiquidado == null ? r.getValorLiquido() : valorLiquidado).setScale(2, RoundingMode.HALF_UP);
    if (valor.signum() <= 0 || valor.compareTo(r.getValorBruto()) > 0) {
      throw new NegocioException("O valor liquidado deve ser maior que zero e não pode superar o valor bruto da parcela (R$ "
          + r.getValorBruto() + ").");
    }
    var lanc = livro.lancar(Lanc.builder().conta(ContaLivro.BANCO).tipo(TipoLancamento.ENTRADA).valor(valor).data(data)
        .origem(OrigemLancamento.LIQUIDACAO_CARTAO).descricao("Liquidação da parcela " + r.getParcela() + "/"
            + r.getTotalParcelas() + " da venda #" + r.getPedido().getId() + " (" + r.getOperadora() + ")")
        .pedido(r.getPedido()).recebimento(r.getRecebimento()).recebivel(r).chave("liq:" + k).usuario(ator).build());
    r.setStatus(StatusRecebivel.LIQUIDADO);
    r.setDataLiquidacao(data);
    r.setValorLiquidado(valor);
    r.setLiquidadoEm(relogio.agora());
    r.setLiquidadoPor(ator);
    r.setChaveLiquidacao(k);
    auditoria.registrar(AuditoriaTipo.CARTAO_LIQUIDADO, "Parcela " + r.getParcela() + "/" + r.getTotalParcelas()
        + " da venda #" + r.getPedido().getId() + " liquidada: R$ " + valor + " (previsto R$ " + r.getValorLiquido() + ")",
        "PEDIDO", r.getPedido().getId());
    return mapper.view(r);
  }

  /** Estorna a liquidação (lançamento oposto, na data atual); a parcela volta a ser PREVISTA. Idempotente por chave. */
  @Transactional
  public RecebivelView estornarLiquidacao(Long id, String motivo, String chave) {
    User ator = usuarioAtual.get();
    permissoes.exigir(ator, br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira.ESTORNAR);
    String k = VendaAcesso.exigirTexto(chave, "Informe a chave de idempotência do estorno (cabeçalho Idempotency-Key).");
    String mot = VendaAcesso.exigirTexto(motivo, "Informe o motivo do estorno da liquidação.");
    RecebivelCartao r = recebiveis.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Recebível não encontrado"));
    String chaveLivro = "estliq:" + k;
    if (lancamentos.findByChave(chaveLivro).isPresent()) {
      return mapper.view(r);
    }
    if (r.getStatus() != StatusRecebivel.LIQUIDADO) {
      throw new NegocioException("Só é possível estornar a liquidação de uma parcela liquidada.");
    }
    var original = livro.ativo(lancamentos.findByRecebivelIdOrderByIdAsc(r.getId()))
        .orElseThrow(() -> new NegocioException("Não há lançamento de liquidação ativo para estornar."));
    livro.estornar(original, "Estorno da liquidação da parcela " + r.getParcela() + ": " + mot, chaveLivro, ator);
    r.setStatus(StatusRecebivel.PREVISTO);
    r.setDataLiquidacao(null);
    r.setValorLiquidado(null);
    r.setLiquidadoEm(null);
    r.setLiquidadoPor(null);
    r.setChaveLiquidacao(null);
    auditoria.registrar(AuditoriaTipo.CARTAO_LIQUIDACAO_ESTORNADA, "Liquidação da parcela " + r.getParcela() + " da venda #"
        + r.getPedido().getId() + " estornada: " + mot, "PEDIDO", r.getPedido().getId());
    return mapper.view(r);
  }
}
