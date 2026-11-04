package com.eventos.application.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dados da declaração para palestrante ou apresentador (RF-35): reflete a atividade e o papel exercido.
 */
public record SpeakerDeclarationDto(
        String speakerName,
        String roleInActivity,
        String activityTitle,
        String eventTitle,
        LocalDateTime activityStart,
        LocalDateTime activityEnd,
        double activityHours,
        LocalDate issueDate
) {
}
