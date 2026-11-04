package com.eventos.adapters.input.desktop;

/**
 * Falha em uma chamada à API: carrega o status HTTP (0 quando não houve resposta) e a mensagem
 * compreensível devolvida pelo servidor em {"error": "..."}.
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int getStatus() {
        return status;
    }
}
