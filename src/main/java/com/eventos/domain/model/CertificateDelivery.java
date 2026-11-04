package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Registro de uma tentativa de envio do certificado por e-mail (RF-34). A falha é um resultado
 * registrado, não uma exceção: o certificado continua disponível para download.
 */
public class CertificateDelivery implements Serializable {
    /** Dois envios bem-sucedidos dentro desta janela contam como o mesmo (clique repetido). */
    public static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(60);

    private final Long id;
    private final Long certificateId;
    private final String recipient;
    private final DeliveryStatus status;
    private final String detail;
    private final LocalDateTime attemptedAt;

    public CertificateDelivery(Long id, Long certificateId, String recipient, DeliveryStatus status,
                               String detail, LocalDateTime attemptedAt) {
        if (certificateId == null) throw new ValidationException("ID do certificado é obrigatório para o envio.");
        if (recipient == null || recipient.isBlank()) throw new ValidationException("Destinatário do envio é obrigatório.");
        if (status == null) throw new ValidationException("Situação do envio é obrigatória.");
        this.id = id;
        this.certificateId = certificateId;
        this.recipient = recipient;
        this.status = status;
        this.detail = detail == null ? "" : detail;
        this.attemptedAt = attemptedAt != null ? attemptedAt : LocalDateTime.now();
    }

    public static CertificateDelivery sent(Long certificateId, String recipient, LocalDateTime when) {
        return new CertificateDelivery(null, certificateId, recipient, DeliveryStatus.SENT,
                "Certificado enviado em PDF para " + recipient + ".", when);
    }

    public static CertificateDelivery failed(Long certificateId, String recipient, String reason, LocalDateTime when) {
        return new CertificateDelivery(null, certificateId, recipient, DeliveryStatus.FAILED, reason, when);
    }

    public boolean isSentWithin(Duration window, LocalDateTime now) {
        return status == DeliveryStatus.SENT && Duration.between(attemptedAt, now).compareTo(window) < 0;
    }

    public Long getId() {
        return id;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public String getRecipient() {
        return recipient;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }
}
