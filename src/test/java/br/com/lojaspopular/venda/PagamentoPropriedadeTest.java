package br.com.lojaspopular.venda;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import br.com.lojaspopular.application.payment.PaymentService;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.payment.enums.PaymentMethod;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;

/** Um cliente não pode operar o pagamento de pedido alheio, nem de pedido cancelado. */
@SpringBootTest
@ActiveProfiles("test")
class PagamentoPropriedadeTest {

  @Autowired PaymentService pagamentos;
  @Autowired PedidoRepository pedidos;
  @Autowired UserRepository users;

  @Test
  void clienteNaoIniciaPagamentoDePedidoAlheio() {
    User dono = cliente();
    User intruso = cliente();
    Pedido p = pedido(dono, PedidoStatus.CRIADO);

    como(intruso);
    assertThatThrownBy(() -> pagamentos.iniciarPagamento(p.getId(), PaymentMethod.PIX))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void pedidoCanceladoNaoAceitaPagamentoNemDoDono() {
    User dono = cliente();
    Pedido p = pedido(dono, PedidoStatus.CANCELADO);

    como(dono);
    assertThatThrownBy(() -> pagamentos.iniciarPagamento(p.getId(), PaymentMethod.PIX))
        .isInstanceOf(NegocioException.class);
  }

  private User cliente() {
    return users.save(User.builder().email("cli-" + UUID.randomUUID() + "@t.com").passwordHash("x")
        .roles(Set.of(Role.CLIENTE)).enabled(true).createdAt(Instant.now()).build());
  }

  private Pedido pedido(User dono, PedidoStatus status) {
    return pedidos.save(Pedido.builder().usuario(dono).status(status).subtotal(BigDecimal.TEN)
        .frete(BigDecimal.ZERO).total(BigDecimal.TEN).build());
  }

  private void como(User u) {
    var auth = u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, auth));
  }
}
