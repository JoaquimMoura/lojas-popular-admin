package br.com.lojaspopular.application.cliente;

/** Utilitários de documentos (CPF) e normalização de dígitos. */
public final class Documentos {

  private Documentos() {
  }

  public static String somenteDigitos(String valor) {
    return valor == null ? "" : valor.replaceAll("\\D", "");
  }

  /** Valida CPF (11 dígitos, não repetidos, dígitos verificadores). */
  public static boolean cpfValido(String cpf) {
    String d = somenteDigitos(cpf);
    if (d.length() != 11 || d.chars().distinct().count() == 1) {
      return false;
    }
    return digito(d, 9) == d.charAt(9) - '0' && digito(d, 10) == d.charAt(10) - '0';
  }

  private static int digito(String d, int tamanho) {
    int soma = 0;
    for (int i = 0; i < tamanho; i++) {
      soma += (d.charAt(i) - '0') * (tamanho + 1 - i);
    }
    int r = (soma * 10) % 11;
    return r == 10 ? 0 : r;
  }
}
