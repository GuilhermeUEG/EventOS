package com.eventos.support;

import com.eventos.application.dtos.EmailMessage;
import com.eventos.application.ports.output.EmailDeliveryException;
import com.eventos.application.ports.output.EmailSenderPort;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adaptador de saída falso: guarda o que "enviou" e pode simular falha de entrega.
 */
public class RecordingEmailSender implements EmailSenderPort {
    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile String failureReason;

    @Override
    public void send(EmailMessage message) {
        if (failureReason != null) throw new EmailDeliveryException(failureReason);
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return sent;
    }

    /** A partir daqui os envios falham com este motivo; {@code null} volta ao normal. */
    public void failWith(String reason) {
        this.failureReason = reason;
    }
}
