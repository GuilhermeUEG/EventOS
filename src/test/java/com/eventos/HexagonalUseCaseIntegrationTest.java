package com.eventos;

import com.eventos.adapters.output.pdf.OpenPdfGeneratorAdapter;
import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.JdbcActivityRepository;
import com.eventos.adapters.output.persistence.JdbcAttendanceRepository;
import com.eventos.adapters.output.persistence.JdbcCertificateRepository;
import com.eventos.adapters.output.persistence.JdbcEventRepository;
import com.eventos.adapters.output.persistence.JdbcRegistrationRepository;
import com.eventos.adapters.output.persistence.JdbcSurveyRepository;
import com.eventos.adapters.output.persistence.JdbcUserRepository;
import com.eventos.adapters.output.security.Sha256SecurityAdapter;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.input.CertificateUseCase;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.RegistrationUseCase;
import com.eventos.application.ports.input.ReportUseCase;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.application.services.AttendanceServiceImpl;
import com.eventos.application.services.AuthServiceImpl;
import com.eventos.application.services.CertificateServiceImpl;
import com.eventos.application.services.EventServiceImpl;
import com.eventos.application.services.RegistrationServiceImpl;
import com.eventos.application.services.ReportServiceImpl;
import com.eventos.application.services.SurveyServiceImpl;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Integração Hexagonal (Casos de Uso Ponta a Ponta CA-01 a CA-08)")
public class HexagonalUseCaseIntegrationTest {

    private static AuthUseCase authUseCase;
    private static EventUseCase eventUseCase;
    private static RegistrationUseCase registrationUseCase;
    private static AttendanceUseCase attendanceUseCase;
    private static SurveyUseCase surveyUseCase;
    private static CertificateUseCase certificateUseCase;
    private static ReportUseCase reportUseCase;

    @BeforeAll
    static void setup() {
        DatabaseManager.initializeDatabase();

        JdbcUserRepository userRepo = new JdbcUserRepository();
        JdbcEventRepository eventRepo = new JdbcEventRepository();
        JdbcActivityRepository actRepo = new JdbcActivityRepository();
        JdbcRegistrationRepository regRepo = new JdbcRegistrationRepository();
        JdbcAttendanceRepository attRepo = new JdbcAttendanceRepository();
        JdbcSurveyRepository surveyRepo = new JdbcSurveyRepository();
        JdbcCertificateRepository certRepo = new JdbcCertificateRepository();

        Sha256SecurityAdapter sec = new Sha256SecurityAdapter();
        OpenPdfGeneratorAdapter pdf = new OpenPdfGeneratorAdapter();

        authUseCase = new AuthServiceImpl(userRepo, sec);
        eventUseCase = new EventServiceImpl(eventRepo, actRepo);
        registrationUseCase = new RegistrationServiceImpl(regRepo, eventRepo, actRepo, userRepo);
        attendanceUseCase = new AttendanceServiceImpl(attRepo, actRepo, regRepo, userRepo, sec);
        surveyUseCase = new SurveyServiceImpl(surveyRepo, actRepo, regRepo, attRepo, userRepo);
        certificateUseCase = new CertificateServiceImpl(certRepo, eventRepo, actRepo, attRepo, regRepo, userRepo, pdf);
        reportUseCase = new ReportServiceImpl(eventRepo, regRepo, actRepo, attRepo, userRepo, pdf);
    }

    @Test
    @DisplayName("Fluxo Completo Integrado: Cadastro, Inscrição, QR Frequência, Avaliação, Relatório e Certificado")
    void testCompleteEndToEndScenario() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 5);

        // 1. Cadastro de Usuários
        User org = authUseCase.register("Org " + uniqueSuffix, "org_" + uniqueSuffix + "@test.com", "pass123", UserRole.ORGANIZER);
        User part = authUseCase.register("Participante " + uniqueSuffix, "part_" + uniqueSuffix + "@test.com", "pass123", UserRole.PARTICIPANT);
        assertNotNull(org.getId());
        assertNotNull(part.getId());

        // 2. Criação e Publicação do Evento (CA-01)
        LocalDateTime now = LocalDateTime.now();
        Event event = eventUseCase.createEvent(new Event(null, "Semana de POO " + uniqueSuffix, "Desc",
                new Period(now.plusDays(1), now.plusDays(2)), EventStatus.DRAFT, org.getId(), null, 50, new MinimumAttendancePercentagePolicy(50.0)));

        Activity act = eventUseCase.addActivityToEvent(event.getId(), new Activity(null, event.getId(), "Palestra de Arquitetura", "Desc",
                new Period(now.plusDays(1).plusHours(1), now.plusDays(1).plusHours(3)),
                new ActivityLocation("Auditório Central", "TI", 50), ActivityType.LECTURE, 50, 0, null, new SingleCheckInPolicy(), true));

        eventUseCase.publishEvent(event.getId());
        Event published = eventUseCase.getEvent(event.getId());
        assertEquals(EventStatus.PUBLISHED, published.getStatus());

        // 3. Inscrição do Participante (CA-02, CA-03)
        Registration reg = registrationUseCase.registerForEvent(event.getId(), part.getId(), Set.of(act.getId()));
        assertTrue(reg.isConfirmed());

        List<Activity> agenda = registrationUseCase.getParticipantAgenda(part.getId(), event.getId());
        assertEquals(1, agenda.size());
        assertEquals("Palestra de Arquitetura", agenda.get(0).getTitle());

        // 4. Registro de Frequência por QR Code (CA-04)
        String qrToken = attendanceUseCase.generateQrToken(act.getId());
        assertNotNull(qrToken);

        var attRecord = attendanceUseCase.recordQrAttendance(qrToken, part.getId());
        assertNotNull(attRecord.getId());

        // 5. Configuração e Envio de Avaliação (CA-06)
        FeedbackSurvey survey = surveyUseCase.createSurvey(new FeedbackSurvey(null, act.getId(), "Pesquisa de Satisfação",
                List.of(new SurveyQuestion(null, "Nota geral?", QuestionType.RATING_SCALE, List.of(), true)), true));

        assertTrue(surveyUseCase.canUserEvaluate(part.getId(), act.getId()));

        SurveyResponse resp = surveyUseCase.submitResponse(new SurveyResponse(null, survey.getId(), act.getId(), part.getId(),
                false, Map.of(survey.getQuestions().get(0).getId(), "5"), LocalDateTime.now()));
        assertNotNull(resp.getId());

        var metrics = surveyUseCase.getConsolidatedResults(act.getId());
        assertEquals(1, metrics.getTotalResponses());
        assertEquals(5.0, metrics.getAverageRating());

        // 6. Relatório Operacional (CA-07)
        EnrolledReportDto rep = reportUseCase.getEnrolledReport(event.getId());
        assertEquals(1, rep.getTotalEnrolled());

        byte[] pdfBytes = reportUseCase.exportEnrolledPdf(event.getId());
        assertTrue(pdfBytes.length > 0, "PDF deve ser gerado");

        // 7. Emissão de Certificado com Código Verificável (CA-08)
        Certificate cert = certificateUseCase.issueCertificateIfEligible(event.getId(), part.getId());
        assertNotNull(cert.getVerificationCode());

        Certificate verified = certificateUseCase.getCertificateByVerificationCode(cert.getVerificationCode());
        assertEquals(part.getName(), verified.getParticipantName());

        byte[] certPdf = certificateUseCase.exportCertificatePdf(cert.getId());
        assertTrue(certPdf.length > 0, "PDF do certificado deve ser gerado");
    }
}
