package br.com.lojaspopular.venda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.security.JwtUtil;

/** Contratos de segurança HTTP: 401 x 403, usuário desativado e cabeçalho de idempotência no CORS. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaApiTest {

  @Autowired MockMvc mvc;
  @Autowired JwtUtil jwt;
  @Autowired UserRepository users;

  @Test
  void semTokenEh401() throws Exception {
    mvc.perform(get("/api/v1/vendas")).andExpect(status().isUnauthorized());
  }

  @Test
  void autenticadoSemPerfilEh403EnaoDerrubaASessao() throws Exception {
    // Antes da correção, o 403 virava um redirecionamento interno a /error (autenticado)
    // e o cliente recebia 401, o que o frontend trata como sessão expirada.
    String token = token(usuario(Role.VENDEDOR, true));
    mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/config/comercial").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void perfilClienteNaoAcessaAreaDeGestao() throws Exception {
    String token = token(usuario(Role.CLIENTE, true));
    mvc.perform(get("/api/v1/vendas").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
  }

  @Test
  void tokenDeUsuarioDesativadoNaoDaAcesso() throws Exception {
    User u = usuario(Role.VENDEDOR, true);
    String token = token(u);
    mvc.perform(get("/api/v1/vendas").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

    u.setEnabled(false);
    users.save(u);
    mvc.perform(get("/api/v1/vendas").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
  }

  @Test
  void corsPermiteOCabecalhoIdempotencyKey() throws Exception {
    mvc.perform(options("/api/v1/vendas/1/confirmar")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,idempotency-key"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  private User usuario(Role role, boolean ativo) {
    return users.save(User.builder().email("seg-" + UUID.randomUUID() + "@t.com").passwordHash("x")
        .roles(Set.of(role)).enabled(ativo).createdAt(Instant.now()).build());
  }

  private String token(User u) {
    return jwt.generateAccessToken(u.getEmail(), Map.of("roles", u.getRoles().stream().map(Enum::name).toList()));
  }
}
