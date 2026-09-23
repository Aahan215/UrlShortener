package com.example.urlShortener.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UrlShortenerException.class)
    public ResponseEntity<Map<String, Object>> handleUrlShortenerException(UrlShortenerException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", ex.getStatus().value(),
            "error", ex.getStatus().getReasonPhrase(),
            "message", ex.getMessage()
        ));
    }
}
