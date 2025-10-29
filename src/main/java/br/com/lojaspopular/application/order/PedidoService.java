package br.com.lojaspopular.application.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import br.com.lojaspopular.application.notificacao.NotificacaoService;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoService {

  private final PedidoRepository pedidoRepo;
  private final UserRepository userRepo;
  private final NotificacaoService notificacaoService;

  public Pedido criarPedido(List<ItemPedido> itens) {
    User usuario = getUsuarioAtual();

    Pedido pedido = Pedido.builder()
        .usuario(usuario)
        .itens(itens)
        .status(PedidoStatus.CRIADO)
        .criadoEm(Instant.now())
        .build();

    itens.forEach(i -> {
        i.setPedido(pedido);
        if (i.getPrecoUnitario() != null && i.getQuantidade() != null) {
            i.setTotal(i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())));
        } else {
            i.setTotal(BigDecimal.ZERO);
        }
    });
    
    pedido.calcularTotais();

    var novo = pedidoRepo.save(pedido);
    
    notificacaoService.notificarPedidoCriado(novo);
    
    return novo;
  }

  public List<Pedido> listarPedidosDoUsuario() {
    User usuario = getUsuarioAtual();
    return pedidoRepo.findByUsuario(usuario);
  }

  public List<Pedido> listarTodos() {
    return pedidoRepo.findAll();
  }

  public Pedido alterarStatus(Long id, PedidoStatus status) {
    Pedido pedido = pedidoRepo.findById(id)
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));
    pedido.setStatus(status);
    return pedidoRepo.save(pedido);
  }

  private User getUsuarioAtual() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return userRepo.findByEmail(auth.getName())
        .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
  }
}
