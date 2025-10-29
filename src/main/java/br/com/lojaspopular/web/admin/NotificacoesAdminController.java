package br.com.lojaspopular.web.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.domain.notificacao.model.NotificacaoEvento;
import br.com.lojaspopular.domain.notificacao.model.WhatsAppMensagem;
import br.com.lojaspopular.domain.notificacao.repository.NotificacaoEventoRepository;
import br.com.lojaspopular.domain.notificacao.repository.WhatsAppMensagemRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/notificacoes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class NotificacoesAdminController {

  private final NotificacaoEventoRepository notifRepo;
  private final WhatsAppMensagemRepository waRepo;

  @GetMapping("/email")
  public ResponseEntity<List<NotificacaoEvento>> listarEmails(
      @RequestParam(required = false) String destinatario) {
    if (destinatario != null && !destinatario.isBlank()) {
      return ResponseEntity.ok(
          notifRepo.findByDestinatarioOrderByDataEnvioDesc(destinatario)
      );
    }
    return ResponseEntity.ok(notifRepo.findAllByOrderByDataEnvioDesc());
  }

  @GetMapping("/whatsapp")
  public ResponseEntity<List<WhatsAppMensagem>> listarWhatsapps(
      @RequestParam(required = false) String destinatario) {
    if (destinatario != null && !destinatario.isBlank()) {
      return ResponseEntity.ok(
          waRepo.findByDestinatarioOrderByDataEnvioDesc(destinatario)
      );
    }
    return ResponseEntity.ok(waRepo.findAll());
  }

  // KPIs simples
  @GetMapping("/kpi")
  public ResponseEntity<?> kpi() {
    var totalEmails = notifRepo.count();
    var totalWhats = waRepo.count();
    return ResponseEntity.ok(
        java.util.Map.of("totalEmails", totalEmails, "totalWhatsApp", totalWhats)
    );
  }
}

