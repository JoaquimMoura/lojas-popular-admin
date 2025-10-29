package br.com.lojaspopular.web.payment.dto;

import br.com.lojaspopular.domain.payment.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public class PaymentDtos {
  public record PaymentCreateRequest(
      @NotNull Long orderId,
      @NotNull PaymentMethod method // PIX, CARD, BOLETO
  ) {}

  // Notificação genérica do MP (podem vir outros campos)
  public record WebhookNotification(
      String id,
      String type,
      Data data
  ) {
    public record Data(String id) {}
  }
}

