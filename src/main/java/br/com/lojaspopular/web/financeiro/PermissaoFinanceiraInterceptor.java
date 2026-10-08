package br.com.lojaspopular.web.financeiro;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.financeiro.PermissaoFinanceiraService;
import br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Consultas (GET) do financeiro exigem a permissão CONSULTAR (D12). As leituras do próprio vendedor (comissão e meta
 * próprias) e as permissões do usuário atual ficam de fora.
 */
@Component
@RequiredArgsConstructor
public class PermissaoFinanceiraInterceptor implements HandlerInterceptor {

  private final PermissaoFinanceiraService permissoes;
  private final UsuarioAtual usuarioAtual;

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    String path = req.getRequestURI();
    if (!"GET".equals(req.getMethod()) || path.endsWith("/comissoes/minhas") || path.endsWith("/metas/minha")
        || path.endsWith("/financeiro/permissoes")) {
      return true;
    }
    permissoes.exigir(usuarioAtual.get(), OperacaoFinanceira.CONSULTAR);
    return true;
  }
}
