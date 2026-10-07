package br.com.lojaspopular.application.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.application.usuario.UsuarioService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.financeiro.model.MetaVendedor;
import br.com.lojaspopular.domain.financeiro.repository.MetaVendedorRepository;
import br.com.lojaspopular.domain.financeiro.repository.RestituicaoRepository;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.financeiro.FinanceiroDtos.MetaView;
import lombok.RequiredArgsConstructor;

/**
 * Metas mensais individuais. "Vendido" = vendas confirmadas e não canceladas do vendedor no mês (cancelamentos ficam
 * de fora). O tratamento das devoluções depende de D09: sem a política definida o atingimento é PROVISÓRIO
 * (as restituições do mês aparecem separadas e não são abatidas).
 */
@Service
@RequiredArgsConstructor
public class MetaService {

  private final MetaVendedorRepository metas;
  private final PedidoRepository pedidos;
  private final RestituicaoRepository restituicoes;
  private final UserRepository users;
  private final PeriodoService periodo;
  private final ConfiguracaoComercialService config;
  private final Relogio relogio;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Transactional
  public MetaView definir(Long vendedorId, String mesTexto, BigDecimal valor) {
    User ator = usuarioAtual.get();
    if (!UsuarioAtual.isGestor(ator)) {
      throw new org.springframework.security.access.AccessDeniedException("Somente gerente ou proprietário define metas.");
    }
    LocalDate mes = Relogio.mes(mesTexto);
    User v = users.findById(vendedorId).orElseThrow(() -> new NegocioException("Vendedor não encontrado."));
    if (!v.isEnabled() || v.getRoles().stream().noneMatch(UsuarioService.PERFIS_OPERACIONAIS::contains)) {
      throw new NegocioException("O usuário selecionado não pode ter meta de vendas.");
    }
    periodo.exigirAberto(mes);
    MetaVendedor m = metas.findByVendedorIdAndMes(vendedorId, mes)
        .orElseGet(() -> MetaVendedor.builder().vendedor(v).mes(mes).build());
    m.setValor(valor.setScale(2, RoundingMode.HALF_UP));
    m.setDefinidaPor(ator);
    m.setDefinidaEm(relogio.agora());
    metas.save(m);
    auditoria.registrar(AuditoriaTipo.META_DEFINIDA, "Meta de " + FinanceiroMapper.nome(v) + " para " + Relogio.texto(mes)
        + ": R$ " + m.getValor(), "USUARIO", vendedorId);
    return calcular(v, mes);
  }

  @Transactional(readOnly = true)
  public List<MetaView> listar(String mesTexto) {
    LocalDate mes = Relogio.mes(mesTexto);
    return users.listarAtivosComPerfis(UsuarioService.PERFIS_OPERACIONAIS).stream()
        .sorted(Comparator.comparing(u -> FinanceiroMapper.nome(u).toLowerCase())).map(u -> calcular(u, mes)).toList();
  }

  /** Meta e atingimento do próprio vendedor autenticado. */
  @Transactional(readOnly = true)
  public MetaView minha(String mesTexto) {
    return calcular(usuarioAtual.get(), Relogio.mes(mesTexto));
  }

  private MetaView calcular(User v, LocalDate mes) {
    var meta = metas.findByVendedorIdAndMes(v.getId(), mes).map(MetaVendedor::getValor).orElse(null);
    BigDecimal vendido = pedidos.somaVendidaNoPeriodo(v.getId(), relogio.inicioDoDia(mes), relogio.fimDoMes(mes));
    LocalDate fim = mes.plusMonths(1).minusDays(1);
    BigDecimal restituido = restituicoes.somaEfetivadaDoVendedor(v.getId(), mes, fim);
    Boolean abate = config.obter().getMetaDescontaDevolucoes();
    String politica = abate == null ? "PENDENTE_D09" : abate ? "ABATE" : "NAO_ABATE";
    BigDecimal base = Boolean.TRUE.equals(abate) ? vendido.subtract(restituido) : vendido;
    BigDecimal pct = meta == null ? null : base.multiply(BigDecimal.valueOf(100)).divide(meta, 2, RoundingMode.HALF_UP);
    String obs = null;
    if (abate == null) {
      obs = "Atingimento provisório: a política de devoluções na meta (D09) não foi definida; restituições do mês (R$ "
          + restituido + ") não foram abatidas.";
    } else if (meta == null) {
      obs = "Meta não definida para o mês.";
    }
    return new MetaView(v.getId(), FinanceiroMapper.nome(v), Relogio.texto(mes), meta, vendido, restituido, pct, politica,
        abate == null, obs);
  }
}
