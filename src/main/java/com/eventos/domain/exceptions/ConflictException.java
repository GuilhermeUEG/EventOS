package com.eventos.domain.exceptions;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
