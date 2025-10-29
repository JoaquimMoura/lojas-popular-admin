package br.com.lojaspopular.application.notificacao;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.application.notificacao.producer.MensageriaProducer;
import br.com.lojaspopular.application.realtime.RealtimeBus;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.notificacao.enums.NotificacaoStatus;
import br.com.lojaspopular.domain.notificacao.enums.NotificacaoTipo;
import br.com.lojaspopular.domain.notificacao.enums.WhatsAppMensagemStatus;
import br.com.lojaspopular.domain.notificacao.model.NotificacaoEvento;
import br.com.lojaspopular.domain.notificacao.model.WhatsAppMensagem;
import br.com.lojaspopular.domain.notificacao.repository.NotificacaoEventoRepository;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

	private final EmailService emailService;
	private final AuditoriaService auditoriaService;
	private final NotificacaoEventoRepository repo;
	private final MensageriaProducer mensageriaProducer;
	private final RealtimeBus realtimeBus;

	// ==========================
	// NOTIFICAÇÕES DE PEDIDO
	// ==========================

	@Transactional
	public void notificarPedidoCriado(Pedido pedido) {
		enviarEmailComAuditoria(pedido.getUsuario(), "Pedido criado com sucesso!", "pedido-criado.html",
				NotificacaoTipo.PEDIDO_CRIADO);

		String texto = String.format("""
				🛒 Pedido criado!
				Pedido #%d recebido com sucesso.
				Valor total: R$ %.2f
				Em breve você receberá atualizações do seu pedido!
				""", pedido.getId(), pedido.getTotal());

		enviarWhatsAppAsync(pedido.getUsuario(), texto);
	}

	// ==========================
	// NOTIFICAÇÕES DE PAGAMENTO
	// ==========================

	@Transactional
	public void notificarPagamentoAprovado(Pedido pedido) {
		enviarEmailComAuditoria(pedido.getUsuario(), "Pagamento aprovado!", "pagamento-aprovado.html",
				NotificacaoTipo.PAGAMENTO_APROVADO);

		String texto = String.format("""
				✅ Pagamento aprovado!
				Pedido #%d confirmado.
				Valor total: R$ %.2f
				Obrigado por comprar na Lojas Popular Móveis 💙
				""", pedido.getId(), pedido.getTotal());

		enviarWhatsAppAsync(pedido.getUsuario(), texto);
	}

	@Transactional
	public void notificarPagamentoRejeitado(Pedido pedido) {
		enviarEmailComAuditoria(pedido.getUsuario(), "Pagamento não aprovado.", "pagamento-rejeitado.html",
				NotificacaoTipo.PAGAMENTO_REJEITADO);

		String texto = String.format("""
				⚠️ Pagamento não aprovado!
				Pedido #%d foi rejeitado.
				Verifique seus dados ou tente outro método de pagamento.
				""", pedido.getId());

		enviarWhatsAppAsync(pedido.getUsuario(), texto);
	}

	// ==========================
	// MÉTODOS INTERNOS DE APOIO
	// ==========================

	private void enviarEmailComAuditoria(User usuario, String assunto, String template, NotificacaoTipo tipo) {
		NotificacaoEvento evento = NotificacaoEvento.builder().tipo(tipo).assunto(assunto)
				.destinatario(usuario.getEmail()).status(NotificacaoStatus.PENDENTE).build();

		repo.save(evento);

		try {
			emailService.enviarEmail(usuario.getEmail(), assunto, template, usuario);
			evento.setStatus(NotificacaoStatus.ENVIADO);
			auditoriaService.registrar(AuditoriaTipo.valueOf(tipo.name()),
					assunto + " enviado para " + usuario.getEmail());
		} catch (Exception e) {
			evento.setStatus(NotificacaoStatus.FALHA);
			evento.setDetalheErro(e.getMessage());
		}
		
		repo.save(evento);
		
		realtimeBus.publish("notificacao-email", evento);
	}

	private void enviarWhatsAppAsync(User usuario, String conteudo) {

		if (usuario.getTelefone() == null || usuario.getTelefone().isBlank()) {
			
			auditoriaService.registrar(AuditoriaTipo.NOTIFICACAO_FALHA,
					"Usuário sem telefone cadastrado: " + usuario.getEmail());
			return;
		}

		// cria o evento de notificação vinculado à mensagem
		NotificacaoEvento evento = NotificacaoEvento	
												.builder()
													.tipo(NotificacaoTipo.PAGAMENTO_APROVADO)
													.assunto("Envio WhatsApp")
													.destinatario(usuario.getTelefone())
													.status(NotificacaoStatus.PENDENTE)
												.build();
		repo.save(evento);

		WhatsAppMensagem msg = WhatsAppMensagem	
										.builder()
											.destinatario(usuario.getTelefoneNormalizado())
											.conteudo(conteudo)
											.notificacaoEvento(evento)
											.status(WhatsAppMensagemStatus.PENDENTE)
										.build();

		mensageriaProducer.enviarParaFila(msg);
		
		realtimeBus.publish("notificacao-whatsapp-queued", msg);

		auditoriaService.registrar(AuditoriaTipo.NOTIFICACAO_ENVIADA,
				"Mensagem WhatsApp enviada para fila: " + usuario.getTelefone());
	}

}
