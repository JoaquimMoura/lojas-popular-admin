package br.com.lojaspopular.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient mpWebClient(
      @Value("${mercadopago.base-url}") String baseUrl,
      @Value("${mercadopago.access-token}") String accessToken
  ) {
    return WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();
  }
}
