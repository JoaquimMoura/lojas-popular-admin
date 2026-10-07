package br.com.lojaspopular.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import br.com.lojaspopular.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> {})
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers
            .frameOptions(frame -> frame.disable()) // ✅ Necessário pro console renderizar
        )
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        )
        .authorizeHttpRequests(auth -> auth
            // O 403 do container vira um redirecionamento interno para /error; sem isto o cliente
            // recebia 401 (e o frontend tratava como sessão expirada, deslogando o usuário).
            .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            // Comprovantes e evidências (entrega, montagem, pós-venda) só saem pelo endpoint autenticado
            .requestMatchers("/uploads/privado/**").denyAll()
            .requestMatchers("/uploads/**").permitAll()
            .requestMatchers("/categorias/**").permitAll()

            .requestMatchers("/api/v1/auth/**", "/api/v1/payments/webhook").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/produtos/**", "/api/v1/categorias/**", "/api/v1/config/loja", "/api/v1/fanpage/**").permitAll()

            .requestMatchers("/api/v1/auditoria/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/vendedores").hasAnyRole("ADMIN", "GERENTE", "VENDEDOR")
            .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/config/comercial/**", "/api/v1/encomendas/**", "/api/v1/ocorrencias/**")
                .hasAnyRole("ADMIN", "GERENTE")
            .requestMatchers("/api/v1/clientes/**", "/api/v1/vendas/**", "/api/v1/estoque/**", "/api/v1/agenda/**",
                "/api/v1/arquivos/**")
                .hasAnyRole("ADMIN", "GERENTE", "VENDEDOR")
            .requestMatchers("/api/v1/fanpage/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/produtos/**", "/api/v1/categorias/**").hasAnyRole("ADMIN", "GERENTE", "VENDEDOR")
            .requestMatchers("/api/v1/pedidos/**", "/api/v1/payments/**").hasAnyRole("CLIENTE", "ADMIN", "GERENTE", "VENDEDOR")

            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}
