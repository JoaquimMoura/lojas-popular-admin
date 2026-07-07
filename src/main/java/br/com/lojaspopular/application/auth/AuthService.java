package br.com.lojaspopular.application.auth;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final AuditoriaService auditoriaService;

  public record Tokens(
      String accessToken,
      String refreshToken,
      long expiresIn,
      UserSummary user) {
  }

  public record UserSummary(Long id, String email, Set<String> roles) {
  }

  public Tokens login(String email, String rawPassword) {
    var user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("Credenciais invalidas"));

    log.info("Validando senha para usuario {}", email);

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("Credenciais invalidas");
    }

    var tokens = generateTokens(user);
    auditoriaService.registrar(AuditoriaTipo.LOGIN_SUCESSO, "Usuario logado com sucesso");
    return tokens;
  }

  public Tokens refresh(String refreshToken) {
    var jws = jwtUtil.parse(refreshToken);

    if (!"refresh".equals(jws.getBody().get("typ"))) {
      throw new IllegalArgumentException("Token invalido");
    }

    String email = jws.getBody().getSubject();

    var user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("Usuario invalido"));

    return generateTokens(user);
  }

  private Tokens generateTokens(User user) {
    Set<String> roles = user.getRoles().stream()
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());

    Map<String, Object> claims = Map.of("roles", roles);

    String accessToken = jwtUtil.generateAccessToken(user.getEmail(), claims);
    String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
    long ttl = 3600L; // TODO alinhar com configuracao em application.yml

    return new Tokens(
        accessToken,
        refreshToken,
        ttl,
        new UserSummary(user.getId(), user.getEmail(), roles));
  }
}
