package com.eventos.application.services;

import com.eventos.application.ports.input.CertificateUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.application.ports.output.CertificateRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.ports.output.PdfGeneratorPort;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CertificateServiceImpl implements CertificateUseCase {
    private final CertificateRepository certificateRepository;
    private final EventRepository eventRepository;
    private final ActivityRepository activityRepository;
    private final AttendanceRepository attendanceRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final PdfGeneratorPort pdfGeneratorPort;

    public CertificateServiceImpl(CertificateRepository certificateRepository,
                                  EventRepository eventRepository,
                                  ActivityRepository activityRepository,
                                  AttendanceRepository attendanceRepository,
                                  RegistrationRepository registrationRepository,
                                  UserRepository userRepository,
                                  PdfGeneratorPort pdfGeneratorPort) {
        this.certificateRepository = certificateRepository;
        this.eventRepository = eventRepository;
        this.activityRepository = activityRepository;
        this.attendanceRepository = attendanceRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.pdfGeneratorPort = pdfGeneratorPort;
    }

    @Override
    public Certificate issueCertificateIfEligible(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento não encontrado."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado."));

        Optional<Registration> reg = registrationRepository.findByEventAndUser(eventId, userId);
        if (reg.isEmpty() || !reg.get().isConfirmed()) {
            throw new BusinessRuleException("Usuário não está inscrito neste evento.");
        }

        // Já possui certificado emitido?
        Optional<Certificate> existing = certificateRepository.findByEventAndUser(eventId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<Activity> eventActivities = activityRepository.findByEventId(eventId);
        int totalActivitiesAttended = 0;
        double totalHoursAttended = 0.0;
        double totalEventHours = event.calculateTotalHours();

        for (Activity act : eventActivities) {
            List<AttendanceRecord> records = attendanceRepository.findByActivityAndUser(act.getId(), userId);
            AttendanceStatus status = act.evaluateAttendance(records);
            if (status == AttendanceStatus.PRESENT) {
                totalActivitiesAttended++;
                totalHoursAttended += act.getPeriod().getDurationHours();
            }
        }

        boolean eligible = event.getCertificateEligibilityPolicy().isEligible(
                totalActivitiesAttended, eventActivities.size(), totalHoursAttended, totalEventHours);

        if (!eligible) {
            throw new BusinessRuleException("Participante não atingiu os critérios de elegibilidade para certificação: "
                    + event.getCertificateEligibilityPolicy().getPolicyDescription() +
                    " (Participou de " + totalActivitiesAttended + "/" + eventActivities.size() + " atividades, " +
                    String.format("%.1f", totalHoursAttended) + "h/" + String.format("%.1f", totalEventHours) + "h).");
        }

        Certificate cert = new Certificate(null, eventId, userId, user.getName(), event.getTitle(),
                totalHoursAttended > 0 ? totalHoursAttended : 2.0, LocalDate.now(), null);
        return certificateRepository.save(cert);
    }

    @Override
    public Certificate getCertificateByVerificationCode(String code) {
        return certificateRepository.findByVerificationCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Certificado com código '" + code + "' não foi encontrado."));
    }

    @Override
    public List<Certificate> getUserCertificates(Long userId) {
        return certificateRepository.findByUserId(userId);
    }

    @Override
    public byte[] exportCertificatePdf(Long certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new EntityNotFoundException("Certificado não encontrado."));
        return pdfGeneratorPort.generateCertificatePdf(cert);
    }
}
