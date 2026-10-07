package br.com.lojaspopular.exception;

import java.util.List;
import java.util.Map;

/** Possível cliente duplicado: o usuário precisa confirmar explicitamente para prosseguir. */
public class DuplicidadeClienteException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<Map<String, Object>> duplicados;
  private final boolean bloqueante;

  public DuplicidadeClienteException(String mensagem, List<Map<String, Object>> duplicados, boolean bloqueante) {
    super(mensagem);
    this.duplicados = duplicados;
    this.bloqueante = bloqueante;
  }

  public List<Map<String, Object>> getDuplicados() {
    return duplicados;
  }

  /** CPF repetido nunca pode ser ignorado; telefone/nome repetidos podem ser confirmados. */
  public boolean isBloqueante() {
    return bloqueante;
  }
}
