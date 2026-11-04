package com.eventos.adapters.output.email;

import com.eventos.application.dtos.EmailMessage;
import com.eventos.application.ports.output.EmailDeliveryException;
import com.eventos.application.ports.output.EmailSenderPort;

/**
 * Usado quando nenhum servidor SMTP foi configurado: toda tentativa falha com uma explicação clara,
 * em vez de fingir que o e-mail foi enviado. O certificado continua disponível para download.
 */
public class UnconfiguredEmailAdapter implements EmailSenderPort {
    @Override
    public void send(EmailMessage message) {
        throw new EmailDeliveryException("O envio de e-mail não está configurado neste servidor "
                + "(defina EVENTOS_SMTP_HOST). Baixe o certificado em PDF pelo site.");
    }
}
