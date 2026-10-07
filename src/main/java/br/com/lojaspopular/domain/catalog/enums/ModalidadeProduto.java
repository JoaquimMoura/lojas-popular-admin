package br.com.lojaspopular.domain.catalog.enums;

public enum ModalidadeProduto {
  PRONTA_ENTREGA,
  ENCOMENDA,
  AMBAS;

  public boolean aceita(br.com.lojaspopular.domain.order.enums.ModalidadeItem m) {
    return this == AMBAS || this.name().equals(m.name());
  }
}
