package br.com.lojaspopular.exception;

/** Login ou renovação de token recusados: credenciais ou token inválidos (HTTP 401). */
public class CredenciaisInvalidasException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CredenciaisInvalidasException(String mensagem) {
    super(mensagem);
  }
}
