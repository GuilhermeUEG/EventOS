package com.eventos.domain.exceptions;

public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super(message);
    }
}
