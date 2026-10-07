package br.com.lojaspopular.domain.comercial.enums;

import java.math.RoundingMode;

public enum Arredondamento {
  HALF_UP(RoundingMode.HALF_UP),
  HALF_EVEN(RoundingMode.HALF_EVEN),
  DOWN(RoundingMode.DOWN),
  UP(RoundingMode.UP);

  private final RoundingMode mode;

  Arredondamento(RoundingMode mode) {
    this.mode = mode;
  }

  public RoundingMode mode() {
    return mode;
  }
}
