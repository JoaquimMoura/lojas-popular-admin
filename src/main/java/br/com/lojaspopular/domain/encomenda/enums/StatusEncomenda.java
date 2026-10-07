package br.com.lojaspopular.domain.encomenda.enums;

/** Acompanhamento de encomenda (a compra é feita fora do sistema). */
public enum StatusEncomenda {
  AGUARDANDO_PEDIDO,
  PEDIDO_REALIZADO,
  PARCIALMENTE_RECEBIDA,
  RECEBIDA,
  CANCELADA
}
