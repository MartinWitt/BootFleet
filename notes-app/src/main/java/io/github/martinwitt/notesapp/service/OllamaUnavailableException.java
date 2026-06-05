package io.github.martinwitt.notesapp.service;

public class OllamaUnavailableException extends RuntimeException {
    public OllamaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
