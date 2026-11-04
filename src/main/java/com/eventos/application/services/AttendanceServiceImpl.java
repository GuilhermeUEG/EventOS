package com.eventos.application.services;

import com.eventos.application.dtos.AttendanceResult;
import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.application.dtos.QrCodeDto;
import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.ports.output.QrCodeGeneratorPort;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.SecurityPort;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.ForbiddenException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.User;
import com.eventos.domain.policies.AttendanceDecision;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Casos de uso de frequência. O que varia por atividade (como interpretar uma leitura de QR Code
 * e como calcular a situação) é decidido pela {@code AttendancePolicy} da própria atividade.
 * Os métodos que gravam são serializados para que leituras repetidas não dupliquem registros.
 */
public class AttendanceServiceImpl implements AttendanceUseCase {
    private static final Logger log = LoggerFactory.getLogger(AttendanceServiceImpl.class);
    private static final int QR_IMAGE_SIZE = 320;

    private final AttendanceRepository attendanceRepository;
    private final ActivityRepository activityRepository;
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final SecurityPort securityPort;
    private final QrCodeGeneratorPort qrCodeGenerator;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                                 ActivityRepository activityRepository,
                                 EventRepository eventRepository,
                                 RegistrationRepository registrationRepository,
                                 UserRepository userRepository,
                                 SecurityPort securityPort,
                                 QrCodeGeneratorPort qrCodeGenerator) {
        this.attendanceRepository = attendanceRepository;
        this.activityRepository = activityRepository;
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.securityPort = securityPort;
        this.qrCodeGenerator = qrCodeGenerator;
    }

    @Override
    public QrCodeDto generateQrCode(Long activityId) {
        Activity activity = findActivity(activityId);
        String token = securityPort.generateQrToken(activity.getId());
        return new QrCodeDto(activity.getId(), token, qrCodeGenerator.generatePng(token, QR_IMAGE_SIZE),
                securityPort.qrValidity().toSeconds());
    }

    @Override
    public synchronized AttendanceResult recordQrAttendance(String qrToken, Long userId) {
        Long activityId = securityPort.validateQrToken(qrToken);
        if (activityId == null) {
            log.warn("Leitura de QR Code recusada: token inválido ou expirado (usuário {}).", userId);
            throw new BusinessRuleException("Token de QR Code inválido ou expirado.");
        }
        Activity activity = findActivity(activityId);
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Participante não encontrado."));

        Optional<Registration> registration = registrationRepository.findByEventAndUser(activity.getEventId(), userId);
        if (registration.isEmpty() || !registration.get().isConfirmed()) {
            throw new BusinessRuleException("Você precisa estar inscrito no evento para registrar frequência nesta atividade.");
        }
        if (!findEvent(activity).admits(registration.get(), activity)) {
            throw new BusinessRuleException("Você precisa estar inscrito nesta atividade para registrar frequência.");
        }

        List<AttendanceRecord> existing = attendanceRepository.findByActivityAndUser(activityId, userId);
        LocalDateTime now = eventNow(activity);
        AttendanceDecision decision = activity.decideQrMark(existing, now);
        switch (decision.getKind()) {
            case REJECTED:
                throw new BusinessRuleException(decision.getReason());
            case ALREADY_RECORDED:
                return new AttendanceResult(decision.getExisting(), true, activity.evaluateAttendance(existing));
            default:
                AttendanceRecord saved = attendanceRepository.save(new AttendanceRecord(null, activityId, userId, now,
                        decision.getTypeToRecord(), AttendanceRecord.ORIGIN_QR, "Leitura de QR Code validada"));
                List<AttendanceRecord> updated = new ArrayList<>(existing);
                updated.add(saved);
                return new AttendanceResult(saved, false, activity.evaluateAttendance(updated));
        }
    }

    @Override
    public synchronized AttendanceRecord recordManualAttendance(Long activityId, Long userId, Long organizerId,
                                                                AttendanceType type, String notes) {
        Activity activity = findActivity(activityId);
        User organizer = requireStaff(organizerId);
        requireJustification(notes);
        AttendanceType markType = type != null ? type : AttendanceType.MANUAL_ENTRY;
        if (markType == AttendanceType.REVOCATION) {
            throw new ValidationException("Para corrigir uma presença use a operação de revogação.");
        }
        requireParticipantEnrolled(activity, userId);
        AttendanceRecord record = new AttendanceRecord(null, activityId, userId, eventNow(activity), markType,
                AttendanceRecord.manualOrigin(organizer.getId()), notes.trim());
        AttendanceRecord saved = attendanceRepository.save(record);
        log.info("Presença manual ({}) registrada na atividade {} para o usuário {} pelo responsável {}.",
                markType, activityId, userId, organizerId);
        return saved;
    }

    @Override
    public synchronized AttendanceRecord revokeAttendance(Long activityId, Long userId, Long organizerId, String notes) {
        Activity activity = findActivity(activityId);
        User organizer = requireStaff(organizerId);
        requireJustification(notes);
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Participante não encontrado."));
        List<AttendanceRecord> existing = attendanceRepository.findByActivityAndUser(activityId, userId);
        if (AttendanceRecord.effective(existing).isEmpty()) {
            throw new BusinessRuleException("Não há presença vigente deste participante para revogar nesta atividade.");
        }
        AttendanceRecord saved = attendanceRepository.save(new AttendanceRecord(null, activityId, userId, eventNow(activity),
                AttendanceType.REVOCATION, AttendanceRecord.manualOrigin(organizer.getId()), notes.trim()));
        log.info("Presença do usuário {} na atividade {} revogada pelo responsável {}.", userId, activityId, organizerId);
        return saved;
    }

    @Override
    public List<AttendanceRecord> getActivityRecords(Long activityId) {
        return attendanceRepository.findByActivityId(activityId);
    }

    @Override
    public AttendanceStatus evaluateParticipantAttendance(Long activityId, Long userId) {
        Activity activity = findActivity(activityId);
        return activity.evaluateAttendance(attendanceRepository.findByActivityAndUser(activityId, userId));
    }

    @Override
    public List<AttendanceStatusDto> getActivityAttendanceOverview(Long activityId) {
        Activity activity = findActivity(activityId);
        Event event = findEvent(activity);
        List<Registration> eligible = registrationRepository.findByEventId(activity.getEventId()).stream()
                .filter(reg -> event.admits(reg, activity))
                .toList();
        Map<Long, User> users = new HashMap<>();
        userRepository.findByIds(eligible.stream().map(Registration::getUserId).toList())
                .forEach(u -> users.put(u.getId(), u));
        Map<Long, List<AttendanceRecord>> recordsByUser = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByActivityId(activityId)) {
            recordsByUser.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
        }

        List<AttendanceStatusDto> overview = new ArrayList<>();
        for (Registration reg : eligible) {
            User user = users.get(reg.getUserId());
            if (user == null) continue;
            List<AttendanceRecord> records = recordsByUser.getOrDefault(user.getId(), List.of());
            AttendanceRecord last = records.stream()
                    .max(Comparator.comparing(AttendanceRecord::getTimestamp)
                            .thenComparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId()))
                    .orElse(null);
            overview.add(new AttendanceStatusDto(user.getId(), user.getName(), user.getEmail().getValue(),
                    activity.getId(), activity.getTitle(), activity.evaluateAttendance(records),
                    last == null ? null : last.getTimestamp(), last == null ? "-" : last.getRecordedBy()));
        }
        return overview;
    }

    private Activity findActivity(Long activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade com ID " + activityId + " não encontrada."));
    }

    private Event findEvent(Activity activity) {
        return eventRepository.findById(activity.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Evento da atividade não encontrado."));
    }

    /** Horário atual no fuso do evento da atividade (RN-19). */
    private LocalDateTime eventNow(Activity activity) {
        return eventRepository.findById(activity.getEventId())
                .map(Event::now)
                .orElseGet(LocalDateTime::now);
    }

    private User requireStaff(Long organizerId) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new EntityNotFoundException("Organizador não encontrado."));
        if (!organizer.isOrganizerOrAdmin()) {
            throw new ForbiddenException("Apenas organizadores ou administradores podem alterar a frequência manualmente.");
        }
        return organizer;
    }

    private void requireJustification(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            throw new BusinessRuleException("Para auditoria, é obrigatório informar o motivo da alteração manual de presença (RN-11, RN-12).");
        }
    }

    private void requireParticipantEnrolled(Activity activity, Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Participante não encontrado."));
        Event event = findEvent(activity);
        boolean enrolled = registrationRepository.findByEventAndUser(activity.getEventId(), userId)
                .map(reg -> event.admits(reg, activity))
                .orElse(false);
        if (!enrolled) {
            throw new BusinessRuleException("O participante não está inscrito nesta atividade.");
        }
    }
}
