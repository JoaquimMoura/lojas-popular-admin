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
  @Autowired br.com.lojaspopular.application.arquivo.ArquivoService arquivos;

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

  @Test
  void arquivosPrivadosNaoSaemPeloCaminhoPublicoDeUploads() throws Exception {
    String token = token(usuario(Role.ADMIN, true));
    mvc.perform(get("/uploads/privado/entregas/1/qualquer.png")).andExpect(status().isUnauthorized());
    mvc.perform(get("/uploads/privado/entregas/1/qualquer.png").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    // o endpoint autenticado exige login e não aceita caminho fora da área privada
    mvc.perform(get("/api/v1/arquivos").param("caminho", "privado/x.png")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/arquivos").param("caminho", "privado/../application.yml")
        .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/arquivos").param("caminho", "produtos/a.png")
        .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
  }

  @Test
  void vendedorNaoOperaSaidaEncomendaNemPosVenda() throws Exception {
    String token = token(usuario(Role.VENDEDOR, true));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/vendas/1/saida")
        .header("Authorization", "Bearer " + token).header("Idempotency-Key", "k"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/encomendas").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/ocorrencias").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    // leitura da agenda é permitida à operação
    mvc.perform(get("/api/v1/agenda").param("de", "2026-01-01").param("ate", "2026-01-31")
        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
  }

  @Test
  void vendedorSoLeComprovantesDeVendasProprias() throws Exception {
    // arquivo de uma venda que não existe/não é do vendedor: gestor lê, vendedor recebe 404
    String caminho = arquivos.salvar(new org.springframework.mock.web.MockMultipartFile("a", "c.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}), "entregas/987654");
    String vend = token(usuario(Role.VENDEDOR, true));
    String ger = token(usuario(Role.GERENTE, true));
    mvc.perform(get("/api/v1/arquivos").param("caminho", caminho).header("Authorization", "Bearer " + ger))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/arquivos").param("caminho", caminho).header("Authorization", "Bearer " + vend))
        .andExpect(status().isNotFound());
    // evidências de pós-venda não são do escopo do vendedor
    String ocorrencia = arquivos.salvar(new org.springframework.mock.web.MockMultipartFile("a", "o.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}), "ocorrencias/5");
    mvc.perform(get("/api/v1/arquivos").param("caminho", ocorrencia).header("Authorization", "Bearer " + vend))
        .andExpect(status().isNotFound());
  }

  @Test
  void loginComSenhaErradaEh401ComMensagemEAuditoriaListaSemErro() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
        .contentType("application/json").content("{\"email\":\"nao.existe@t.com\",\"senha\":\"qualquer123\"}"))
        .andExpect(status().isUnauthorized());
    String adm = token(usuario(Role.ADMIN, true));
    mvc.perform(get("/api/v1/auditoria").header("Authorization", "Bearer " + adm)).andExpect(status().isOk());
  }

  private User usuario(Role role, boolean ativo) {
    return users.save(User.builder().email("seg-" + UUID.randomUUID() + "@t.com").passwordHash("x")
        .roles(Set.of(role)).enabled(ativo).createdAt(Instant.now()).build());
  }

  private String token(User u) {
    return jwt.generateAccessToken(u.getEmail(), Map.of("roles", u.getRoles().stream().map(Enum::name).toList()));
  }
}
