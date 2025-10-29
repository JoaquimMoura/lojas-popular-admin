package br.com.lojaspopular.exception;

public class NotFoundException extends RuntimeException {
	
	private static final long serialVersionUID = 533905997333286206L;

	public NotFoundException(String msg) {
		super(msg);
	}
}
