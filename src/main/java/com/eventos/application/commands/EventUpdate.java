package com.eventos.application.commands;

import com.eventos.domain.model.Period;
import java.time.LocalDateTime;

/**
 * Alterações solicitadas a um evento. Campos nulos significam "manter o valor atual".
 */
public record EventUpdate(
        String title,
        String description,
        Period period,
        Integer maxCapacity,
        Boolean activitySelectionEnabled,
        Boolean activitySelectionRequired,
        LocalDateTime registrationDeadline,
        String timeZone
) {
}
