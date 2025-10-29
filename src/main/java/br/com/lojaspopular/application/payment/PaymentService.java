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
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentGatewayStrategy gateway;
	private final PedidoRepository pedidoRepo;
	private final AuditoriaService auditoriaService; // ✅ injeta o serviço de auditoria
	private final NotificacaoService notificacaoService;

	@Transactional
	public Map<String, Object> iniciarPagamento(Long pedidoId, PaymentMethod method) {

		Pedido pedido = pedidoRepo.findById(pedidoId).orElseThrow(() -> new NotFoundException("Pedido não encontrado"));

		var init = gateway.createPayment(pedido, method);
		pedido.setPaymentId(init.paymentId());
		pedido.setPaymentMethod(method);
		pedidoRepo.save(pedido);

		return Map.of("paymentId", init.paymentId(), "method", init.method().name(), "amount", init.amount(), "extra",
				init.extra());
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

		pedido.setStatus(novoStatus);
		pedidoRepo.save(pedido);
	}
}
