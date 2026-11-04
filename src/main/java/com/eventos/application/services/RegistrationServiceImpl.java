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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Casos de uso de inscrição e agenda. As regras (prazo, capacidade, seleção, conflito) vivem em
 * {@link Event}, {@link Activity} e {@link ConflictValidator}; este serviço apenas coordena
 * repositórios. Os métodos são serializados porque verificam e alteram contadores de vaga.
 */
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
    public synchronized Registration registerForEvent(Long eventId, Long userId, Set<Long> activityIds) {
        Event event = findEvent(eventId);
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário com ID " + userId + " não encontrado."));

        Optional<Registration> existing = registrationRepository.findByEventAndUser(eventId, userId);
        if (existing.isPresent() && existing.get().isConfirmed()) {
            throw new BusinessRuleException("Você já está inscrito neste evento.");
        }

        Set<Long> requested = activityIds == null ? Set.of() : new LinkedHashSet<>(activityIds);
        event.assertCanRegister(event.now(), registrationRepository.countConfirmedByEventId(eventId), requested);

        List<Activity> chosen = new ArrayList<>();
        for (Long actId : requested) {
            Activity act = findActivity(actId);
            requireInEvent(act, eventId);
            ConflictValidator.validateParticipantAgendaConflict(act, chosen);
            act.assertHasAvailableSlot();
            chosen.add(act);
        }

        List<Activity> reserved = reserveSlots(chosen);
        try {
            Set<Long> selected = new HashSet<>();
            chosen.forEach(a -> selected.add(a.getId()));
            LocalDateTime now = event.now();
            Registration reg = existing.map(r -> r.reconfirm(selected, now))
                    .orElseGet(() -> new Registration(null, eventId, userId, selected, now, RegistrationStatus.CONFIRMED));
            return registrationRepository.save(reg);
        } catch (RuntimeException e) {
            reserved.forEach(a -> activityRepository.releaseSlot(a.getId()));
            throw e;
        }
    }

    @Override
    public synchronized Registration addActivityToRegistration(Long eventId, Long userId, Long activityId) {
        Event event = findEvent(eventId);
        event.assertActivitySelectionEnabled();
        event.assertCanChangeSelection(event.now());
        Registration reg = registrationRepository.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> new BusinessRuleException("Você precisa primeiro se inscrever no evento antes de escolher atividades."));
        Activity target = findActivity(activityId);
        requireInEvent(target, eventId);
        if (reg.getSelectedActivityIds().contains(activityId)) {
            return reg;
        }
        // O próprio objeto recusa a escolha em inscrição cancelada, antes de qualquer vaga ser reservada.
        reg.selectActivity(activityId);

        ConflictValidator.validateParticipantAgendaConflict(target, getParticipantAgenda(userId, eventId));
        target.assertHasAvailableSlot();
        List<Activity> reserved = reserveSlots(List.of(target));
        try {
            return registrationRepository.save(reg);
        } catch (RuntimeException e) {
            reserved.forEach(a -> activityRepository.releaseSlot(a.getId()));
            throw e;
        }
    }

    @Override
    public synchronized Registration removeActivityFromRegistration(Long eventId, Long userId, Long activityId) {
        Event event = findEvent(eventId);
        event.assertCanChangeSelection(event.now());
        Registration reg = registrationRepository.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada."));
        if (!reg.getSelectedActivityIds().contains(activityId)) {
            return reg;
        }
        if (event.isActivitySelectionRequired() && reg.getSelectedActivityIds().size() <= 1) {
            throw new BusinessRuleException("Este evento exige ao menos uma atividade escolhida; cancele a inscrição se desejar sair.");
        }
        reg.removeActivity(activityId);
        Registration saved = registrationRepository.save(reg);
        activityRepository.findById(activityId)
                .filter(Activity::isRequiresRegistration)
                .ifPresent(a -> activityRepository.releaseSlot(a.getId()));
        return saved;
    }

    @Override
    public synchronized void cancelRegistration(Long eventId, Long userId) {
        Event event = findEvent(eventId);
        event.assertCanCancelRegistration(event.now());
        Registration reg = registrationRepository.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada."));
        Set<Long> toRelease = new HashSet<>(reg.getSelectedActivityIds());
        reg.cancel();
        registrationRepository.save(reg);
        for (Long actId : toRelease) {
            activityRepository.findById(actId)
                    .filter(Activity::isRequiresRegistration)
                    .ifPresent(a -> activityRepository.releaseSlot(a.getId()));
        }
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
        Event event = findEvent(eventId);
        if (!event.isActivitySelectionEnabled()) {
            // Inscrição no evento basta (RN-05): a agenda é a programação inteira, em ordem cronológica.
            return new ArrayList<>(activityRepository.findByEventId(eventId));
        }
        if (regOpt.get().getSelectedActivityIds().isEmpty()) {
            return new ArrayList<>();
        }
        List<Activity> list = new ArrayList<>(activityRepository.findByIds(new ArrayList<>(regOpt.get().getSelectedActivityIds())));
        list.sort(Comparator.comparing((Activity a) -> a.getPeriod().getStart()));
        return list;
    }

    /** Reserva uma vaga por atividade que controla inscrição; desfaz tudo se alguma lotou no meio do caminho. */
    private List<Activity> reserveSlots(List<Activity> activities) {
        List<Activity> reserved = new ArrayList<>();
        for (Activity act : activities) {
            if (!act.isRequiresRegistration()) continue;
            if (!activityRepository.reserveSlot(act.getId())) {
                reserved.forEach(a -> activityRepository.releaseSlot(a.getId()));
                throw new BusinessRuleException("A atividade '" + act.getTitle() + "' não possui mais vagas disponíveis.");
            }
            reserved.add(act);
        }
        return reserved;
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento com ID " + eventId + " não encontrado."));
    }

    private Activity findActivity(Long activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade com ID " + activityId + " não encontrada."));
    }

    private void requireInEvent(Activity activity, Long eventId) {
        if (!eventId.equals(activity.getEventId())) {
            throw new BusinessRuleException("A atividade '" + activity.getTitle() + "' não pertence ao evento informado.");
        }
    }
}
