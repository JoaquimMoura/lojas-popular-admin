package br.com.lojaspopular.web.usuario;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.usuario.UsuarioService;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Gestão de acesso: somente o proprietário (ADMIN). */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

  public record UsuarioRequest(
      @NotBlank(message = "Nome é obrigatório") String nome,
      @NotBlank(message = "E-mail é obrigatório") String email,
      @NotBlank(message = "Senha é obrigatória") String senha,
      @NotNull(message = "Perfil é obrigatório") Role perfil,
      String telefone) {
  }

  public record UsuarioAtualizarRequest(
      @NotBlank(message = "Nome é obrigatório") String nome,
      @NotNull(message = "Perfil é obrigatório") Role perfil,
      String telefone) {
  }

  public record SenhaRequest(@NotBlank(message = "Senha é obrigatória") String senha) {
  }

  public record UsuarioResponse(Long id, String nome, String email, Set<Role> perfis, String telefone,
      boolean ativo, Instant criadoEm) {
  }

  public record VendedorResponse(Long id, String nome) {
  }

  private final UsuarioService service;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<UsuarioResponse> listar() {
    return service.listar().stream().map(this::toResponse).toList();
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public UsuarioResponse criar(@Valid @RequestBody UsuarioRequest req) {
    return toResponse(service.criar(req.nome(), req.email(), req.senha(), req.perfil(), req.telefone()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioAtualizarRequest req) {
    return toResponse(service.atualizar(id, req.nome(), req.perfil(), req.telefone()));
  }

  @PostMapping("/{id}/desativar")
  @PreAuthorize("hasRole('ADMIN')")
  public UsuarioResponse desativar(@PathVariable Long id) {
    return toResponse(service.desativar(id));
  }

  @PostMapping("/{id}/ativar")
  @PreAuthorize("hasRole('ADMIN')")
  public UsuarioResponse ativar(@PathVariable Long id) {
    return toResponse(service.ativar(id));
  }

  @PutMapping("/{id}/senha")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> redefinirSenha(@PathVariable Long id, @Valid @RequestBody SenhaRequest req) {
    service.redefinirSenha(id, req.senha());
    return ResponseEntity.noContent().build();
  }

  /** Vendedores responsáveis possíveis: necessário na tela Nova venda para gerente e proprietário. */
  @GetMapping("/vendedores")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
  public List<VendedorResponse> vendedores() {
    return service.vendedoresAtivos().stream()
        .map(u -> new VendedorResponse(u.getId(), u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail()))
        .toList();
  }

  private UsuarioResponse toResponse(User u) {
    return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getRoles(), u.getTelefone(), u.isEnabled(),
        u.getCreatedAt());
  }
}
