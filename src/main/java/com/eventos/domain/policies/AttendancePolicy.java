package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Strategy para a frequência configurável por atividade (RF-19, RF-23, RN-09, ROO-05, ROO-10).
 *
 * <p>Cada política responde às duas perguntas que variam: "qual a situação do participante com estes
 * registros?" e "o que fazer quando ele lê o QR Code agora?". Assim nenhum serviço precisa conhecer
 * os tipos concretos de política.</p>
 */
public interface AttendancePolicy extends Serializable {
    String getPolicyKey();

    String getPolicyName();

    String getPolicyDescription();

    AttendanceStatus evaluate(List<AttendanceRecord> records);

    AttendanceDecision decideQrMark(List<AttendanceRecord> records, LocalDateTime now);
}
