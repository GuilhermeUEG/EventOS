package com.eventos.support;

import com.eventos.adapters.input.rest.ApiJson;
import com.eventos.adapters.input.rest.RestApiController;
import com.eventos.adapters.output.pdf.OpenPdfGeneratorAdapter;
import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.JdbcActivityRepository;
import com.eventos.adapters.output.persistence.JdbcAttendanceRepository;
import com.eventos.adapters.output.persistence.JdbcCertificateDeliveryRepository;
import com.eventos.adapters.output.persistence.JdbcCertificateRepository;
import com.eventos.adapters.output.persistence.JdbcEventRepository;
import com.eventos.adapters.output.persistence.JdbcRegistrationRepository;
import com.eventos.adapters.output.persistence.JdbcSurveyRepository;
import com.eventos.adapters.output.persistence.JdbcUserRepository;
import com.eventos.adapters.output.qr.ZxingQrCodeAdapter;
import com.eventos.adapters.output.security.InMemorySessionStore;
import com.eventos.adapters.output.security.Pbkdf2SecurityAdapter;
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
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import io.javalin.Javalin;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aplicação completa para testes: casos de uso reais, banco H2 em memória (configurado pelo Gradle) e
 * API REST em porta aleatória. Cada teste cria usuários com e-mails únicos, sem depender de ordem.
 */
public final class TestApp implements AutoCloseable {
    public static final String DEFAULT_PASSWORD = "senha123";

    public final JdbcUserRepository users = new JdbcUserRepository();
    public final JdbcEventRepository events = new JdbcEventRepository();
    public final JdbcActivityRepository activities = new JdbcActivityRepository();
    public final JdbcRegistrationRepository registrations = new JdbcRegistrationRepository();
    public final JdbcAttendanceRepository attendance = new JdbcAttendanceRepository();
    public final JdbcSurveyRepository surveys = new JdbcSurveyRepository();
    public final JdbcCertificateRepository certificates = new JdbcCertificateRepository();
    public final Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter();
    public final JdbcCertificateDeliveryRepository deliveries = new JdbcCertificateDeliveryRepository();
    /** E-mails "enviados" nos testes ficam aqui; defina {@link RecordingEmailSender#failWith} para simular falha. */
    public final RecordingEmailSender emailSender = new RecordingEmailSender();

    public final AuthUseCase auth;
    public final EventUseCase eventUseCase;
    public final RegistrationUseCase registrationUseCase;
    public final AttendanceUseCase attendanceUseCase;
    public final SurveyUseCase surveyUseCase;
    public final CertificateUseCase certificateUseCase;
    public final ReportUseCase reportUseCase;

    public final Javalin javalin;
    public final String baseUrl;

    private TestApp() {
        DatabaseManager.initializeDatabase();
        OpenPdfGeneratorAdapter pdf = new OpenPdfGeneratorAdapter();
        ZxingQrCodeAdapter qr = new ZxingQrCodeAdapter();
        auth = new AuthServiceImpl(users, security);
        eventUseCase = new EventServiceImpl(events, activities);
        registrationUseCase = new RegistrationServiceImpl(registrations, events, activities, users);
        attendanceUseCase = new AttendanceServiceImpl(attendance, activities, events, registrations, users, security, qr);
        surveyUseCase = new SurveyServiceImpl(surveys, activities, registrations, attendance, events);
        certificateUseCase = new CertificateServiceImpl(certificates, events, activities, attendance, registrations, users, pdf,
                emailSender, deliveries);
        reportUseCase = new ReportServiceImpl(events, registrations, activities, attendance, users, pdf);

        javalin = Javalin.create(config -> config.jsonMapper(ApiJson.javalinMapper())).start(0);
        new RestApiController(auth, eventUseCase, registrationUseCase, attendanceUseCase, surveyUseCase,
                certificateUseCase, reportUseCase, new InMemorySessionStore(), javalin);
        baseUrl = "http://localhost:" + javalin.port();
    }

    public static TestApp start() {
        return new TestApp();
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    /** Cria diretamente no repositório um usuário com o perfil indicado (a API pública só cria participantes). */
    public User createUser(String name, UserRole role) {
        String slug = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return users.save(new User(null, name, new Email(uniqueEmail(slug)),
                security.hashPassword(DEFAULT_PASSWORD), role, LocalDateTime.now()));
    }

    @Override
    public void close() {
        javalin.stop();
    }
}
