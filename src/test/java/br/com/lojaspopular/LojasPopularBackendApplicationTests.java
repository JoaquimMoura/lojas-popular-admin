package br.com.lojaspopular;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.assertj.core.api.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.sun.source.tree.AssertTree;

@SpringBootTest
class LojasPopularBackendApplicationTests {

	@Test
	void contextLoads() {
		assertEquals(1, 1, "Teste");
	}

}
