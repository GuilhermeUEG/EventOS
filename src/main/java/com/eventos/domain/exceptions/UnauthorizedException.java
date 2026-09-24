package com.eventos.domain.exceptions;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
