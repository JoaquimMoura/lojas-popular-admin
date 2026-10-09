package br.com.lojaspopular.application.venda;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.comercial.ConfiguracaoComercialService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.model.PedidoClienteEvento;
import br.com.lojaspopular.domain.order.repository.PedidoClienteEventoRepository;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.ConfiguracaoPendenteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Vínculo posterior de venda antiga a um cliente (proprietário) e troca do cliente de uma venda confirmada (permissão
 * específica, D13, bloqueada enquanto não definida). Só muda o cliente da venda (e a cópia dos dados dele na venda): nunca valores,
 * estoque, pagamentos, comissão nem fechamento. Tudo com justificativa, responsável, data e auditoria.
 */
@Service
@RequiredArgsConstructor
public class VendaClienteService {

  private final PedidoRepository pedidos;
  private final ClienteRepository clientes;
  private final PedidoClienteEventoRepository eventos;
  private final ConfiguracaoComercialService config;
  private final AuditoriaService auditoria;
  private final UsuarioAtual usuarioAtual;

  /** Venda SEM cliente (antiga) passa a ter o cliente informado, após conferência do proprietário. */
  @Transactional
  public void vincular(Long pedidoId, Long clienteId, String justificativa) {
    User ator = usuarioAtual.get();
    if (!UsuarioAtual.tem(ator, Role.ADMIN)) {
      throw new AccessDeniedException("Somente o proprietário vincula uma venda antiga a um cliente.");
    }
    String just = VendaAcesso.exigirTexto(justificativa, "Informe a justificativa (como o cliente foi conferido).");
    Pedido p = pedidos.findByIdForUpdate(pedidoId).orElseThrow(() -> new NotFoundException("Venda não encontrada"));
    if (p.getCliente() != null) {
      throw new NegocioException("Esta venda já tem cliente. Para trocar, use a troca de cliente (exige permissão específica).");
    }
    Cliente novo = clienteAtivo(clienteId);
    aplicar(p, novo);
    eventos.save(PedidoClienteEvento.builder().pedido(p).tipo(PedidoClienteEvento.VINCULO_POSTERIOR).clienteNovo(novo)
        .justificativa(just).usuario(ator).build());
    auditoria.registrar(AuditoriaTipo.VENDA_CLIENTE_VINCULADO, "Venda #" + p.getId() + " vinculada ao cliente #" + novo.getId()
        + " (" + novo.getNome() + "): " + just, "PEDIDO", p.getId());
  }

  /** Troca o cliente de uma venda confirmada: só com a permissão específica definida (D13) e justificativa. */
  @Transactional
  public void trocar(Long pedidoId, Long clienteId, String justificativa) {
    User ator = usuarioAtual.get();
    var perfis = config.perfis(config.obter().getPerfisTrocaCliente());
    if (perfis.isEmpty()) {
      throw new ConfiguracaoPendenteException("Configuração pendente (D13): o proprietário ainda não definiu quem pode trocar o "
          + "cliente de uma venda confirmada.", List.of("D13: perfis autorizados a trocar o cliente da venda não definidos."));
    }
    if (perfis.stream().noneMatch(r -> UsuarioAtual.tem(ator, r))) {
      throw new AccessDeniedException("Seu perfil não está autorizado a trocar o cliente de uma venda confirmada.");
    }
    String just = VendaAcesso.exigirTexto(justificativa, "Informe a justificativa da troca de cliente.");
    Pedido p = pedidos.findByIdForUpdate(pedidoId).orElseThrow(() -> new NotFoundException("Venda não encontrada"));
    if (p.getStatusComercial() != StatusComercial.CONFIRMADA) {
      throw new NegocioException("A troca de cliente é só para vendas confirmadas (antes disso, edite a venda).");
    }
    if (p.getCliente() == null) {
      throw new NegocioException("Esta venda não tem cliente: o proprietário deve vinculá-la a um cliente.");
    }
    if (p.getCliente().getId().equals(clienteId)) {
      throw new NegocioException("O cliente informado já é o cliente desta venda.");
    }
    Cliente anterior = p.getCliente();
    Cliente novo = clienteAtivo(clienteId);
    aplicar(p, novo);
    eventos.save(PedidoClienteEvento.builder().pedido(p).tipo(PedidoClienteEvento.TROCA).clienteAnterior(anterior)
        .clienteNovo(novo).justificativa(just).usuario(ator).build());
    auditoria.registrar(AuditoriaTipo.VENDA_CLIENTE_TROCADO, "Venda #" + p.getId() + ": cliente #" + anterior.getId() + " ("
        + anterior.getNome() + ") trocado por #" + novo.getId() + " (" + novo.getNome() + "): " + just, "PEDIDO", p.getId());
  }

  private Cliente clienteAtivo(Long id) {
    Cliente c = clientes.findById(id).orElseThrow(() -> new NegocioException("Cliente não encontrado."));
    if (!c.isAtivo()) {
      throw new NegocioException("O cliente selecionado está inativo.");
    }
    return c;
  }

  private void aplicar(Pedido p, Cliente c) {
    p.setCliente(c);
    p.setClienteNomeHist(c.getNome());
    p.setClienteCpfHist(c.getCpf());
    p.setClienteTelefoneHist(c.getTelefone());
  }
}
