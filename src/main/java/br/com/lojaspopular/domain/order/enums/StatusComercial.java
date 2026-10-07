package br.com.lojaspopular.domain.order.enums;

public enum StatusComercial {
  /** Pedido anterior à gestão de vendas: sem cliente/vendedor/reserva atribuídos. */
  LEGADO,
  RASCUNHO,
  AGUARDANDO_APROVACAO,
  CONFIRMADA,
  CANCELADA
}
