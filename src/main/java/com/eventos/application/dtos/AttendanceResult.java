package com.eventos.application.dtos;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;

/**
 * Resultado de uma marcação de frequência: o registro, se ele já existia (repetição acidental
 * não duplica) e a situação de presença calculada após a operação.
 */
public record AttendanceResult(AttendanceRecord record, boolean alreadyRecorded, AttendanceStatus status) {
}
