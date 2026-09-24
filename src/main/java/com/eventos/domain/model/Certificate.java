package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Certificado de participação emitido com código verificável (RF-32, RF-33, RN-16).
 */
public class Certificate implements Serializable {
    private final Long id;
    private final Long eventId;
    private final Long userId;
    private final String participantName;
    private final String eventTitle;
    private final double completionHours;
    private final LocalDate issueDate;
    private final String verificationCode;

    public Certificate(Long id, Long eventId, Long userId, String participantName,
                       String eventTitle, double completionHours, LocalDate issueDate, String verificationCode) {
        if (eventId == null) throw new ValidationException("ID do evento é obrigatório para emissão de certificado.");
        if (userId == null) throw new ValidationException("ID do usuário é obrigatório.");
        if (participantName == null || participantName.trim().isEmpty()) throw new ValidationException("Nome do participante é obrigatório.");
        if (eventTitle == null || eventTitle.trim().isEmpty()) throw new ValidationException("Título do evento é obrigatório.");

        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.participantName = participantName.trim();
        this.eventTitle = eventTitle.trim();
        this.completionHours = completionHours > 0 ? completionHours : 1.0;
        this.issueDate = issueDate != null ? issueDate : LocalDate.now();
        this.verificationCode = (verificationCode != null && !verificationCode.trim().isEmpty())
                ? verificationCode.trim()
                : UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public double getCompletionHours() {
        return completionHours;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Certificate that = (Certificate) o;
        return Objects.equals(verificationCode, that.verificationCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verificationCode);
    }
}
