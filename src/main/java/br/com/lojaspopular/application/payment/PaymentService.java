package br.com.lojaspopular.application.payment;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.notificacao.NotificacaoService;
import br.com.lojaspopular.application.payment.PaymentGatewayStrategy.PaymentInfo;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.order.repository.PedidoRepository;
import br.com.lojaspopular.domain.payment.enums.PaymentMethod;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentGatewayStrategy gateway;
	private final PedidoRepository pedidoRepo;
	private final AuditoriaService auditoriaService; // ✅ injeta o serviço de auditoria
	private final NotificacaoService notificacaoService;
	private final UsuarioAtual usuarioAtual;

	@Transactional
	public Map<String, Object> iniciarPagamento(Long pedidoId, PaymentMethod method) {

		Pedido pedido = pedidoRepo.findById(pedidoId).orElseThrow(() -> new NotFoundException("Pedido não encontrado"));
		verificarPropriedade(pedido);
		if (pedido.getStatus() == PedidoStatus.CANCELADO || pedido.getStatus() == PedidoStatus.PAGO
				|| pedido.getStatusComercial() == br.com.lojaspopular.domain.order.enums.StatusComercial.CANCELADA) {
			throw new NegocioException("Este pedido não aceita novo pagamento (cancelado ou já pago).");
		}

		var init = gateway.createPayment(pedido, method);
		pedido.setPaymentId(init.paymentId());
		pedido.setPaymentMethod(method);
		pedidoRepo.save(pedido);

		return Map.of("paymentId", init.paymentId(), "method", init.method().name(), "amount", init.amount(), "extra",
				init.extra());
	}

	/** O cliente só opera o pagamento do próprio pedido; vendedor, o das suas vendas; gestão, qualquer um. */
	private void verificarPropriedade(Pedido pedido) {
		User ator = usuarioAtual.get();
		if (UsuarioAtual.isGestor(ator)) {
			return;
		}
		boolean dono = pedido.getUsuario().getId().equals(ator.getId());
		boolean vendedorResponsavel = pedido.getVendedor() != null && pedido.getVendedor().getId().equals(ator.getId());
		if (!dono && !vendedorResponsavel) {
			throw new NotFoundException("Pedido não encontrado");
		}
	}

	@Transactional
	public void atualizarStatusPorPagamento(String paymentId) {

		PaymentInfo info = gateway.fetchPayment(paymentId);
		Pedido pedido = pedidoRepo.findAll().stream().filter(p -> paymentId.equals(p.getPaymentId())).findFirst()
				.orElseThrow(() -> new NotFoundException("Pedido do pagamento não encontrado"));

		PedidoStatus novoStatus;

		switch (info.status().toLowerCase()) {
			case "approved" -> {
				novoStatus = PedidoStatus.PAGO;
				notificacaoService.notificarPagamentoAprovado(pedido);
				auditoriaService.registrar(AuditoriaTipo.PAGAMENTO_APROVADO,
						"Pagamento aprovado para pedido #" + pedido.getId());
			}
			case "cancelled", "rejected" -> {
		        novoStatus = PedidoStatus.CANCELADO;
		        notificacaoService.notificarPagamentoRejeitado(pedido);
		        auditoriaService.registrar(
		            AuditoriaTipo.PAGAMENTO_REJEITADO,
		            "Pagamento rejeitado/cancelado para pedido #" + pedido.getId()
		        );
		    }									
		default -> novoStatus = PedidoStatus.CRIADO;
		}

		// Etapa 1: pagamento rejeitado não cancela venda do fluxo de gestão (cancelamento é decisão
		// comercial, com liberação de reserva). A revisão completa do pagamento é da Etapa 3.
		if (novoStatus == PedidoStatus.CANCELADO && !pedido.isLegado()) {
			pedidoRepo.save(pedido);
			return;
		}
		if (novoStatus == PedidoStatus.PAGO) {
			pedido.setStatusPagamento(br.com.lojaspopular.domain.order.enums.StatusPagamento.PAGO);
		}
		pedido.setStatus(novoStatus);
		pedidoRepo.save(pedido);
	}
}
