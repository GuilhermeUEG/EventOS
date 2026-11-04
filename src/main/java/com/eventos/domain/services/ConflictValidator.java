package com.eventos.domain.services;

import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.model.Activity;
import java.util.List;

/**
 * Domain Service com a política uniforme de conflitos (RF-07, RF-18, RN-07):
 * conflito de sala na programação e conflito de horário na agenda do participante
 * são sempre BLOQUEADOS, com mensagem explicando qual atividade colide.
 */
public class ConflictValidator {
    private ConflictValidator() {
    }

    /** Uma sala não pode receber duas atividades ao mesmo tempo. */
    public static void validateActivityRoomConflict(Activity newActivity, List<Activity> existingActivities) {
        if (newActivity == null || existingActivities == null) return;
        for (Activity existing : existingActivities) {
            if (newActivity.conflictsWith(existing)) {
                throw new ConflictException("Conflito de Sala: A sala '" + newActivity.getLocation().getRoom() +
                        "' já está ocupada pela atividade '" + existing.getTitle() + "' no horário " +
                        existing.getPeriod() + ".");
            }
        }
    }

    /** O participante não pode escolher duas atividades com horários sobrepostos. */
    public static void validateParticipantAgendaConflict(Activity targetActivity, List<Activity> currentAgendaActivities) {
        if (targetActivity == null || currentAgendaActivities == null) return;
        for (Activity chosen : currentAgendaActivities) {
            if (targetActivity.overlapsInTimeWith(chosen)) {
                throw new ConflictException("Conflito na sua Agenda! A atividade '" + targetActivity.getTitle() +
                        "' (" + targetActivity.getPeriod() + ") sobrepõe o horário de '" + chosen.getTitle() +
                        "' (" + chosen.getPeriod() + ").");
            }
        }
    }
}
