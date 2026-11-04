package com.eventos.application.ports.output;

/**
 * Falha ao entregar um e-mail. A mensagem é segura para exibir ao usuário (nunca contém credenciais).
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
