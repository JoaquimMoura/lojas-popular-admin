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

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(String... args) {
    seedUser("admin@loja.com",    "123456", Set.of(Role.ADMIN));
    seedUser("vendedor@loja.com", "123456", Set.of(Role.VENDEDOR));
    seedUser("cliente@loja.com",  "123456", Set.of(Role.CLIENTE));
  }

  private void seedUser(String email, String rawPassword, Set<Role> roles) {
    userRepository.findByEmail(email).orElseGet(() -> {
      var user = User.builder()
          .email(email)
          .passwordHash(passwordEncoder.encode(rawPassword))
          .roles(roles)
          .enabled(true)
          .build();
      System.out.println("Usuario " + email + " criado (profile local/dev).");
      return userRepository.save(user);
    });
  }
}
