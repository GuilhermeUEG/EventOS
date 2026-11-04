package com.eventos.application.ports.output;

import com.eventos.application.dtos.EmailMessage;

public interface EmailSenderPort {
    /** Envia a mensagem ou lança {@link EmailDeliveryException} explicando o motivo. */
    void send(EmailMessage message);
}
