package br.com.lojaspopular.application.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/** Resolve o usuário autenticado da requisição e seus perfis. */
@Component
@RequiredArgsConstructor
public class UsuarioAtual {

  private final UserRepository userRepo;

  public User get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new NotFoundException("Usuário não autenticado");
    }
    return userRepo.findByEmail(auth.getName())
        .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
  }

  public static boolean tem(User u, Role role) {
    return u.getRoles() != null && u.getRoles().contains(role);
  }

  /** Proprietário ou gerente: enxergam todas as vendas e podem aprovar. */
  public static boolean isGestor(User u) {
    return tem(u, Role.ADMIN) || tem(u, Role.GERENTE);
  }
}
