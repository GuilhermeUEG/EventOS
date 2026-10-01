package com.eventos;

import static org.junit.jupiter.api.Assertions.*;

import com.eventos.adapters.input.desktop.EventOsApiClient;
import com.eventos.adapters.input.rest.RestApiController;
import com.eventos.adapters.output.pdf.OpenPdfGeneratorAdapter;
import com.eventos.adapters.output.persistence.*;
import com.eventos.adapters.output.security.InMemorySessionStore;
import com.eventos.adapters.output.security.Pbkdf2SecurityAdapter;
import com.eventos.application.services.*;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DesktopApiClientIntegrationTest {
    @Test
    void desktopUsesAuthenticatedHttpApi() {
        DatabaseManager.initializeDatabase();
        var users = new JdbcUserRepository();
        var events = new JdbcEventRepository();
        var activities = new JdbcActivityRepository();
        var registrations = new JdbcRegistrationRepository();
        var attendance = new JdbcAttendanceRepository();
        var surveys = new JdbcSurveyRepository();
        var certificates = new JdbcCertificateRepository();
        var security = new Pbkdf2SecurityAdapter();
        var pdf = new OpenPdfGeneratorAdapter();

        var authUseCase = new AuthServiceImpl(users, security);
        var eventUseCase = new EventServiceImpl(events, activities);
        var registrationUseCase = new RegistrationServiceImpl(registrations, events, activities, users);
        var attendanceUseCase = new AttendanceServiceImpl(attendance, activities, registrations, users, security);
        var surveyUseCase = new SurveyServiceImpl(surveys, activities, registrations, attendance, users);
        var certificateUseCase = new CertificateServiceImpl(certificates, events, activities, attendance,
                registrations, users, pdf);
        var reportUseCase = new ReportServiceImpl(events, registrations, activities, attendance, users, pdf);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "desktop_" + suffix + "@test.com";
        authUseCase.register("Organizador Desktop", email, "senha123", UserRole.ORGANIZER);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson(mapper, true))).start(0);
        try {
            new RestApiController(authUseCase, eventUseCase, registrationUseCase, attendanceUseCase,
                    surveyUseCase, certificateUseCase, reportUseCase, new InMemorySessionStore(), app);

            EventOsApiClient client = new EventOsApiClient("http://localhost:" + app.port());
            client.login(email, "senha123");

            LocalDateTime start = LocalDateTime.now().plusDays(5);
            Event event = new Event(null, "Evento Desktop " + suffix, "Criado pela API",
                    new Period(start, start.plusDays(1)), EventStatus.DRAFT, null, null,
                    50, new MinimumAttendancePercentagePolicy(75));
            Event created = client.createEvent(event);

            assertNotNull(created.getId());
            assertTrue(client.listEvents().stream().anyMatch(item -> item.getId().equals(created.getId())));
        } finally {
            app.stop();
        }
    }
}
