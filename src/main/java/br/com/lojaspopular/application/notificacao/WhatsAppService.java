package br.com.lojaspopular.application.notificacao;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import br.com.lojaspopular.config.WhatsAppProperties;
import br.com.lojaspopular.domain.notificacao.enums.WhatsAppMensagemStatus;
import br.com.lojaspopular.domain.notificacao.model.WhatsAppMensagem;
import br.com.lojaspopular.domain.notificacao.repository.WhatsAppMensagemRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppService {

  private final WhatsAppProperties props;
  private final WhatsAppMensagemRepository repo;
  private final RestTemplate restTemplate = new RestTemplate();

  public WhatsAppMensagem enviar(String telefone, String conteudo) {
    
	WhatsAppMensagem msg = WhatsAppMensagem.builder()
        .destinatario(telefone)
        .conteudo(conteudo)
        .canal("whatsapp")
        .provedor(props.getProvider())
        .dataEnvio(Instant.now())
        .status(WhatsAppMensagemStatus.PENDENTE)
        .build();
	
    repo.save(msg);

    try {
      Map<String, Object> body = Map.of(
          "from", props.getRemetente(),
          "to", telefone,
          "contents", new Object[]{ Map.of("type", "text", "text", conteudo) }
      );

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(props.getApiKey());

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
      ResponseEntity<String> response = restTemplate.exchange(
          props.getBaseUrl(), HttpMethod.POST, entity, String.class);

      msg.setMensagemId(response.getBody());
      msg.setStatus(WhatsAppMensagemStatus.ENVIADA);
    } catch (Exception e) {
      msg.setStatus(WhatsAppMensagemStatus.FALHA);
    }

    return repo.save(msg);
  }
}

