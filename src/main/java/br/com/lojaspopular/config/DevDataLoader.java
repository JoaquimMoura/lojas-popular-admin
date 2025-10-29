// em br/com/lojaspopular/config/DevDataLoader.java
package br.com.lojaspopular.config;

import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Configuration
@Profile({ "local" })
@RequiredArgsConstructor
public class DevDataLoader implements CommandLineRunner {
	private final UserRepository repo;
	private final PasswordEncoder encoder;

	@Override
	public void run(String... args) {
		repo.findByEmail("admin@loja.com").orElseGet(() -> {
			var u = User.builder().email("admin@loja.com").passwordHash(encoder.encode("123456"))
					.roles(Set.of(Role.ADMIN)).enabled(true).build();
			System.out.println("✅ Usuário admin@loja.com criado (profile local/dev).");
			return repo.save(u);
		});
	}
}
