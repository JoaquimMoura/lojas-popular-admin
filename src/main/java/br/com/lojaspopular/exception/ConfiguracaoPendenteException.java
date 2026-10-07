package br.com.lojaspopular.exception;

import java.util.List;

/**
 * Função bloqueada porque uma decisão comercial ainda não foi configurada
 * (ex.: limite de desconto, arredondamento, perfis de cancelamento).
 */
public class ConfiguracaoPendenteException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final List<String> pendencias;

  public ConfiguracaoPendenteException(String mensagem, List<String> pendencias) {
    super(mensagem);
    this.pendencias = pendencias;
  }

  public ConfiguracaoPendenteException(String mensagem) {
    this(mensagem, List.of());
  }

  public List<String> getPendencias() {
    return pendencias;
  }
}
