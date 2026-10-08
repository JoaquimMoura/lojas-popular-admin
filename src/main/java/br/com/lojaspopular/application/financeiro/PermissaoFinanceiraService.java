package br.com.lojaspopular.application.financeiro;

import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.domain.comercial.model.ConfiguracaoComercial;
import br.com.lojaspopular.domain.financeiro.enums.OperacaoFinanceira;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import lombok.RequiredArgsConstructor;

/**
 * Ponto único das permissões financeiras (D12). O proprietário sempre pode; o gerente só pode as operações que o
 * proprietário concedeu explicitamente. Decisão pendente (nula) = o gerente NÃO tem a operação.
 */
@Service
@RequiredArgsConstructor
public class PermissaoFinanceiraService {

  private final ConfiguracaoComercialService config;

  /** Perfis concedidos para a operação; nulo se a decisão ainda não foi tomada. */
  public Set<Role> concedidos(OperacaoFinanceira op) {
    String csv = valor(config.obter(), op);
    return csv == null || csv.isBlank() ? null : config.perfis(csv);
  }

  public boolean pode(User ator, OperacaoFinanceira op) {
    if (UsuarioAtual.tem(ator, Role.ADMIN)) {
      return true;
    }
    if (!UsuarioAtual.tem(ator, Role.GERENTE)) {
      return false;
    }
    Set<Role> c = concedidos(op);
    return c != null && c.contains(Role.GERENTE);
  }

  public void exigir(User ator, OperacaoFinanceira op) {
    if (pode(ator, op)) {
      return;
    }
    if (!UsuarioAtual.tem(ator, Role.GERENTE)) {
      throw new AccessDeniedException("Somente gerente ou proprietário realiza esta operação.");
    }
    throw new AccessDeniedException(concedidos(op) == null
        ? "Permissão financeira pendente (D12): o proprietário ainda não autorizou o gerente a " + op.rotulo() + "."
        : "O proprietário não autorizou o gerente a " + op.rotulo() + ".");
  }

  public Map<String, Boolean> minhas(User ator) {
    Map<String, Boolean> out = new java.util.LinkedHashMap<>();
    for (OperacaoFinanceira op : OperacaoFinanceira.values()) {
      out.put(op.name(), pode(ator, op));
    }
    return out;
  }

  private static String valor(ConfiguracaoComercial c, OperacaoFinanceira op) {
    return switch (op) {
      case CONSULTAR -> c.getPermFinConsultar();
      case RECEBER -> c.getPermFinReceber();
      case PAGAR -> c.getPermFinPagar();
      case ESTORNAR -> c.getPermFinEstornar();
      case RESTITUIR -> c.getPermFinRestituir();
    };
  }
}
