package br.com.lojaspopular.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manipulador global de exceções da API.
 * Centraliza o formato de resposta de erros.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
   * Conflito de versão otimista (registro modificado por outro usuário)
   */
  @ExceptionHandler(ConflitoVersaoException.class)
  public ResponseEntity<?> handleConflitoVersao(ConflitoVersaoException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 409,
      "error", "Conflito de concorrência",
      "message", ex.getMessage()
    ));
  }

  /**
   * Falha de lock otimista detectada pelo próprio Hibernate (edições concorrentes na mesma janela)
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<?> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 409,
      "error", "Conflito de concorrência",
      "message", "Este registro foi modificado por outro usuário. Recarregue os dados e tente novamente."
    ));
  }

  /**
   * Violação de constraint no banco (ex.: SKU duplicado)
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
    String causa = ex.getMostSpecificCause().getMessage();
    String message = "Já existe um registro com esses dados.";
    if (causa != null && causa.contains("produtos_sku_key")) {
      message = "Já existe um produto cadastrado com esse SKU. Use outro SKU ou deixe o campo em branco.";
    }

    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 409,
      "error", "Conflito de dados",
      "message", message
    ));
  }

  /**
   * Falhas de acesso a dados não mapeadas acima (ex.: schema desatualizado, coluna
   * inexistente). Evita expor a mensagem crua do SQL/driver ao cliente.
   */
  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<?> handleDataAccess(DataAccessException ex) {
    log.error("Erro de acesso a dados", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 500,
      "error", "Erro ao acessar o banco de dados",
      "message", "Não foi possível completar a operação. Tente novamente ou contate o suporte."
    ));
  }

  /**
   * Recurso estático inexistente (ex.: imagem apagada, favicon) — 404 em vez de 500.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", 404,
      "error", "Not Found",
      "message", "Recurso não encontrado: " + ex.getResourcePath()
    ));
  }

  /**
   * Erros genéricos (fallback)
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGeneric(Exception ex) {
    log.error("Erro não tratado", ex);
    Map<String, Object> body = new HashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", 500);
    body.put("error", "Internal Server Error");
    body.put("message", ex.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
