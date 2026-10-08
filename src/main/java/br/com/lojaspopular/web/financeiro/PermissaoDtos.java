package br.com.lojaspopular.web.financeiro;

import java.util.Map;
import java.util.Set;

import br.com.lojaspopular.domain.user.Role;

public final class PermissaoDtos {

  private PermissaoDtos() {
  }

  /** Perfis (além do proprietário) autorizados em cada operação. Conjunto vazio/nulo = decisão pendente (só o proprietário). */
  public record PermissoesFinanceirasRequest(Set<Role> consultar, Set<Role> receber, Set<Role> pagar, Set<Role> estornar,
      Set<Role> restituir) {
  }

  public record PermissoesFinanceirasView(Set<Role> consultar, Set<Role> receber, Set<Role> pagar, Set<Role> estornar,
      Set<Role> restituir, boolean todasDecididas, Map<String, Boolean> minhas) {
  }
}
