package br.com.lojaspopular.application.usuario;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Gestão de acesso (UC-01): o proprietário cria, altera e desativa usuários operacionais.
 * Desativar nunca apaga o usuário, preservando as vendas vinculadas a ele.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

  /** Perfis operacionais da loja (contam para o limite de usuários ativos). */
  public static final Set<Role> PERFIS_OPERACIONAIS = EnumSet.of(Role.ADMIN, Role.GERENTE, Role.VENDEDOR);

  private final UserRepository repo;
  private final PasswordEncoder encoder;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  @Value("${app.max-usuarios-ativos:5}")
  private int maxUsuariosAtivos;

  @Transactional(readOnly = true)
  public List<User> listar() {
    return repo.findAll().stream().sorted(Comparator.comparing(User::getId)).toList();
  }

  /** Usuários ativos que podem ser vendedor responsável (vendedor, gerente ou proprietário). */
  @Transactional(readOnly = true)
  public List<User> vendedoresAtivos() {
    return repo.listarAtivosComPerfis(PERFIS_OPERACIONAIS).stream()
        .sorted(Comparator.comparing(u -> nomeOuEmail(u).toLowerCase())).toList();
  }

  @Transactional
  public User criar(String nome, String email, String senha, Role perfil, String telefone) {
    validarPerfil(perfil);
    if (nome == null || nome.isBlank()) {
      throw new NegocioException("Nome é obrigatório.");
    }
    if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new NegocioException("E-mail inválido.");
    }
    validarSenha(senha);
    String emailNorm = email.trim().toLowerCase();
    if (repo.findByEmail(emailNorm).isPresent()) {
      throw new NegocioException("Já existe um usuário com este e-mail.");
    }
    exigirVaga();

    User u = User.builder().nome(nome.trim()).email(emailNorm).passwordHash(encoder.encode(senha))
        .roles(EnumSet.of(perfil)).telefone(blankToNull(telefone)).enabled(true).createdAt(Instant.now()).build();
    u = repo.save(u);
    auditoria.registrar(AuditoriaTipo.USUARIO_CRIADO, "Usuário criado: " + emailNorm + " (" + perfil + ")", "USUARIO",
        u.getId());
    return u;
  }

  @Transactional
  public User atualizar(Long id, String nome, Role perfil, String telefone) {
    validarPerfil(perfil);
    User u = buscar(id);
    if (nome == null || nome.isBlank()) {
      throw new NegocioException("Nome é obrigatório.");
    }
    boolean eraAdmin = UsuarioAtual.tem(u, Role.ADMIN);
    if (eraAdmin && perfil != Role.ADMIN && u.isEnabled()) {
      exigirOutroProprietario(u);
    }
    u.setNome(nome.trim());
    u.setRoles(EnumSet.of(perfil));
    u.setTelefone(blankToNull(telefone));
    auditoria.registrar(AuditoriaTipo.USUARIO_ALTERADO, "Usuário alterado: " + u.getEmail() + " (" + perfil + ")",
        "USUARIO", u.getId());
    return u;
  }

  @Transactional
  public User desativar(Long id) {
    User u = buscar(id);
    if (!u.isEnabled()) {
      return u;
    }
    if (u.getId().equals(usuarioAtual.get().getId())) {
      throw new NegocioException("Você não pode desativar o próprio usuário.");
    }
    if (UsuarioAtual.tem(u, Role.ADMIN)) {
      exigirOutroProprietario(u);
    }
    u.setEnabled(false);
    auditoria.registrar(AuditoriaTipo.USUARIO_DESATIVADO, "Usuário desativado: " + u.getEmail(), "USUARIO", u.getId());
    return u;
  }

  @Transactional
  public User ativar(Long id) {
    User u = buscar(id);
    if (u.isEnabled()) {
      return u;
    }
    if (u.getRoles().stream().anyMatch(PERFIS_OPERACIONAIS::contains)) {
      exigirVaga();
    }
    u.setEnabled(true);
    auditoria.registrar(AuditoriaTipo.USUARIO_ATIVADO, "Usuário ativado: " + u.getEmail(), "USUARIO", u.getId());
    return u;
  }

  @Transactional
  public void redefinirSenha(Long id, String senha) {
    validarSenha(senha);
    User u = buscar(id);
    u.setPasswordHash(encoder.encode(senha));
    auditoria.registrar(AuditoriaTipo.USUARIO_ALTERADO, "Senha redefinida: " + u.getEmail(), "USUARIO", u.getId());
  }

  // ---- internos ----

  private User buscar(Long id) {
    return repo.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
  }

  private void validarPerfil(Role perfil) {
    if (perfil == null || !PERFIS_OPERACIONAIS.contains(perfil)) {
      throw new NegocioException("Perfil inválido: use Proprietário, Gerente ou Vendedor.");
    }
  }

  private void validarSenha(String senha) {
    if (senha == null || senha.length() < 8) {
      throw new NegocioException("A senha deve ter ao menos 8 caracteres.");
    }
  }

  private void exigirVaga() {
    if (repo.contarAtivosComPerfis(PERFIS_OPERACIONAIS) >= maxUsuariosAtivos) {
      throw new NegocioException("Limite de " + maxUsuariosAtivos
          + " usuários ativos atingido. Desative um usuário antes de criar ou ativar outro.");
    }
  }

  private void exigirOutroProprietario(User u) {
    boolean existeOutro = repo.listarAtivosComPerfis(EnumSet.of(Role.ADMIN)).stream()
        .anyMatch(o -> !o.getId().equals(u.getId()));
    if (!existeOutro) {
      throw new NegocioException("A loja precisa de ao menos um proprietário ativo.");
    }
  }

  private static String nomeOuEmail(User u) {
    return u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
