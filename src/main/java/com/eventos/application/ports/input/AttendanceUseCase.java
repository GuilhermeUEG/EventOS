package com.eventos.application.ports.input;

import com.eventos.application.dtos.AttendanceResult;
import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.application.dtos.QrCodeDto;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.util.List;

public interface AttendanceUseCase {
    QrCodeDto generateQrCode(Long activityId);

    /** Registra a presença lida por QR Code para o participante autenticado. */
    AttendanceResult recordQrAttendance(String qrToken, Long userId);

    /** Lançamento manual por organizador, com justificativa obrigatória (RN-11). */
    AttendanceRecord recordManualAttendance(Long activityId, Long userId, Long organizerId, AttendanceType type, String notes);

    /** Correção manual: invalida as marcações vigentes do participante, preservando o histórico (RN-12). */
    AttendanceRecord revokeAttendance(Long activityId, Long userId, Long organizerId, String notes);

    List<AttendanceRecord> getActivityRecords(Long activityId);

    AttendanceStatus evaluateParticipantAttendance(Long activityId, Long userId);

    List<AttendanceStatusDto> getActivityAttendanceOverview(Long activityId);
}
