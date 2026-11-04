package com.eventos.application.services;

import com.eventos.application.dtos.CertificateEligibilityDto;
import com.eventos.application.dtos.EmailMessage;
import com.eventos.application.ports.output.CertificateDeliveryRepository;
import com.eventos.application.ports.output.EmailDeliveryException;
import com.eventos.application.ports.output.EmailSenderPort;
import com.eventos.domain.model.CertificateDelivery;
import java.time.LocalDateTime;
import com.eventos.application.dtos.SpeakerDeclarationDto;
import com.eventos.domain.model.Speaker;
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
import com.eventos.domain.model.User;
import com.eventos.domain.policies.ParticipationSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateServiceImpl implements CertificateUseCase {
    private static final Logger log = LoggerFactory.getLogger(CertificateServiceImpl.class);

    private final CertificateRepository certificateRepository;
    private final EventRepository eventRepository;
    private final ActivityRepository activityRepository;
    private final AttendanceRepository attendanceRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final PdfGeneratorPort pdfGeneratorPort;
    private final EmailSenderPort emailSender;
    private final CertificateDeliveryRepository deliveryRepository;

    public CertificateServiceImpl(CertificateRepository certificateRepository,
                                  EventRepository eventRepository,
                                  ActivityRepository activityRepository,
                                  AttendanceRepository attendanceRepository,
                                  RegistrationRepository registrationRepository,
                                  UserRepository userRepository,
                                  PdfGeneratorPort pdfGeneratorPort,
                                  EmailSenderPort emailSender,
                                  CertificateDeliveryRepository deliveryRepository) {
        this.emailSender = emailSender;
        this.deliveryRepository = deliveryRepository;
        this.certificateRepository = certificateRepository;
        this.eventRepository = eventRepository;
        this.activityRepository = activityRepository;
        this.attendanceRepository = attendanceRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.pdfGeneratorPort = pdfGeneratorPort;
    }

    @Override
    public CertificateEligibilityDto checkEligibility(Long eventId, Long userId) {
        Event event = findEvent(eventId);
        boolean registered = registrationRepository.findByEventAndUser(eventId, userId)
                .map(reg -> reg.isConfirmed()).orElse(false);
        String policy = event.getCertificateEligibilityPolicy().getPolicyDescription();
        if (!registered) {
            return new CertificateEligibilityDto(false, "Você não possui inscrição confirmada neste evento.",
                    policy, 0, 0, 0, 0, false);
        }
        ParticipationSummary summary = summarize(eventId, userId);
        boolean issued = certificateRepository.findByEventAndUser(eventId, userId).isPresent();
        boolean eligible = issued || event.getCertificateEligibilityPolicy().isEligible(summary);
        String justification = eligible
                ? (issued ? "Certificado já emitido." : "Você atende ao critério de elegibilidade do evento.")
                : "Critério não atingido: " + policy + " Você participou de " + summary.getAttendedCount() + "/"
                    + summary.getTotalActivities() + " atividades ("
                    + String.format("%.1f", summary.getHoursAttended()) + "h de "
                    + String.format("%.1f", summary.getTotalHours()) + "h).";
        return new CertificateEligibilityDto(eligible, justification, policy, summary.getAttendedCount(),
                summary.getTotalActivities(), summary.getHoursAttended(), summary.getTotalHours(), issued);
    }

    @Override
    public synchronized Certificate issueCertificateIfEligible(Long eventId, Long userId) {
        Event event = findEvent(eventId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado."));
        registrationRepository.findByEventAndUser(eventId, userId)
                .filter(reg -> reg.isConfirmed())
                .orElseThrow(() -> new BusinessRuleException("Usuário não está inscrito neste evento."));

        Optional<Certificate> existing = certificateRepository.findByEventAndUser(eventId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        ParticipationSummary summary = summarize(eventId, userId);
        CertificateEligibilityDto eligibility = checkEligibility(eventId, userId);
        if (!eligibility.eligible()) {
            throw new BusinessRuleException("Participante não atingiu os critérios de elegibilidade para certificação. "
                    + eligibility.justification());
        }
        Certificate saved = certificateRepository.save(new Certificate(null, eventId, userId, user.getName(),
                event.getTitle(), summary.getHoursAttended(), LocalDate.now(), null));
        log.info("Certificado {} emitido para o usuário {} no evento {}.", saved.getId(), userId, eventId);
        return saved;
    }

    @Override
    public Certificate getCertificateById(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Certificado não encontrado."));
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
        return pdfGeneratorPort.generateCertificatePdf(getCertificateById(certificateId));
    }

    @Override
    public synchronized CertificateDelivery sendCertificateByEmail(Long certificateId) {
        Certificate cert = getCertificateById(certificateId);
        User owner = userRepository.findById(cert.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Titular do certificado não encontrado."));
        String recipient = owner.getEmail().getValue();
        LocalDateTime now = LocalDateTime.now();

        // Clique repetido logo após um envio bem-sucedido não manda o mesmo e-mail duas vezes (RNF-10).
        Optional<CertificateDelivery> recent = deliveryRepository.findByCertificateId(certificateId).stream()
                .filter(d -> d.isSentWithin(CertificateDelivery.DUPLICATE_WINDOW, now))
                .findFirst();
        if (recent.isPresent()) {
            return recent.get();
        }

        CertificateDelivery attempt;
        try {
            emailSender.send(new EmailMessage(recipient,
                    "Seu certificado - " + cert.getEventTitle(),
                    "Olá, " + cert.getParticipantName() + "!\n\n"
                            + "Segue em anexo o seu certificado de participação no evento \"" + cert.getEventTitle() + "\".\n"
                            + "Código de verificação: " + cert.getVerificationCode() + "\n\n"
                            + "Você também pode baixá-lo a qualquer momento pelo site do evento.",
                    "certificado_" + cert.getVerificationCode() + ".pdf",
                    pdfGeneratorPort.generateCertificatePdf(cert), "application/pdf"));
            attempt = CertificateDelivery.sent(certificateId, recipient, now);
            log.info("Certificado {} enviado por e-mail ao titular {}.", certificateId, cert.getUserId());
        } catch (EmailDeliveryException e) {
            attempt = CertificateDelivery.failed(certificateId, recipient, e.getMessage(), now);
            log.warn("Envio do certificado {} falhou: {}", certificateId, e.getMessage());
        }
        return deliveryRepository.save(attempt);
    }

    @Override
    public List<CertificateDelivery> getDeliveries(Long certificateId) {
        getCertificateById(certificateId);
        return deliveryRepository.findByCertificateId(certificateId);
    }

    @Override
    public byte[] exportSpeakerDeclarationPdf(Long activityId, Long speakerId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        Speaker speaker = activity.getSpeakers().stream()
                .filter(s -> s.getId() != null && s.getId().equals(speakerId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Esta atividade não possui essa pessoa vinculada."));
        Event event = findEvent(activity.getEventId());
        return pdfGeneratorPort.generateSpeakerDeclarationPdf(new SpeakerDeclarationDto(speaker.getName(),
                speaker.getRoleInActivity(), activity.getTitle(), event.getTitle(), activity.getPeriod().getStart(),
                activity.getPeriod().getEnd(), activity.getPeriod().getDurationHours(), LocalDate.now()));
    }

    /** Presença validada por atividade, calculada pela política de cada atividade. */
    private ParticipationSummary summarize(Long eventId, Long userId) {
        List<Activity> activities = activityRepository.findByEventId(eventId);
        Map<Long, List<AttendanceRecord>> recordsByActivity = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByUserAndEvent(userId, eventId)) {
            recordsByActivity.computeIfAbsent(r.getActivityId(), k -> new ArrayList<>()).add(r);
        }
        Set<Long> attended = new HashSet<>();
        double hoursAttended = 0.0;
        double totalHours = 0.0;
        for (Activity act : activities) {
            double hours = act.getPeriod().getDurationHours();
            totalHours += hours;
            AttendanceStatus status = act.evaluateAttendance(recordsByActivity.getOrDefault(act.getId(), List.of()));
            if (status == AttendanceStatus.PRESENT) {
                attended.add(act.getId());
                hoursAttended += hours;
            }
        }
        return new ParticipationSummary(attended, activities.size(), hoursAttended, totalHours);
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento não encontrado."));
    }
}
