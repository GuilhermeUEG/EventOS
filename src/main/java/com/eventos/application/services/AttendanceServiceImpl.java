package com.eventos.application.services;

import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.SecurityPort;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.UnauthorizedException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceServiceImpl implements AttendanceUseCase {
    private final AttendanceRepository attendanceRepository;
    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final SecurityPort securityPort;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                                 ActivityRepository activityRepository,
                                 RegistrationRepository registrationRepository,
                                 UserRepository userRepository,
                                 SecurityPort securityPort) {
        this.attendanceRepository = attendanceRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.securityPort = securityPort;
    }

    @Override
    public String generateQrToken(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade com ID " + activityId + " não encontrada."));
        return securityPort.generateQrToken(activity.getId());
    }

    @Override
    public AttendanceRecord recordQrAttendance(String qrToken, Long userId) {
        Long activityId = securityPort.validateQrToken(qrToken);
        if (activityId == null) {
            throw new BusinessRuleException("Token de QR Code inválido ou expirado.");
        }
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Participante não encontrado."));

        // Valida se usuário está inscrito no evento
        Optional<Registration> regOpt = registrationRepository.findByEventAndUser(activity.getEventId(), userId);
        if (regOpt.isEmpty() || !regOpt.get().isConfirmed()) {
            throw new BusinessRuleException("Você precisa estar inscrito no evento para registrar frequência nesta atividade.");
        }

        List<AttendanceRecord> existing = attendanceRepository.findByActivityAndUser(activityId, userId);
        
        // Determina tipo: se política for Entrada/Saída e já tem CHECK_IN, marca CHECK_OUT
        AttendanceType markType = AttendanceType.CHECK_IN;
        if (activity.getAttendancePolicy() instanceof com.eventos.domain.policies.CheckInCheckOutPolicy) {
            boolean hasIn = existing.stream().anyMatch(r -> r.getType() == AttendanceType.CHECK_IN);
            if (hasIn) {
                markType = AttendanceType.CHECK_OUT;
            }
        }

        AttendanceRecord record = new AttendanceRecord(null, activityId, userId, LocalDateTime.now(),
                markType, "QR_SCAN", "Leitura de QR Code validada");
        return attendanceRepository.save(record);
    }

    @Override
    public AttendanceRecord recordManualAttendance(Long activityId, Long userId, Long organizerId, AttendanceType type, String notes) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new EntityNotFoundException("Organizador não encontrado."));

        if (!organizer.isOrganizerOrAdmin()) {
            throw new UnauthorizedException("Apenas organizadores ou administradores podem efetuar lançamento manual de presença.");
        }

        if (notes == null || notes.trim().isEmpty()) {
            throw new BusinessRuleException("Para auditoria, é obrigatório informar o motivo do lançamento manual de presença (RN-11, RN-12).");
        }

        AttendanceRecord record = new AttendanceRecord(null, activityId, userId, LocalDateTime.now(),
                type != null ? type : AttendanceType.MANUAL_ENTRY,
                "MANUAL_BY_USER_" + organizer.getId(), notes);
        return attendanceRepository.save(record);
    }

    @Override
    public List<AttendanceRecord> getActivityRecords(Long activityId) {
        return attendanceRepository.findByActivityId(activityId);
    }

    @Override
    public AttendanceStatus evaluateParticipantAttendance(Long activityId, Long userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        List<AttendanceRecord> records = attendanceRepository.findByActivityAndUser(activityId, userId);
        return activity.evaluateAttendance(records);
    }

    @Override
    public List<AttendanceStatusDto> getActivityAttendanceOverview(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        List<Registration> eventRegs = registrationRepository.findByEventId(activity.getEventId());
        
        List<AttendanceStatusDto> overview = new ArrayList<>();
        for (Registration reg : eventRegs) {
            if (!reg.isConfirmed()) continue;
            User user = userRepository.findById(reg.getUserId()).orElse(null);
            if (user == null) continue;

            List<AttendanceRecord> records = attendanceRepository.findByActivityAndUser(activityId, user.getId());
            AttendanceStatus status = activity.evaluateAttendance(records);
            LocalDateTime lastTime = records.isEmpty() ? null : records.get(records.size() - 1).getTimestamp();
            String recordedBy = records.isEmpty() ? "-" : records.get(records.size() - 1).getRecordedBy();

            overview.add(new AttendanceStatusDto(user.getId(), user.getName(), user.getEmail().getValue(),
                    activity.getId(), activity.getTitle(), status, lastTime, recordedBy));
        }
        return overview;
    }
}
