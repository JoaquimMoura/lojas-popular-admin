package br.com.lojaspopular.application.notificacao.listener;

import static br.com.lojaspopular.config.RabbitMQConfig.QUEUE_WHATSAPP;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.lojaspopular.application.notificacao.WhatsAppService;
import br.com.lojaspopular.application.realtime.RealtimeBus;
import br.com.lojaspopular.domain.notificacao.enums.WhatsAppMensagemStatus;
import br.com.lojaspopular.domain.notificacao.model.WhatsAppMensagem;
import br.com.lojaspopular.domain.notificacao.repository.WhatsAppMensagemRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WhatsAppConsumer {

	private final WhatsAppService whatsappService;
	private final WhatsAppMensagemRepository repo;
	private final RealtimeBus realtimeBus;

	@RabbitListener(queues = QUEUE_WHATSAPP)
	public void consumirMensagem(WhatsAppMensagem mensagem) {
		try {
			var enviada = whatsappService.enviar(mensagem.getDestinatario(), mensagem.getConteudo());
			mensagem.setMensagemId(enviada.getMensagemId());
			mensagem.setStatus(WhatsAppMensagemStatus.ENVIADA);
		} catch (Exception e) {
			mensagem.setStatus(WhatsAppMensagemStatus.FALHA);
		}
		
		realtimeBus.publish("notificacao-whatsapp-status", mensagem);
		repo.save(mensagem);
	}
}
