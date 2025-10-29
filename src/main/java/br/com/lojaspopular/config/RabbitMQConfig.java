package br.com.lojaspopular.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  public static final String QUEUE_WHATSAPP = "notificacao.whatsapp";
  public static final String EXCHANGE_NOTIFICACAO = "notificacao.exchange";
  public static final String ROUTING_KEY_WHATSAPP = "notificacao.whatsapp.key";

  @Bean
  public Queue queueWhatsApp() {
    return QueueBuilder.durable(QUEUE_WHATSAPP).build();
  }

  @Bean
  public DirectExchange exchangeNotificacao() {
    return new DirectExchange(EXCHANGE_NOTIFICACAO);
  }

  @Bean
  public Binding bindingWhatsApp() {
    return BindingBuilder.bind(queueWhatsApp())
        .to(exchangeNotificacao())
        .with(ROUTING_KEY_WHATSAPP);
  }
}

