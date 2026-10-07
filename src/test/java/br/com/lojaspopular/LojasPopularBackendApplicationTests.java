package br.com.lojaspopular;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LojasPopularBackendApplicationTests {

	@Test
	void contextLoads() {
		// Sobe o contexto completo com o perfil de testes (H2, sem integrações externas).
	}

}
