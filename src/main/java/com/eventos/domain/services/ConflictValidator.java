package com.eventos.domain.services;

import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.model.Activity;
import java.util.List;

/**
 * Domain Service para validação de conflitos de horário e salas (ROO-04, RF-07, RF-18, RN-07).
 */
public class ConflictValidator {

    /**
     * Valida conflitos de sala/local entre atividades em um mesmo evento.
     */
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

    /**
     * Valida se uma nova atividade selecionada pelo participante conflita em horário com as já escolhidas na agenda.
     */
    public static void validateParticipantAgendaConflict(Activity targetActivity, List<Activity> currentAgendaActivities) {
        if (targetActivity == null || currentAgendaActivities == null) return;

        for (Activity chosen : currentAgendaActivities) {
            if (targetActivity.getId() != null && targetActivity.getId().equals(chosen.getId())) {
                continue;
            }
            if (targetActivity.getPeriod().overlaps(chosen.getPeriod())) {
                throw new ConflictException("Conflito na sua Agenda! A atividade '" + targetActivity.getTitle() +
                        "' (" + targetActivity.getPeriod() + ") sobrepõe o horário de '" + chosen.getTitle() +
                        "' (" + chosen.getPeriod() + ").");
            }
        }
    }
}
