package br.com.lojaspopular.application.venda;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.cliente.model.EnderecoCliente;
import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.order.enums.ModalidadeItem;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.store.service.LojaConfigService;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.venda.dto.VendaDtos.EnderecoEntrega;
import br.com.lojaspopular.web.venda.dto.VendaDtos.VendaDetalheResponse;
import lombok.RequiredArgsConstructor;

/**
 * Comprovante de compra (pedido de venda, NÃO é documento fiscal). Não calcula nada: reproduz os totais, itens, endereço e
 * agendamentos do detalhe oficial do pedido ({@link VendaService#obter}), que já aplica o escopo do vendedor. Estado financeiro
 * (valor pago, saldo, parcelas registradas) só aparece para quem pode consultar o financeiro (D12). Emitir/reemitir só grava uma
 * linha de auditoria: não altera estoque, pagamento, comissão nem situação do pedido.
 */
@Service
@RequiredArgsConstructor
public class ComprovanteService {

  public record Loja(String nome, String endereco, String telefone, String logoUrl) {
  }

  public record ClienteDoc(String nome, String telefone, EnderecoEntrega enderecoCadastral) {
  }

  public record Item(String sku, String descricao, Integer quantidade, BigDecimal precoUnitario, BigDecimal total,
      ModalidadeItem modalidade, LocalDate previsaoChegada) {
  }

  public record Parcela(Integer numero, BigDecimal valor) {
  }

  public record Pagamento(FormaPagamento forma, Integer parcelas, String situacao, BigDecimal ajusteFormaPagamentoPercentual,
      boolean financeiroVisivel, BigDecimal valorPago, BigDecimal saldo, List<Parcela> parcelasRegistradas) {
  }

  public record Entrega(TipoEntrega tipo, EnderecoEntrega enderecoDestaEntrega, String situacao, LocalDate dataAgendada,
      PeriodoAgenda periodo) {
  }

  public record Montagem(String situacao, LocalDate dataAgendada, PeriodoAgenda periodo) {
  }

  public record Comprovante(Long pedidoId, Instant dataVenda, String statusVenda, boolean cancelado, Instant canceladoEm,
      boolean legado, String vendedor, Loja loja, ClienteDoc cliente, List<Item> itens, BigDecimal subtotal,
      BigDecimal desconto, BigDecimal frete, BigDecimal total, Pagamento pagamento, Entrega entrega, Montagem montagem,
      String observacaoCliente, Instant emitidoEm) {
  }

  private final VendaService vendas;
  private final VendaAcesso acesso;
  private final LojaConfigService loja;
  private final PermissaoFinanceiraService permissoes;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional
  public Comprovante emitir(Long pedidoId) {
    User ator = usuarioAtual.get();
    VendaDetalheResponse d = vendas.obter(pedidoId);   // dados oficiais + escopo do vendedor (404 para vendas alheias)
    Pedido p = acesso.carregar(pedidoId, ator);
    boolean cancelada = d.statusComercial() == StatusComercial.CANCELADA;
    boolean legado = d.statusComercial() == StatusComercial.LEGADO;
    if (!legado && d.confirmadoEm() == null) {
      throw new NegocioException("O comprovante só existe para vendas confirmadas. Este pedido ainda é um rascunho"
          + " ou está aguardando aprovação.");
    }

    var cfg = loja.getOrCreateDefault();
    var lojaDoc = new Loja(cfg.getNome(), cfg.getEndereco(), cfg.getWhatsapp(), cfg.getLogoUrl());
    EnderecoEntrega cadastral = p.getCliente() == null ? null : p.getCliente().getEnderecos().stream()
        .filter(EnderecoCliente::isPrincipal).findFirst().or(() -> p.getCliente().getEnderecos().stream().findFirst())
        .map(e -> new EnderecoEntrega(e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(), e.getBairro(),
            e.getCidade(), e.getUf())).orElse(null);
    String telefone = d.cliente() == null ? null : d.cliente().telefone();
    var cliente = d.cliente() == null ? null : new ClienteDoc(d.cliente().nome(), telefone, cadastral);

    var previsoes = d.encomendas() == null ? java.util.Map.<Long, LocalDate>of() : d.encomendas().stream()
        .filter(e -> e.previsaoChegada() != null)
        .collect(java.util.stream.Collectors.toMap(e -> e.itemId(), e -> e.previsaoChegada(), (a, b) -> a));
    var itens = d.itens().stream().map(i -> new Item(i.sku(), i.descricao(), i.quantidade(), i.precoUnitario(), i.total(),
        i.modalidade(), previsoes.get(i.id()))).toList();

    // pedido antigo sem controle de pagamento: não afirma valor pago nem saldo ("Não informado")
    boolean financeiro = permissoes.pode(ator, OperacaoFinanceira.CONSULTAR) && d.pagamento() != null && !legado
        && d.statusPagamento() != br.com.lojaspopular.domain.order.enums.StatusPagamento.NAO_INFORMADO;
    List<Parcela> parcelas = List.of();
    BigDecimal pago = null;
    BigDecimal saldo = null;
    if (financeiro) {
      pago = d.pagamento().recebido();
      saldo = d.pagamento().saldo();
      // parcelas do cartão EXATAMENTE como registradas (a última absorve o arredondamento); sem taxas nem líquidos
      parcelas = d.pagamento().recebimentos().stream()
          .filter(r -> r.status() == br.com.lojaspopular.domain.financeiro.enums.StatusRecebimento.REGISTRADO
              && r.forma() == FormaPagamento.CARTAO)
          .flatMap(r -> r.recebiveis().stream())
          .filter(x -> x.status() != br.com.lojaspopular.domain.financeiro.enums.StatusRecebivel.CANCELADO)
          .map(x -> new Parcela(x.parcela(), x.valorBruto())).toList();
    }
    var pagamento = new Pagamento(d.formaPagamento(), d.parcelas(), d.statusPagamento() == null ? null : d.statusPagamento().name(),
        d.ajusteCondicaoPercentual(), financeiro, pago, saldo, parcelas);

    var e = d.entrega();
    boolean temEndereco = d.endereco() != null && d.endereco().logradouro() != null;
    var entrega = new Entrega(d.tipoEntrega(), d.tipoEntrega() == TipoEntrega.ENTREGA && temEndereco ? d.endereco() : null,
        d.statusEntrega() == null ? null : d.statusEntrega().name(), e == null ? null : e.dataPrevista(),
        e == null ? null : e.periodo());
    var m = d.montagem();
    var montagem = new Montagem(d.statusMontagem() == null ? null : d.statusMontagem().name(), m == null ? null : m.dataPrevista(),
        m == null ? null : m.periodo());

    auditoria.registrar(AuditoriaTipo.COMPROVANTE_EMITIDO, "Comprovante do pedido #" + pedidoId + " emitido", "PEDIDO", pedidoId);
    return new Comprovante(d.id(), d.confirmadoEm() != null ? d.confirmadoEm() : d.criadoEm(), d.statusComercial().name(), cancelada,
        d.canceladoEm(), legado, d.vendedor() == null ? null : d.vendedor().nome(), lojaDoc, cliente, itens, d.subtotal(),
        d.desconto(), d.frete(), d.total(), pagamento, entrega, montagem, p.getObservacaoCliente(), Instant.now());
  }

  /** Observação destinada ao cliente (acesso, entrega, montagem). Impressa no comprovante; não é a observação interna. */
  @Transactional
  public String definirObservacaoCliente(Long pedidoId, String texto) {
    User ator = usuarioAtual.get();
    Pedido p = acesso.travar(pedidoId, ator);
    if (p.getStatusComercial() == StatusComercial.CANCELADA) {
      throw new NegocioException("A venda está cancelada.");
    }
    String t = texto == null || texto.isBlank() ? null : texto.trim();
    if (t != null && t.length() > 500) {
      throw new NegocioException("A observação ao cliente pode ter no máximo 500 caracteres.");
    }
    p.setObservacaoCliente(t);
    auditoria.registrar(AuditoriaTipo.VENDA_ALTERADA, "Observação ao cliente do pedido #" + pedidoId + " atualizada", "PEDIDO",
        pedidoId);
    return t;
  }
}
