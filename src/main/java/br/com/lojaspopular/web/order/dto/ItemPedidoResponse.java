package br.com.lojaspopular.web.order.dto;

import java.math.BigDecimal;

public record ItemPedidoResponse(
  String produto,
  Integer quantidade,
  BigDecimal precoUnitario,
  BigDecimal total
) {}
