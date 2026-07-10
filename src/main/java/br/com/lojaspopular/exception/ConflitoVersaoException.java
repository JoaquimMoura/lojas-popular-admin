package br.com.lojaspopular.exception;

public class ConflitoVersaoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ConflitoVersaoException(String mensagem) {
		super(mensagem);
	}
}
