package br.com.lojaspopular.application.payment;

import java.math.BigDecimal;
import java.util.Map;

import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.payment.enums.PaymentMethod;

public interface PaymentGatewayStrategy {

  record PaymentInit(
      String paymentId,
      PaymentMethod method,
      BigDecimal amount,
      Map<String, Object> extra // ex.: qrCode, initPoint
  ) {}

  record PaymentInfo(
      String paymentId,
      String status, // raw do gateway
      BigDecimal amount
  ) {}

  PaymentInit createPayment(Pedido pedido, PaymentMethod method);

  PaymentInfo fetchPayment(String paymentId);
}

