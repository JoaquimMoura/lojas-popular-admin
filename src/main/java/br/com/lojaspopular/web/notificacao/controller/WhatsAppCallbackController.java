package br.com.lojaspopular.web.notificacao.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.com.lojaspopular.application.realtime.RealtimeBus;
import br.com.lojaspopular.domain.notificacao.enums.WhatsAppMensagemStatus;
import br.com.lojaspopular.domain.notificacao.model.WhatsAppMensagem;
import br.com.lojaspopular.domain.notificacao.repository.WhatsAppMensagemRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/whatsapp/callback")
@RequiredArgsConstructor
public class WhatsAppCallbackController {

  private final WhatsAppMensagemRepository repo;
  private final RealtimeBus realtimeBus;

  @PostMapping
  public ResponseEntity<Map<String,String>> status(@RequestBody Map<String, Object> body) {

	 // Exemplo simplificado: adapte de acordo com payload real da Zenvia
    try {
     
    	String mensagemId = String.valueOf(body.get("messageId"));
      String status = String.valueOf(body.getOrDefault("status", "DELIVERED")).toUpperCase();

      WhatsAppMensagem msg = repo.findAll().stream()
          .filter(m -> mensagemId.equals(m.getMensagemId()))
          .findFirst()
          .orElse(null);

      if (msg != null) {
        switch (status) {
          case "DELIVERED" -> msg.setStatus(WhatsAppMensagemStatus.ENTREGUE);
          case "READ"      -> msg.setStatus(WhatsAppMensagemStatus.LIDA);
          case "FAILED", "ERROR" -> msg.setStatus(WhatsAppMensagemStatus.FALHA);
          default -> { /* ignore */ }
        }
        repo.save(msg);
        realtimeBus.publish("notificacao-whatsapp-status", msg);
      }

      return ResponseEntity.ok(Map.of("ok","true"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}

