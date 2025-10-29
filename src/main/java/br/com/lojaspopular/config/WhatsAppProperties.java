package br.com.lojaspopular.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppProperties {
	private String baseUrl;
	private String apiKey;
	private String provider;
	private String remetente;
}
