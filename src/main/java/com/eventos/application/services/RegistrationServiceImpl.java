package com.eventos.application.services;

import com.eventos.application.ports.input.RegistrationUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.RegistrationStatus;
import com.eventos.domain.services.ConflictValidator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RegistrationServiceImpl implements RegistrationUseCase {
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public RegistrationServiceImpl(RegistrationRepository registrationRepository,
                                   EventRepository eventRepository,
                                   ActivityRepository activityRepository,
                                   UserRepository userRepository) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Registration registerForEvent(Long eventId, Long userId, Set<Long> activityIds) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento com ID " + eventId + " não encontrado."));
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário com ID " + userId + " não encontrado."));

        if (!event.isEnrollmentOpen()) {
            throw new BusinessRuleException("As inscrições para este evento estão fechadas (" + event.getStatus().getDescription() + ").");
        }
        if (!event.isRegistrationWithinDeadline(LocalDateTime.now(ZoneId.of(event.getTimeZone())))) {
            throw new BusinessRuleException("O prazo de inscrição deste evento foi encerrado.");
        }
        if (event.isActivitySelectionRequired() && (activityIds == null || activityIds.isEmpty())) {
            throw new BusinessRuleException("Selecione ao menos uma atividade para concluir a inscrição.");
        }
        if (!event.isActivitySelectionEnabled() && activityIds != null && !activityIds.isEmpty()) {
            throw new BusinessRuleException("Este evento não permite escolha individual de atividades.");
        }

        // Verifica capacidade do evento
        int confirmedCount = registrationRepository.countConfirmedByEventId(eventId);
        if (confirmedCount >= event.getMaxCapacity()) {
            throw new BusinessRuleException("O evento atingiu a capacidade máxima de " + event.getMaxCapacity() + " participantes.");
        }

        Optional<Registration> existing = registrationRepository.findByEventAndUser(eventId, userId);
        if (existing.isPresent() && existing.get().isConfirmed()) {
            throw new BusinessRuleException("Você já está inscrito neste evento.");
        }

        Set<Long> selected = new HashSet<>();
        if (activityIds != null && !activityIds.isEmpty()) {
            List<Activity> chosenActivities = new ArrayList<>();
            for (Long actId : activityIds) {
                if (selected.contains(actId)) continue;
                Activity act = activityRepository.findById(actId)
                        .orElseThrow(() -> new EntityNotFoundException("Atividade com ID " + actId + " não encontrada."));
                if (!eventId.equals(act.getEventId())) {
                    throw new BusinessRuleException("A atividade '" + act.getTitle() + "' não pertence ao evento informado.");
                }
                
                // Valida conflito com outras selecionadas
                ConflictValidator.validateParticipantAgendaConflict(act, chosenActivities);
                if (act.isRequiresRegistration() && !act.hasAvailableSlots()) {
                    throw new BusinessRuleException("A atividade '" + act.getTitle() + "' não possui vagas disponíveis.");
                }
                chosenActivities.add(act);
                selected.add(act.getId());
            }
            // Só altera contadores depois que todo o conjunto foi validado.
            for (Activity act : chosenActivities) {
                if (act.isRequiresRegistration()) {
                    act.bookSlot();
                    activityRepository.updateEnrollments(act.getId(), act.getCurrentEnrollments());
                }
            }
        }

        Registration reg = new Registration(existing.map(Registration::getId).orElse(null),
                eventId, userId, selected, LocalDateTime.now(), RegistrationStatus.CONFIRMED);
        return registrationRepository.save(reg);
    }

    @Override
    public Registration addActivityToRegistration(Long eventId, Long userId, Long activityId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento não encontrado."));
        if (!event.isActivitySelectionEnabled()) {
            throw new BusinessRuleException("Este evento não permite escolha individual de atividades.");
        }
        Registration reg = registrationRepository.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> new BusinessRuleException("Você precisa primeiro se inscrever no evento antes de escolher atividades."));

        Activity target = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        if (!eventId.equals(target.getEventId())) {
            throw new BusinessRuleException("A atividade não pertence ao evento informado.");
        }
        if (reg.getSelectedActivityIds().contains(activityId)) {
            return reg;
        }

        List<Activity> currentAgenda = getParticipantAgenda(userId, eventId);
        ConflictValidator.validateParticipantAgendaConflict(target, currentAgenda);

        if (target.isRequiresRegistration()) {
            target.bookSlot();
            activityRepository.updateEnrollments(target.getId(), target.getCurrentEnrollments());
        }

        reg.selectActivity(activityId);
        return registrationRepository.save(reg);
    }

    @Override
    public Registration removeActivityFromRegistration(Long eventId, Long userId, Long activityId) {
        Registration reg = registrationRepository.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada."));
        if (!reg.getSelectedActivityIds().contains(activityId)) {
            return reg;
        }

        Activity target = activityRepository.findById(activityId).orElse(null);
        if (target != null && target.isRequiresRegistration()) {
            target.releaseSlot();
            activityRepository.updateEnrollments(target.getId(), target.getCurrentEnrollments());
        }

        reg.removeActivity(activityId);
        return registrationRepository.save(reg);
    }

    @Override
    public void cancelRegistration(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento não encontrado."));
        if (!event.isRegistrationWithinDeadline(LocalDateTime.now(ZoneId.of(event.getTimeZone())))) {
            throw new BusinessRuleException("O prazo para cancelamento da inscrição foi encerrado.");
        }
        Registration reg = registrationRepository.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada."));
        
        // Libera vagas das atividades
        for (Long actId : reg.getSelectedActivityIds()) {
            Activity act = activityRepository.findById(actId).orElse(null);
            if (act != null && act.isRequiresRegistration()) {
                act.releaseSlot();
                activityRepository.updateEnrollments(act.getId(), act.getCurrentEnrollments());
            }
        }

        reg.cancel();
        registrationRepository.save(reg);
    }

    @Override
    public List<Registration> getEventRegistrations(Long eventId) {
        return registrationRepository.findByEventId(eventId);
    }

    @Override
    public List<Registration> getUserRegistrations(Long userId) {
        return registrationRepository.findByUserId(userId);
    }

    @Override
    public List<Activity> getParticipantAgenda(Long userId, Long eventId) {
        Optional<Registration> regOpt = registrationRepository.findByEventAndUser(eventId, userId);
        if (regOpt.isEmpty() || !regOpt.get().isConfirmed()) {
            return new ArrayList<>();
        }
        Set<Long> ids = regOpt.get().getSelectedActivityIds();
        if (ids.isEmpty()) return new ArrayList<>();
        
        List<Activity> list = activityRepository.findByIds(new ArrayList<>(ids));
        // Ordena cronologicamente por horário de início (RF-17)
        list.sort((a, b) -> a.getPeriod().getStart().compareTo(b.getPeriod().getStart()));
        return list;
    }
}
