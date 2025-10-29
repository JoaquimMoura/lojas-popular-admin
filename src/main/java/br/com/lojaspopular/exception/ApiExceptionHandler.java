package br.com.lojaspopular.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manipulador global de exceções da API.
 * Centraliza o formato de resposta de erros.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * Erros de validação (ex: @Valid)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();

    return ResponseEntity.badRequest().body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 400,
      "error", "Bad Request",
      "message", message
    ));
  }

  /**
   * Erros de regras de negócio (NegocioException)
   */
  @ExceptionHandler(NegocioException.class)
  public ResponseEntity<?> handleNegocio(NegocioException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 400,
      "error", "Negócio Inválido",
      "message", ex.getMessage()
    ));
  }

  /**
   * Erros de recurso não encontrado (NotFoundException)
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<?> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 404,
      "error", "Not Found",
      "message", ex.getMessage()
    ));
  }

  /**
   * Erros genéricos (fallback)
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGeneric(Exception ex) {
    Map<String, Object> body = new HashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", 500);
    body.put("error", "Internal Server Error");
    body.put("message", ex.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
