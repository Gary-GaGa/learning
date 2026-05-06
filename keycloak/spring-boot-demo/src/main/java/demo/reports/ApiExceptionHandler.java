package demo.reports;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ReportNotFoundException.class)
  public ResponseEntity<Map<String, Object>> notFound(ReportNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
        "error", "not_found",
        "message", ex.getMessage()));
  }

  @ExceptionHandler(ReportForbiddenException.class)
  public ResponseEntity<Map<String, Object>> forbidden(ReportForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
        "error", "forbidden",
        "message", ex.getMessage()));
  }
}
