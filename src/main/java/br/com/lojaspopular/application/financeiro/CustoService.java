package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.financeiro.model.CustoProduto;
import br.com.lojaspopular.domain.financeiro.repository.CustoProdutoRepository;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.ItemPedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Cadastro de custos e custo histórico dos itens vendidos.
 *
 * <p>O custo vigente do catálogo é congelado no item na confirmação da venda e não muda mais. Sem custo cadastrado, o item
 * fica com custo nulo ("não informado"): nunca é preenchido com zero e o fechamento o lista como faltante.
 * Cadastrar/informar custo é do proprietário; consultar é do gerente e do proprietário.
 */
@Service
@RequiredArgsConstructor
public class CustoService {

  public record CustoLinha(Long produtoId, String produto, Long variacaoId, String variacao, String sku, BigDecimal custo,
      LocalDate vigenteDesde, boolean herdadoDoProduto) {
  }

  public record CustoHistorico(Long id, Long variacaoId, BigDecimal custo, LocalDate vigenteDesde, String motivo,
      String registradoPor) {
  }

  public record ItemSemCusto(Long itemId, Long pedidoId, String descricao, Integer quantidade, java.time.Instant confirmadoEm) {
  }

  private final CustoProdutoRepository custos;
  private final ProdutoRepository produtos;
  private final ItemPedidoRepository itens;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;
  private final PeriodoService periodo;
  private final Relogio relogio;

  // ------------------------------------------------------------------ consulta

  /** Custo vigente na data (da variação; se não houver, o do produto). */
  @Transactional(readOnly = true)
  public Optional<CustoProduto> vigente(Produto produto, ProdutoVariacao variacao, LocalDate data) {
    if (variacao != null) {
      var v = custos.daVariacao(variacao.getId(), data);
      if (!v.isEmpty()) {
        return Optional.of(v.get(0));
      }
    }
    return custos.doProduto(produto.getId(), data).stream().findFirst();
  }

  @Transactional(readOnly = true)
  public List<CustoLinha> listar() {
    VendaAcesso.exigirGestor(usuarioAtual.get());
    LocalDate hoje = relogio.hoje();
    List<CustoLinha> out = new ArrayList<>();
    for (Produto p : produtos.findAll()) {
      var doProduto = custos.doProduto(p.getId(), hoje).stream().findFirst();
      out.add(new CustoLinha(p.getId(), p.getNome(), null, null, p.getSku(), doProduto.map(CustoProduto::getCusto).orElse(null),
          doProduto.map(CustoProduto::getVigenteDesde).orElse(null), false));
      for (ProdutoVariacao v : p.getVariacoes()) {
        var proprio = custos.daVariacao(v.getId(), hoje).stream().findFirst();
        var efetivo = proprio.or(() -> doProduto);
        out.add(new CustoLinha(p.getId(), p.getNome(), v.getId(), br.com.lojaspopular.application.estoque.EstoqueService.descricao(v),
            v.getSku(), efetivo.map(CustoProduto::getCusto).orElse(null), efetivo.map(CustoProduto::getVigenteDesde).orElse(null),
            proprio.isEmpty() && doProduto.isPresent()));
      }
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<CustoHistorico> historico(Long produtoId) {
    VendaAcesso.exigirGestor(usuarioAtual.get());
    return custos.historicoDoProduto(produtoId).stream().map(c -> new CustoHistorico(c.getId(),
        c.getVariacao() == null ? null : c.getVariacao().getId(), c.getCusto(), c.getVigenteDesde(), c.getMotivo(),
        c.getCriadoPor().getNome())).toList();
  }

  @Transactional(readOnly = true)
  public List<ItemSemCusto> itensSemCusto() {
    VendaAcesso.exigirGestor(usuarioAtual.get());
    return itens.confirmadosSemCusto().stream().map(i -> new ItemSemCusto(i.getId(), i.getPedido().getId(),
        i.getDescricaoHistorica(), i.getQuantidade(), i.getPedido().getConfirmadoEm())).toList();
  }

  // ------------------------------------------------------------------ escrita

  /** Novo custo (registro novo; o histórico não é alterado). Vale para vendas confirmadas a partir da vigência. */
  @Transactional
  public CustoHistorico registrar(Long produtoId, Long variacaoId, BigDecimal custo, LocalDate vigenteDesde, String motivo) {
    User ator = usuarioAtual.get();
    exigirProprietario(ator);
    if (custo == null || custo.signum() < 0) {
      throw new NegocioException("O custo deve ser zero ou maior (informe 0 apenas se o custo for de fato zero).");
    }
    Produto p = produtos.findById(produtoId).orElseThrow(() -> new NotFoundException("Produto não encontrado"));
    ProdutoVariacao v = null;
    if (variacaoId != null) {
      v = p.getVariacoes().stream().filter(x -> x.getId().equals(variacaoId)).findFirst()
          .orElseThrow(() -> new NegocioException("A variação informada não pertence ao produto."));
    }
    LocalDate desde = vigenteDesde == null ? relogio.hoje() : vigenteDesde;
    CustoProduto c = custos.save(CustoProduto.builder().produto(p).variacao(v).custo(custo.setScale(2, RoundingMode.HALF_UP))
        .vigenteDesde(desde).motivo(motivo == null || motivo.isBlank() ? null : motivo.trim()).criadoPor(ator).build());
    auditoria.registrar(AuditoriaTipo.CUSTO_REGISTRADO, "Custo de " + p.getNome() + (v == null ? "" : " (variação #" + v.getId() + ")")
        + ": R$ " + c.getCusto() + " desde " + desde + " (vendas já confirmadas não mudam)", "PRODUTO", p.getId());
    return new CustoHistorico(c.getId(), variacaoId, c.getCusto(), c.getVigenteDesde(), c.getMotivo(), ator.getNome());
  }

  /** Chamado na confirmação: congela o custo vigente em cada item que ainda não o tem. Sem custo cadastrado, mantém nulo. */
  @Transactional
  public void congelar(Pedido p) {
    LocalDate hoje = relogio.hoje();
    for (ItemPedido i : p.getItens()) {
      if (i.getCustoUnitario() != null) {
        continue;
      }
      vigente(i.getProduto(), i.getVariacao(), hoje).ifPresent(c -> {
        i.setCustoUnitario(c.getCusto());
        i.setCustoOrigem("CATALOGO");
      });
    }
  }

  /** Informa o custo de um item vendido que está sem custo (uma vez; depois é imutável). Exige motivo e período aberto. */
  @Transactional
  public ItemSemCusto informarCustoDoItem(Long itemId, BigDecimal custo, String motivo) {
    User ator = usuarioAtual.get();
    exigirProprietario(ator);
    String m = VendaAcesso.exigirTexto(motivo, "Informe o motivo (ex.: custo conforme nota de compra).");
    if (custo == null || custo.signum() < 0) {
      throw new NegocioException("O custo deve ser zero ou maior.");
    }
    ItemPedido i = itens.findById(itemId).orElseThrow(() -> new NotFoundException("Item não encontrado"));
    if (i.getCustoUnitario() != null) {
      throw new NegocioException("Este item já possui custo histórico (R$ " + i.getCustoUnitario() + ") e ele não pode ser alterado.");
    }
    Pedido p = i.getPedido();
    if (p.getConfirmadoEm() == null) {
      throw new NegocioException("O custo só é informado para itens de vendas confirmadas (na confirmação ele é congelado).");
    }
    periodo.exigirAberto(p.getConfirmadoEm().atZone(relogio.zona()).toLocalDate());
    i.setCustoUnitario(custo.setScale(2, RoundingMode.HALF_UP));
    i.setCustoOrigem("MANUAL");
    auditoria.registrar(AuditoriaTipo.CUSTO_ITEM_INFORMADO, "Custo do item #" + i.getId() + " da venda #" + p.getId() + ": R$ "
        + i.getCustoUnitario() + " — " + m, "PEDIDO", p.getId());
    return new ItemSemCusto(i.getId(), p.getId(), i.getDescricaoHistorica(), i.getQuantidade(), p.getConfirmadoEm());
  }

  private void exigirProprietario(User ator) {
    if (!UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new AccessDeniedException("Somente o proprietário cadastra ou informa custos.");
    }
  }
}
