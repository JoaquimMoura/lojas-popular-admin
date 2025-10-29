package br.com.lojaspopular.application.notificacao.producer;

import static br.com.lojaspopular.config.RabbitMQConfig.EXCHANGE_NOTIFICACAO;
import static br.com.lojaspopular.config.RabbitMQConfig.ROUTING_KEY_WHATSAPP;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MensageriaProducer {

	private final RabbitTemplate rabbitTemplate;

	public void enviarParaFila(Object payload) {
		rabbitTemplate.convertAndSend(EXCHANGE_NOTIFICACAO, ROUTING_KEY_WHATSAPP, payload);
	}
}
