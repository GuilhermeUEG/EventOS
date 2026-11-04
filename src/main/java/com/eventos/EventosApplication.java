package com.eventos;

import com.eventos.adapters.input.desktop.DesktopLauncher;
import com.eventos.adapters.input.rest.ApiJson;
import com.eventos.adapters.input.rest.DemoController;
import com.eventos.adapters.input.rest.RestApiController;
import com.eventos.adapters.output.email.SmtpEmailAdapter;
import com.eventos.adapters.output.email.SmtpSettings;
import com.eventos.adapters.output.email.UnconfiguredEmailAdapter;
import com.eventos.adapters.output.pdf.OpenPdfGeneratorAdapter;
import com.eventos.adapters.output.persistence.JdbcCertificateDeliveryRepository;
import com.eventos.application.ports.output.EmailSenderPort;
import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.DatabaseSeeder;
import com.eventos.adapters.output.persistence.JdbcActivityRepository;
import com.eventos.adapters.output.persistence.JdbcAttendanceRepository;
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
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition root da plataforma EventOS: único lugar que conhece as implementações concretas.
 * Cria adaptadores de saída, casos de uso e adaptadores de entrada e os conecta por portas, sem
 * framework de injeção (arquitetura hexagonal, sem Spring).
 *
 * <p>Argumentos: {@code --seed-large} cria a base de carga (500 participantes e 100 atividades);
 * {@code --no-desktop} sobe somente API e site. Variáveis: {@code EVENTOS_PORT},
 * {@code EVENTOS_DEMO_MODE} (padrão ligado), {@code EVENTOS_DB_URL}, {@code EVENTOS_QR_SECRET},
 * {@code EVENTOS_PUBLIC_URL} (endereço público para os links dos PDFs), {@code EVENTOS_SMTP_*} (envio de e-mail) e
 * {@code EVENTOS_ADMIN_EMAIL}/{@code EVENTOS_ADMIN_PASSWORD}/{@code EVENTOS_ADMIN_NAME} (cria o primeiro administrador).</p>
 */
public class EventosApplication {
    private static final Logger log = LoggerFactory.getLogger(EventosApplication.class);

    public static void main(String[] args) {
        boolean seedLarge = Arrays.asList(args).contains("--seed-large")
                || "true".equalsIgnoreCase(System.getenv("EVENTOS_SEED_LARGE"));
        boolean noDesktop = Arrays.asList(args).contains("--no-desktop");
        boolean demoMode = !"false".equalsIgnoreCase(System.getenv("EVENTOS_DEMO_MODE"));
        int port = resolvePort();

        log.info("EventOS - Plataforma de Gestão de Eventos (POO II)");

        // 1. Banco relacional H2: cria ou evolui o esquema por scripts versionados.
        DatabaseManager.initializeDatabase();

        // 2. Adaptadores de saída.
        JdbcUserRepository userRepository = new JdbcUserRepository();
        JdbcEventRepository eventRepository = new JdbcEventRepository();
        JdbcActivityRepository activityRepository = new JdbcActivityRepository();
        JdbcRegistrationRepository registrationRepository = new JdbcRegistrationRepository();
        JdbcAttendanceRepository attendanceRepository = new JdbcAttendanceRepository();
        JdbcSurveyRepository surveyRepository = new JdbcSurveyRepository();
        JdbcCertificateRepository certificateRepository = new JdbcCertificateRepository();
        Pbkdf2SecurityAdapter securityAdapter = new Pbkdf2SecurityAdapter();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        // Endereço público usado nos PDFs para validar certificados. Em servidor, defina EVENTOS_PUBLIC_URL.
        String publicUrl = resolvePublicUrl(port);
        OpenPdfGeneratorAdapter pdfGenerator = new OpenPdfGeneratorAdapter(publicUrl);
        ZxingQrCodeAdapter qrCodeGenerator = new ZxingQrCodeAdapter();
        JdbcCertificateDeliveryRepository deliveryRepository = new JdbcCertificateDeliveryRepository();
        EmailSenderPort emailSender = SmtpSettings.fromEnvironment()
                .<EmailSenderPort>map(SmtpEmailAdapter::new)
                .orElseGet(UnconfiguredEmailAdapter::new);
        log.info("Envio de e-mail de certificados: {}", emailSender instanceof SmtpEmailAdapter ? "SMTP configurado" : "não configurado (falhas serão registradas)");

        // 3. Dados de demonstração.
        if (demoMode) {
            DatabaseSeeder.seedDemoData(userRepository, eventRepository, activityRepository,
                    registrationRepository, attendanceRepository, surveyRepository);
        }
        String adminEmail = System.getenv("EVENTOS_ADMIN_EMAIL");
        String adminPassword = System.getenv("EVENTOS_ADMIN_PASSWORD");
        if (adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
            DatabaseSeeder.ensureAdministrator(userRepository, System.getenv("EVENTOS_ADMIN_NAME"), adminEmail, adminPassword);
        }
        if (seedLarge) {
            DatabaseSeeder.seedLargeDataset(userRepository, eventRepository, activityRepository,
                    registrationRepository, attendanceRepository);
        }

        // 4. Casos de uso (portas de entrada) ligados às portas de saída.
        AuthUseCase authUseCase = new AuthServiceImpl(userRepository, securityAdapter);
        EventUseCase eventUseCase = new EventServiceImpl(eventRepository, activityRepository);
        RegistrationUseCase registrationUseCase = new RegistrationServiceImpl(
                registrationRepository, eventRepository, activityRepository, userRepository);
        AttendanceUseCase attendanceUseCase = new AttendanceServiceImpl(attendanceRepository, activityRepository,
                eventRepository, registrationRepository, userRepository, securityAdapter, qrCodeGenerator);
        SurveyUseCase surveyUseCase = new SurveyServiceImpl(surveyRepository, activityRepository,
                registrationRepository, attendanceRepository, eventRepository);
        CertificateUseCase certificateUseCase = new CertificateServiceImpl(certificateRepository, eventRepository,
                activityRepository, attendanceRepository, registrationRepository, userRepository, pdfGenerator,
                emailSender, deliveryRepository);
        ReportUseCase reportUseCase = new ReportServiceImpl(eventRepository, registrationRepository,
                activityRepository, attendanceRepository, userRepository, pdfGenerator);

        // 5. Adaptador de entrada: API REST (Javalin) e site público estático.
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(ApiJson.javalinMapper());
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        }).start(port);

        new RestApiController(authUseCase, eventUseCase, registrationUseCase, attendanceUseCase,
                surveyUseCase, certificateUseCase, reportUseCase, sessionStore, app);
        if (demoMode) {
            new DemoController(DatabaseSeeder.DEMO_ACCOUNTS.stream()
                    .map(a -> new DemoController.Account(a.name(), a.email(), a.password(), a.role().name()))
                    .toList()).register(app);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        log.info("API REST e site público disponíveis em http://localhost:{}", port);

        // 6. Adaptador de entrada: desktop Swing, cliente da mesma API (somente com ambiente gráfico).
        if (!noDesktop && !GraphicsEnvironment.isHeadless()) {
            DesktopLauncher.launch("http://localhost:" + port);
        } else {
            log.info("Interface desktop não iniciada (modo headless ou --no-desktop): somente API e site.");
        }
    }

    private static String resolvePublicUrl(int port) {
        String configured = System.getenv("EVENTOS_PUBLIC_URL");
        if (configured == null || configured.isBlank()) return "http://localhost:" + port;
        return configured.trim().replaceAll("/+$", "");
    }

    private static int resolvePort() {
        String configured = System.getenv("EVENTOS_PORT");
        if (configured == null || configured.isBlank()) return 7000;
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            log.warn("EVENTOS_PORT inválida ({}); usando 7000.", configured);
            return 7000;
        }
    }
}
