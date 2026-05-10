package pl.gorskie.wyprawy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.gorskie.wyprawy.service.TrailNotFoundException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Centralny handler błędów API — zwraca czytelne JSON zamiast stacktrace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TrailNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TrailNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Wewnętrzny błąd serwera: " + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
