package br.com.lojaspopular.web.payment.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.payment.PaymentService;
import br.com.lojaspopular.web.payment.dto.PaymentDtos.PaymentCreateRequest;
import br.com.lojaspopular.web.payment.dto.PaymentDtos.WebhookNotification;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping("/create")
  public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody PaymentCreateRequest req) {
    var resp = paymentService.iniciarPagamento(req.orderId(), req.method());
    return ResponseEntity.ok(resp);
  }

  // Mercado Pago Webhook
  @PostMapping("/webhook")
  public ResponseEntity<Map<String, String>> webhook(@RequestBody(required = false) WebhookNotification notif,
                                                     HttpServletRequest request) {
    try {
      // (Opcional) Validar assinatura aqui — ver método auxiliar abaixo (vNext).
      String paymentId = null;

      // Dois formatos possíveis: body estruturado OU query params topic/type+data.id
      if (notif != null && notif.data() != null && notif.data().id() != null) {
        paymentId = notif.data().id();
      } else {
        paymentId = request.getParameter("data.id");
      }

      if (paymentId != null) {
        paymentService.atualizarStatusPorPagamento(paymentId);
      }

      return ResponseEntity.ok(Map.of("status", "ok"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}
