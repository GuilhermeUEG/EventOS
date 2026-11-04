package com.eventos.domain.exceptions;

/**
 * Usuário autenticado, porém sem permissão para a operação (HTTP 403).
 */
public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super(message);
    }
}
