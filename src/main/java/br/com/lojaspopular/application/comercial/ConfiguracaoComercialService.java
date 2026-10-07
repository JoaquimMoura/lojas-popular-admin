package br.com.lojaspopular.application.comercial;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.comercial.enums.Arredondamento;
import br.com.lojaspopular.domain.comercial.model.ConfiguracaoComercial;
import br.com.lojaspopular.domain.comercial.model.CondicaoPagamento;
import br.com.lojaspopular.domain.comercial.repository.ConfiguracaoComercialRepository;
import br.com.lojaspopular.domain.comercial.repository.CondicaoPagamentoRepository;
import br.com.lojaspopular.domain.order.enums.FormaPagamento;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Regras comerciais configuráveis. Valores ausentes representam decisões pendentes
 * (D03, D04, D07): nenhum padrão é assumido; as funções dependentes ficam bloqueadas.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracaoComercialService {

  /** Perfis que podem ser autorizados a cancelar vendas. */
  public static final Set<Role> PERFIS_OPERACIONAIS = EnumSet.of(Role.ADMIN, Role.GERENTE, Role.VENDEDOR);

  public record Pendencia(String codigo, String descricao, String bloqueia) {
  }

  private final ConfiguracaoComercialRepository configRepo;
  private final CondicaoPagamentoRepository condicaoRepo;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional
  public ConfiguracaoComercial obter() {
    return configRepo.findById(ConfiguracaoComercial.ID_UNICO)
        .orElseGet(() -> configRepo.save(new ConfiguracaoComercial()));
  }

  @Transactional
  public List<Pendencia> pendencias() {
    var cfg = obter();
    List<Pendencia> p = new ArrayList<>();
    if (cfg.getLimiteDescontoPercentual() == null) {
      p.add(new Pendencia("D03", "Limite de desconto do vendedor não definido.", "Concessão de desconto em vendas"));
    }
    if (cfg.getArredondamento() == null) {
      p.add(new Pendencia("D04", "Arredondamento dos preços não definido.", "Registro de vendas"));
    }
    if (condicaoRepo.findByAtivaTrueOrderByFormaAscParcelasAsc().isEmpty()) {
      p.add(new Pendencia("D04", "Nenhuma condição de pagamento (forma/parcelas) cadastrada.", "Registro de vendas"));
    }
    if (cfg.getPerfisCancelamento() == null || cfg.getPerfisCancelamento().isBlank()) {
      p.add(new Pendencia("D07", "Perfis autorizados a cancelar vendas não definidos.", "Cancelamento de vendas"));
    }
    if (cfg.getExigePagamentoExpedir() == null) {
      p.add(new Pendencia("D05", "Regra de pagamento exigido para a saída (expedição) não definida.",
          "Saída de mercadoria (baixa de estoque)"));
    }
    return p;
  }

  private List<String> descricoes(String codigo) {
    return pendencias().stream().filter(x -> x.codigo().equals(codigo)).map(Pendencia::descricao).toList();
  }

  /** Arredondamento de preços; bloqueia o registro de venda enquanto não definido (D04). */
  @Transactional
  public Arredondamento exigirArredondamento() {
    var arred = obter().getArredondamento();
    if (arred == null) {
      throw new ConfiguracaoPendenteException(
          "Configuração pendente (D04): defina o arredondamento dos preços em Configuração comercial antes de registrar vendas.",
          descricoes("D04"));
    }
    return arred;
  }

  /** Condição de preço da forma/parcelas escolhidas; só condições cadastradas e ativas são aceitas (D04). */
  @Transactional
  public CondicaoPagamento exigirCondicao(FormaPagamento forma, Integer parcelas) {
    if (condicaoRepo.findByAtivaTrueOrderByFormaAscParcelasAsc().isEmpty()) {
      throw new ConfiguracaoPendenteException(
          "Configuração pendente (D04): cadastre as condições de pagamento (forma e parcelas) antes de registrar vendas.",
          descricoes("D04"));
    }
    return condicaoRepo.findByFormaAndParcelas(forma, parcelas)
        .filter(CondicaoPagamento::isAtiva)
        .orElseThrow(() -> new NegocioException(
            "Condição de pagamento não cadastrada: " + forma + " em " + parcelas + "x."));
  }

  /** Limite de desconto; bloqueia a concessão de desconto enquanto não definido (D03). */
  @Transactional
  public BigDecimal exigirLimiteDesconto() {
    var limite = obter().getLimiteDescontoPercentual();
    if (limite == null) {
      throw new ConfiguracaoPendenteException(
          "Configuração pendente (D03): o limite de desconto não foi definido; não é possível conceder desconto.",
          descricoes("D03"));
    }
    return limite;
  }

  /** Perfis autorizados a cancelar; bloqueia o cancelamento enquanto não definidos (D07). */
  @Transactional
  public Set<Role> exigirPerfisCancelamento() {
    var perfis = perfisCancelamento();
    if (perfis.isEmpty()) {
      throw new ConfiguracaoPendenteException(
          "Configuração pendente (D07): defina quais perfis podem cancelar vendas antes de cancelar.",
          descricoes("D07"));
    }
    return perfis;
  }

  /** Regra de pagamento para expedir; bloqueia a saída enquanto não definida (D05). */
  @Transactional
  public boolean exigirRegraPagamentoExpedir() {
    var regra = obter().getExigePagamentoExpedir();
    if (regra == null) {
      throw new ConfiguracaoPendenteException(
          "Configuração pendente (D05): defina se o pagamento precisa estar quitado para a saída da mercadoria.",
          descricoes("D05"));
    }
    return regra;
  }

  @Transactional
  public Set<Role> perfisCancelamento() {
    String csv = obter().getPerfisCancelamento();
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    return java.util.Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Role::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
  }

  /** Mantém a regra D05 como está (compatível com chamadas anteriores à Etapa 2). */
  @Transactional
  public ConfiguracaoComercial atualizar(BigDecimal limiteDesconto, Arredondamento arredondamento,
      Set<Role> perfisCancelamento) {
    return atualizar(limiteDesconto, arredondamento, perfisCancelamento, obter().getExigePagamentoExpedir());
  }

  @Transactional
  public ConfiguracaoComercial atualizar(BigDecimal limiteDesconto, Arredondamento arredondamento,
      Set<Role> perfisCancelamento, Boolean exigePagamentoExpedir) {
    User ator = usuarioAtual.get();
    var cfg = obter();

    if (limiteDesconto != null
        && (limiteDesconto.signum() < 0 || limiteDesconto.compareTo(new BigDecimal("100")) > 0)) {
      throw new NegocioException("O limite de desconto deve estar entre 0% e 100%.");
    }

    String csvNovo = null;
    if (perfisCancelamento != null && !perfisCancelamento.isEmpty()) {
      if (!PERFIS_OPERACIONAIS.containsAll(perfisCancelamento)) {
        throw new NegocioException("Perfil inválido para cancelamento.");
      }
      csvNovo = perfisCancelamento.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
    if (!java.util.Objects.equals(csvNovo, cfg.getPerfisCancelamento()) && !UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new AccessDeniedException("Somente o proprietário define quem pode cancelar vendas.");
    }

    cfg.setLimiteDescontoPercentual(limiteDesconto == null ? null : limiteDesconto.setScale(2, java.math.RoundingMode.HALF_UP));
    cfg.setArredondamento(arredondamento);
    cfg.setExigePagamentoExpedir(exigePagamentoExpedir);
    cfg.setPerfisCancelamento(csvNovo);
    cfg.setAtualizadoEm(Instant.now());
    cfg.setAtualizadoPor(ator);
    var salvo = configRepo.save(cfg);
    auditoria.registrar(AuditoriaTipo.CONFIG_COMERCIAL_ALTERADA,
        "Configuração comercial: limite de desconto=" + salvo.getLimiteDescontoPercentual()
            + ", arredondamento=" + salvo.getArredondamento()
            + ", perfis de cancelamento=" + salvo.getPerfisCancelamento()
            + ", pagamento exigido para expedir=" + salvo.getExigePagamentoExpedir(),
        "CONFIG_COMERCIAL", ConfiguracaoComercial.ID_UNICO);
    return salvo;
  }

  // ---- condições de pagamento ----

  @Transactional(readOnly = true)
  public List<CondicaoPagamento> listarCondicoes(boolean apenasAtivas) {
    return apenasAtivas ? condicaoRepo.findByAtivaTrueOrderByFormaAscParcelasAsc()
        : condicaoRepo.findAllByOrderByFormaAscParcelasAsc();
  }

  @Transactional
  public CondicaoPagamento criarCondicao(FormaPagamento forma, Integer parcelas, BigDecimal ajuste, boolean ativa) {
    validarCondicao(forma, parcelas, ajuste);
    if (condicaoRepo.findByFormaAndParcelas(forma, parcelas).isPresent()) {
      throw new NegocioException("Já existe uma condição para " + forma + " em " + parcelas + "x.");
    }
    var c = condicaoRepo.save(CondicaoPagamento.builder()
        .forma(forma).parcelas(parcelas)
        .ajustePercentual(ajuste.setScale(2, java.math.RoundingMode.HALF_UP))
        .ativa(ativa).build());
    auditoria.registrar(AuditoriaTipo.CONFIG_COMERCIAL_ALTERADA,
        "Condição criada: " + forma + " " + parcelas + "x, ajuste " + c.getAjustePercentual() + "%",
        "CONDICAO_PAGAMENTO", c.getId());
    return c;
  }

  /**
   * Altera o ajuste ou a situação da condição. Pedidos já registrados guardam seu próprio
   * ajuste e preços, portanto não mudam.
   */
  @Transactional
  public CondicaoPagamento atualizarCondicao(Long id, BigDecimal ajuste, boolean ativa) {
    var c = condicaoRepo.findById(id).orElseThrow(() -> new NotFoundException("Condição não encontrada"));
    validarCondicao(c.getForma(), c.getParcelas(), ajuste);
    c.setAjustePercentual(ajuste.setScale(2, java.math.RoundingMode.HALF_UP));
    c.setAtiva(ativa);
    var salvo = condicaoRepo.save(c);
    auditoria.registrar(AuditoriaTipo.CONFIG_COMERCIAL_ALTERADA,
        "Condição alterada: " + c.getForma() + " " + c.getParcelas() + "x, ajuste " + salvo.getAjustePercentual()
            + "%, ativa=" + ativa,
        "CONDICAO_PAGAMENTO", c.getId());
    return salvo;
  }

  private void validarCondicao(FormaPagamento forma, Integer parcelas, BigDecimal ajuste) {
    if (forma == null || parcelas == null || ajuste == null) {
      throw new NegocioException("Forma, parcelas e ajuste são obrigatórios.");
    }
    if (forma != FormaPagamento.CARTAO && parcelas != 1) {
      throw new NegocioException("Dinheiro e Pix aceitam apenas 1 parcela.");
    }
    if (parcelas < 1 || parcelas > 24) {
      throw new NegocioException("O número de parcelas deve estar entre 1 e 24.");
    }
    if (ajuste.compareTo(new BigDecimal("-100")) <= 0 || ajuste.compareTo(new BigDecimal("1000")) > 0) {
      throw new NegocioException("O ajuste percentual deve ser maior que -100% e no máximo 1000%.");
    }
  }
}
