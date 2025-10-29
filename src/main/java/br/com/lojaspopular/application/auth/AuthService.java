package br.com.lojaspopular.application.auth;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
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
	private final JwtUtil jwt;
	private final AuditoriaService auditoriaService;

	public record Tokens(String accessToken, String refreshToken, long expiresIn) {
	}

	public Tokens login(String email, String rawPassword) {

		var user = userRepository.findByEmail(email)
				.orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas"));

		log.info("Validando senha: raw='{}', hash='{}'", rawPassword, user.getPasswordHash());

		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			throw new IllegalArgumentException("Credenciais inválidas");
		}

		var claims = Map.<String, Object>of("roles", user.getRoles().stream().map(Enum::name).toList());

		String access = jwt.generateAccessToken(user.getEmail(), claims);

		String refresh = jwt.generateRefreshToken(user.getEmail());
		long ttl = 3600L; // alinhar com application.yml
		
		auditoriaService.registrar(AuditoriaTipo.LOGIN_SUCESSO, "Usuário logado com sucesso");

		return new Tokens(access, refresh, ttl);
	}

	public Tokens refresh(String refreshToken) {

		var jws = jwt.parse(refreshToken);

		if (!"refresh".equals(jws.getBody().get("typ"))) {
			throw new IllegalArgumentException("Token inválido");
		}

		String email = jws.getBody().getSubject();

		var user = userRepository.findByEmail(email)
				.orElseThrow(() -> new IllegalArgumentException("Usuário inválido"));

		var claims = Map.<String, Object>of("roles", user.getRoles().stream().map(Enum::name).toArray(String[]::new));
		String access = jwt.generateAccessToken(email, claims);
		String newRefresh = jwt.generateRefreshToken(email);
		long ttl = 3600L;

		return new Tokens(access, newRefresh, ttl);
	}
	

}
