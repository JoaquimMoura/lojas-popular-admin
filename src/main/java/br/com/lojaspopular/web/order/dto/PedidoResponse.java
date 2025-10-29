package br.com.lojaspopular.web.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;

public record PedidoResponse(
  Long id,
  PedidoStatus status,
  BigDecimal subtotal,
  BigDecimal frete,
  BigDecimal total,
  Instant criadoEm,
  List<ItemPedidoResponse> itens
) {}
