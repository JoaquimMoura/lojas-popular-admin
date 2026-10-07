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
		if (!pedido.isLegado()) {
			throw new NegocioException("Esta venda é da gestão de vendas: o pagamento é registrado em Financeiro (recebimentos), "
					+ "não pelo checkout online.");
		}
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

	/**
	 * Webhook do gateway (pedidos do checkout online/legado). Regras: o pedido é localizado pelo paymentId (indexado); o
	 * valor aprovado precisa bater com o total do pedido; eventos repetidos não duplicam efeito, notificação nem auditoria;
	 * uma tentativa rejeitada NÃO cancela o pedido (cancelar é decisão comercial); eventos pendentes/em análise nunca
	 * rebaixam um pedido já pago. Vendas da gestão recebem pagamento pelo Financeiro (recebimentos), não por este canal.
	 */
	@Transactional
	public void atualizarStatusPorPagamento(String paymentId) {

		PaymentInfo info = gateway.fetchPayment(paymentId);
		Pedido achado = pedidoRepo.findByPaymentId(paymentId)
				.orElseThrow(() -> new NotFoundException("Pedido do pagamento não encontrado"));
		// serializa entregas simultâneas do mesmo evento
		Pedido pedido = pedidoRepo.findByIdForUpdate(achado.getId()).orElseThrow();

		if (!pedido.isLegado()) {
			auditoriaService.registrar(AuditoriaTipo.PAGAMENTO_REJEITADO, "Evento do gateway ignorado: a venda #" + pedido.getId()
					+ " é da gestão e recebe pagamento pelo Financeiro", "PEDIDO", pedido.getId());
			return;
		}
		String situacao = info.status() == null ? "" : info.status().toLowerCase();
		switch (situacao) {
			case "approved" -> {
				if (info.amount() == null || pedido.getTotal() == null || info.amount().compareTo(pedido.getTotal()) != 0) {
					auditoriaService.registrar(AuditoriaTipo.PAGAMENTO_REJEITADO, "Pagamento aprovado com valor divergente no pedido #"
							+ pedido.getId() + " (gateway " + info.amount() + ", pedido " + pedido.getTotal() + "): ignorado",
							"PEDIDO", pedido.getId());
					return;
				}
				if (pedido.getStatus() == PedidoStatus.PAGO || pedido.getStatus() == PedidoStatus.ENTREGUE) {
					return; // evento repetido: já processado
				}
				if (pedido.getStatus() == PedidoStatus.CANCELADO) {
					auditoriaService.registrar(AuditoriaTipo.PAGAMENTO_APROVADO, "Pagamento aprovado em pedido já cancelado (#"
							+ pedido.getId() + "): requer conferência manual; nenhum status alterado", "PEDIDO", pedido.getId());
					return;
				}
				pedido.setStatus(PedidoStatus.PAGO);
				pedido.setStatusPagamento(br.com.lojaspopular.domain.order.enums.StatusPagamento.PAGO);
				pedidoRepo.save(pedido);
				notificacaoService.notificarPagamentoAprovado(pedido);
				auditoriaService.registrar(AuditoriaTipo.PAGAMENTO_APROVADO,
						"Pagamento aprovado para pedido #" + pedido.getId(), "PEDIDO", pedido.getId());
			}
			case "cancelled", "rejected" -> {
				// tentativa de pagamento recusada: o pedido segue como está (pode tentar de novo); não é cancelamento comercial
				if (pedido.getStatus() == PedidoStatus.CRIADO) {
					notificacaoService.notificarPagamentoRejeitado(pedido);
					auditoriaService.registrar(AuditoriaTipo.PAGAMENTO_REJEITADO,
							"Tentativa de pagamento rejeitada/cancelada para pedido #" + pedido.getId() + " (pedido mantido)",
							"PEDIDO", pedido.getId());
				}
			}
			default -> {
				// pendente / em análise: nada a fazer (nunca rebaixa um pedido já pago)
			}
		}
	}
}
