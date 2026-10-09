package br.com.lojaspopular.domain.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** Normalização para comparar nomes: sem acentos, sem espaços repetidos nas pontas/meio e em minúsculas. */
public final class Normalizacao {

  private Normalizacao() {
  }

  public static String normalizar(String s) {
    if (s == null) {
      return "";
    }
    String t = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return t.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /** Nome para exibição: apara e colapsa espaços, mantendo maiúsculas e acentos como digitado. */
  public static String limpar(String s) {
    return s == null ? null : s.trim().replaceAll("\\s+", " ");
  }
}
