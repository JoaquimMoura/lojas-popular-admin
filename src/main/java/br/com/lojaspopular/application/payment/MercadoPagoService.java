package br.com.lojaspopular.application.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import br.com.lojaspopular.application.payment.PaymentGatewayStrategy.PaymentInfo;
import br.com.lojaspopular.application.payment.PaymentGatewayStrategy.PaymentInit;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.payment.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MercadoPagoService implements PaymentGatewayStrategy {

  private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

  private final WebClient mpWebClient;

  @Value("${mercadopago.base-url}")
  private String baseUrl;

  /**
   * Cria pagamento no Mercado Pago.
   * Suporta PIX (pagamento instantâneo) e Checkout Pro (cartão/boleto).
   */
  @Override
  public PaymentInit createPayment(Pedido pedido, PaymentMethod method) {
    BigDecimal amount = pedido.getTotal();

    try {
      if (method == PaymentMethod.PIX) {
        return criarPagamentoPix(pedido, amount);
      } else {
        return criarCheckoutPro(pedido, amount, method);
      }
    } catch (WebClientResponseException ex) {
      log.error("❌ Erro HTTP ao criar pagamento no Mercado Pago: status={}, body={}",
          ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
      throw new IllegalStateException("Falha ao criar pagamento no Mercado Pago: " + ex.getStatusCode());
    } catch (Exception e) {
      log.error("❌ Erro inesperado ao criar pagamento: {}", e.getMessage(), e);
      throw new IllegalStateException("Erro inesperado ao criar pagamento no Mercado Pago");
    }
  }

  /**
   * Cria pagamento PIX com QR Code e chave de idempotência.
   */
  private PaymentInit criarPagamentoPix(Pedido pedido, BigDecimal amount) {
    Map<String, Object> body = new HashMap<>();
    body.put("transaction_amount", amount);
    body.put("description", "Pedido #" + pedido.getId());
    body.put("payment_method_id", "pix");

    Map<String, Object> payer = new HashMap<>();
    payer.put("email", pedido.getUsuario() != null ? pedido.getUsuario().getEmail() : "cliente@test.com");
    body.put("payer", payer);

    log.info("🟢 Criando pagamento PIX para pedido {} no valor de R$ {}", pedido.getId(), amount);

    Map<String, Object> resp = mpWebClient.post()
        .uri("/v1/payments")
        .header("X-Idempotency-Key", "pedido-" + pedido.getId()) // garante que não duplica
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    if (resp == null || resp.isEmpty()) {
      throw new IllegalStateException("Resposta vazia do Mercado Pago ao criar PIX");
    }

    String paymentId = String.valueOf(resp.get("id"));
    Map<String, Object> poi = (Map<String, Object>) resp.get("point_of_interaction");
    Map<String, Object> tx = poi != null
        ? (Map<String, Object>) poi.get("transaction_data")
        : Map.of();

    Map<String, Object> extra = new HashMap<>();
    extra.put("qrCode", tx.getOrDefault("qr_code", ""));
    extra.put("qrCodeBase64", tx.getOrDefault("qr_code_base64", ""));
    extra.put("ticketUrl", tx.getOrDefault("ticket_url", ""));
    extra.put("status", resp.get("status"));

    log.info("✅ Pagamento PIX criado com sucesso. ID: {}, status: {}", paymentId, resp.get("status"));

    return new PaymentInit(paymentId, PaymentMethod.PIX, amount, extra);
  }

  /**
   * Cria preferência de pagamento Checkout Pro (cartão ou boleto).
   */
  private PaymentInit criarCheckoutPro(Pedido pedido, BigDecimal amount, PaymentMethod method) {
    Map<String, Object> item = Map.of(
        "title", "Pedido #" + pedido.getId(),
        "quantity", 1,
        "unit_price", amount
    );

    Map<String, Object> preference = Map.of(
        "items", new Object[]{ item },
        "back_urls", Map.of(
            "success", "https://sualoja.com/pedido/sucesso/" + pedido.getId(),
            "failure", "https://sualoja.com/pedido/erro/" + pedido.getId(),
            "pending", "https://sualoja.com/pedido/pendente/" + pedido.getId()
        ),
        "auto_return", "approved"
    );

    log.info("🟢 Criando Checkout Pro ({}), pedido {}, valor R$ {}", method, pedido.getId(), amount);

    Map<String, Object> resp = mpWebClient.post()
        .uri("/checkout/preferences")
        .header("X-Idempotency-Key", UUID.randomUUID().toString())
        .bodyValue(preference)
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    if (resp == null || resp.isEmpty()) {
      throw new IllegalStateException("Resposta vazia ao criar preferência de pagamento");
    }

    String prefId = String.valueOf(resp.get("id"));
    String initPoint = (String) resp.getOrDefault("init_point", resp.get("sandbox_init_point"));

    Map<String, Object> extra = Map.of(
        "initPoint", initPoint,
        "preferenceId", prefId
    );

    log.info("✅ Preferência de pagamento criada: {} → {}", prefId, initPoint);
    return new PaymentInit(prefId, method, amount, extra);
  }

  /**
   * Consulta o status de um pagamento pelo ID.
   */
  @SuppressWarnings("unchecked")
@Override
  public PaymentInfo fetchPayment(String paymentId) {
    try {
      Map<String, Object> resp = mpWebClient.get()
          .uri("/v1/payments/{id}", paymentId)
          .retrieve()
          .bodyToMono(Map.class)
          .block();

      if (resp == null) {
        throw new IllegalStateException("Pagamento não encontrado no Mercado Pago");
      }

      String status = String.valueOf(resp.get("status"));
      BigDecimal amount = new BigDecimal(String.valueOf(resp.get("transaction_amount")));

      log.info("📦 Pagamento {} consultado. Status: {}", paymentId, status);
      return new PaymentInfo(paymentId, status, amount);

    } catch (WebClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new IllegalStateException("Pagamento não encontrado (404)");
      }
      log.error("❌ Erro ao consultar pagamento Mercado Pago: {}", e.getResponseBodyAsString(), e);
      throw new IllegalStateException("Erro ao consultar pagamento: " + e.getStatusCode());
    }
  }
}
