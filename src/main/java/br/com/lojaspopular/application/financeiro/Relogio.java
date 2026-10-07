package br.com.lojaspopular.application.financeiro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.lojaspopular.exception.NegocioException;

/**
 * Data de negócio da loja. O servidor roda em UTC; sem fuso explícito, o "hoje" financeiro viraria o dia
 * às 21h (horário de Brasília), jogando lançamentos no dia/mês errado.
 */
@Component
public class Relogio {

  @Value("${app.timezone:America/Sao_Paulo}")
  private String fuso;

  public ZoneId zona() {
    return ZoneId.of(fuso);
  }

  public LocalDate hoje() {
    return LocalDate.now(zona());
  }

  public Instant agora() {
    return Instant.now();
  }

  public static LocalDate primeiroDia(LocalDate data) {
    return data.withDayOfMonth(1);
  }

  /** Converte "aaaa-mm" (ou aaaa-mm-dd) no primeiro dia do mês. */
  public static LocalDate mes(String texto) {
    try {
      String t = texto == null ? "" : texto.trim();
      if (t.length() > 7) {
        return LocalDate.parse(t).withDayOfMonth(1);
      }
      return YearMonth.parse(t).atDay(1);
    } catch (Exception e) {
      throw new NegocioException("Mês inválido: use o formato aaaa-mm.");
    }
  }

  public static String texto(LocalDate mes) {
    return YearMonth.from(mes).toString();
  }

  public Instant inicioDoDia(LocalDate dia) {
    return dia.atStartOfDay(zona()).toInstant();
  }

  /** Início do dia seguinte ao último dia do mês (limite exclusivo). */
  public Instant fimDoMes(LocalDate mes) {
    return mes.plusMonths(1).atStartOfDay(zona()).toInstant();
  }
}
