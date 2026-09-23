package com.example.urlShortener.exception;

import org.springframework.http.HttpStatus;

public class UrlShortenerException extends RuntimeException {
    private final HttpStatus status;

    public UrlShortenerException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
