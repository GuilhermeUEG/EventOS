package com.eventos;

import com.eventos.adapters.input.desktop.SwingDesktopApp;
import com.eventos.adapters.input.desktop.EventOsApiClient;
import com.eventos.adapters.input.rest.RestApiController;
import com.eventos.adapters.output.pdf.OpenPdfGeneratorAdapter;
import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.DatabaseSeeder;
import com.eventos.adapters.output.persistence.JdbcActivityRepository;
import com.eventos.adapters.output.persistence.JdbcAttendanceRepository;
import com.eventos.adapters.output.persistence.JdbcCertificateRepository;
import com.eventos.adapters.output.persistence.JdbcEventRepository;
import com.eventos.adapters.output.persistence.JdbcRegistrationRepository;
import com.eventos.adapters.output.persistence.JdbcSurveyRepository;
import com.eventos.adapters.output.persistence.JdbcUserRepository;
import com.eventos.adapters.output.security.Pbkdf2SecurityAdapter;
import com.eventos.adapters.output.security.InMemorySessionStore;
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
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Ponto de Entrada Principal da Plataforma EventOS.
 * Realiza a injeção manual de dependências (Arquitetura Hexagonal pura sem Spring Boot).
 */
public class EventosApplication {

    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("   EventOS - Plataforma de Gestão de Eventos (POO II 2026)");
        System.out.println("========================================================================");

        // 1. Inicializa Banco de Dados Relacional H2 (RNF-04)
        DatabaseManager.initializeDatabase();

        // 2. Instancia Repositórios e Adaptadores de Saída (JDBC Puro, Segurança SHA-256 e OpenPDF)
        JdbcUserRepository userRepository = new JdbcUserRepository();
        JdbcEventRepository eventRepository = new JdbcEventRepository();
        JdbcActivityRepository activityRepository = new JdbcActivityRepository();
        JdbcRegistrationRepository registrationRepository = new JdbcRegistrationRepository();
        JdbcAttendanceRepository attendanceRepository = new JdbcAttendanceRepository();
        JdbcSurveyRepository surveyRepository = new JdbcSurveyRepository();
        JdbcCertificateRepository certificateRepository = new JdbcCertificateRepository();

        Pbkdf2SecurityAdapter securityAdapter = new Pbkdf2SecurityAdapter();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        OpenPdfGeneratorAdapter pdfGeneratorAdapter = new OpenPdfGeneratorAdapter();

        // 3. Povoamento de Dados de Demonstração (Seed Demo Data para CA-01 a CA-08)
        DatabaseSeeder.seedDemoData(userRepository, eventRepository, activityRepository, registrationRepository, attendanceRepository, surveyRepository);

        // 4. Instancia Casos de Uso / Serviços de Aplicação (Ports & Adapters)
        AuthUseCase authUseCase = new AuthServiceImpl(userRepository, securityAdapter);
        EventUseCase eventUseCase = new EventServiceImpl(eventRepository, activityRepository);
        RegistrationUseCase registrationUseCase = new RegistrationServiceImpl(registrationRepository, eventRepository, activityRepository, userRepository);
        AttendanceUseCase attendanceUseCase = new AttendanceServiceImpl(attendanceRepository, activityRepository, registrationRepository, userRepository, securityAdapter);
        SurveyUseCase surveyUseCase = new SurveyServiceImpl(surveyRepository, activityRepository, registrationRepository, attendanceRepository, userRepository);
        CertificateUseCase certificateUseCase = new CertificateServiceImpl(certificateRepository, eventRepository, activityRepository, attendanceRepository, registrationRepository, userRepository, pdfGeneratorAdapter);
        ReportUseCase reportUseCase = new ReportServiceImpl(eventRepository, registrationRepository, activityRepository, attendanceRepository, userRepository, pdfGeneratorAdapter);

        // 5. Configuração do Servidor Web e API REST (Javalin 6.x)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, true));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        }).start(7000);

        // 6. Roteamento REST da API (RNF-02)
        new RestApiController(authUseCase, eventUseCase, registrationUseCase, attendanceUseCase,
                surveyUseCase, certificateUseCase, reportUseCase, sessionStore, app);

        System.out.println("✔ Servidor REST e Site Público disponíveis em: http://localhost:7000");

        // 7. Inicializa Aplicação Desktop (Java Swing) se houver ambiente gráfico disponível
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
                EventOsApiClient apiClient = new EventOsApiClient("http://localhost:7000");
                apiClient.login("marcio.giovane@universidade.edu.br", "marcio123");
                SwingDesktopApp desktopApp = new SwingDesktopApp(apiClient);
                desktopApp.setVisible(true);
            });
        } else {
            System.out.println("ℹ Ambiente Headless detectado. Executando em modo exclusivo de API/Web.");
        }
    }
}
