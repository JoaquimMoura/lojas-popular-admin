package br.com.lojaspopular.application.venda;

import org.springframework.stereotype.Component;

import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/** Carregamento de pedidos com escopo de acesso: gerente/proprietário veem tudo; vendedor, só as suas vendas. */
@Component
@RequiredArgsConstructor
public class VendaAcesso {

  private final PedidoRepository pedidoRepo;

  /** Carrega com lock de escrita (serializa operações concorrentes sobre o mesmo pedido). */
  public Pedido travar(Long id, User ator) {
    Pedido p = pedidoRepo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Venda não encontrada"));
    verificar(p, ator);
    return p;
  }

  public Pedido carregar(Long id, User ator) {
    Pedido p = pedidoRepo.findById(id).orElseThrow(() -> new NotFoundException("Venda não encontrada"));
    verificar(p, ator);
    return p;
  }

  public void verificar(Pedido p, User ator) {
    if (UsuarioAtual.isGestor(ator)) {
      return;
    }
    if (p.getVendedor() == null || !p.getVendedor().getId().equals(ator.getId())) {
      throw new NotFoundException("Venda não encontrada");
    }
  }

  /** Operações de atendimento (saída, entrega, montagem, encomenda, pós-venda) são do gerente/proprietário. */
  public static void exigirGestor(User ator) {
    if (!UsuarioAtual.isGestor(ator)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Somente gerente ou proprietário realiza esta operação.");
    }
  }

  public static String exigirTexto(String valor, String mensagem) {
    if (valor == null || valor.isBlank()) {
      throw new NegocioException(mensagem);
    }
    return valor.trim();
  }
}
