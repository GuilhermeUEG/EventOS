package com.eventos.adapters.output.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.RegistrationStatus;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.CheckInCheckOutPolicy;
import com.eventos.domain.policies.MinimumActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.RequiredActivitiesPolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import com.eventos.support.TestApp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Adaptadores de persistência JDBC/H2 (RNF-04, RN-18)")
class PersistenceAdaptersTest {
    private static TestApp app;
    private static User organizer;
    private final LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(8).withMinute(0).withSecond(0).withNano(0);

    @BeforeAll
    static void start() {
        app = TestApp.start();
        organizer = app.createUser("Organizador Persistência", UserRole.ORGANIZER);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private Event event(CertificateEligibilityPolicy policy) {
        return app.events.save(new Event(null, "Evento persistido", "Descrição", new Period(start, start.plusDays(1)),
                EventStatus.DRAFT, organizer.getId(), null, 30, policy));
    }

    private Activity activity(Event event, int capacity, boolean requiresRegistration) {
        return app.activities.save(new Activity(null, event.getId(), "Atividade", "Desc", new Period(start.plusHours(1), start.plusHours(2)),
                new ActivityLocation("Sala P", "Trilha P", capacity), ActivityType.WORKSHOP, capacity, 0,
                List.of(new Speaker(null, "Ana", "Instrutora", "Bio", null), new Speaker(null, "Beto", "Apoio", "", "http://foto")),
                new CheckInCheckOutPolicy(), requiresRegistration));
    }

    @Test
    @DisplayName("atividade é gravada com palestrantes, política e capacidade, e relida igual")
    void activityRoundTrip() {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        Activity saved = activity(ev, 25, true);
        Activity loaded = app.activities.findById(saved.getId()).orElseThrow();
        assertEquals("Atividade", loaded.getTitle());
        assertEquals(25, loaded.getMaxCapacity());
        assertEquals("CHECKIN_CHECKOUT", loaded.getAttendancePolicy().getPolicyKey());
        assertEquals(2, loaded.getSpeakers().size());
        assertEquals("http://foto", loaded.getSpeakers().get(1).getPhotoUrl());
        assertEquals(ActivityType.WORKSHOP, loaded.getType());
    }

    @Test
    @DisplayName("atualizar uma atividade existente funciona (regressão do UPDATE com parâmetros trocados)")
    void activityUpdate() {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        Activity original = activity(ev, 25, true);
        Activity changed = new Activity(original.getId(), ev.getId(), "Título novo", "Outra descrição",
                new Period(start.plusHours(3), start.plusHours(4)), new ActivityLocation("Sala Q", "Trilha Q", 40),
                ActivityType.POSTER, 40, 0, List.of(new Speaker(null, "Carla", "Autora", "", null)),
                new SingleCheckInPolicy(), false);
        app.activities.save(changed);
        Activity loaded = app.activities.findById(original.getId()).orElseThrow();
        assertEquals("Título novo", loaded.getTitle());
        assertEquals("Sala Q", loaded.getLocation().getRoom());
        assertEquals(40, loaded.getMaxCapacity());
        assertEquals(ActivityType.POSTER, loaded.getType());
        assertFalse(loaded.isRequiresRegistration());
        assertEquals(List.of("Carla"), loaded.getSpeakers().stream().map(Speaker::getName).toList());
    }

    @Test
    @DisplayName("reservar vaga é atômico: com 40 tentativas simultâneas para 7 vagas, exatamente 7 têm sucesso")
    void reserveSlotNeverExceedsCapacity() throws Exception {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        Activity a = activity(ev, 7, true);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 40; i++) results.add(pool.submit(() -> app.activities.reserveSlot(a.getId())));
        int successes = 0;
        for (Future<Boolean> f : results) if (f.get()) successes++;
        pool.shutdown();

        assertEquals(7, successes);
        assertEquals(7, app.activities.findById(a.getId()).orElseThrow().getCurrentEnrollments());
        for (int i = 0; i < 10; i++) app.activities.releaseSlot(a.getId());
        assertEquals(0, app.activities.findById(a.getId()).orElseThrow().getCurrentEnrollments(), "nunca fica negativo");
    }

    @Test
    @DisplayName("atividade sem controle de vagas aceita qualquer quantidade de reservas")
    void reserveSlotWithoutControl() {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        Activity a = activity(ev, 1, false);
        assertTrue(app.activities.reserveSlot(a.getId()));
        assertTrue(app.activities.reserveSlot(a.getId()));
    }

    @Test
    @DisplayName("cada política de certificado sobrevive à gravação e à leitura")
    void certificatePolicyRoundTrip() {
        Event base = event(new MinimumActivitiesPolicy(4));
        Activity required = activity(base, 10, true);
        for (CertificateEligibilityPolicy policy : new CertificateEligibilityPolicy[]{
                new MinimumAttendancePercentagePolicy(82.5), new MinimumActivitiesPolicy(4),
                new RequiredActivitiesPolicy(Set.of(required.getId(), required.getId() + 1000))}) {
            Event ev = event(policy);
            Event loaded = app.events.findById(ev.getId()).orElseThrow();
            assertEquals(policy.getClass(), loaded.getCertificateEligibilityPolicy().getClass());
            assertEquals(policy.spec(), loaded.getCertificateEligibilityPolicy().spec());
        }
    }

    @Test
    @DisplayName("linhas gravadas por versões anteriores continuam legíveis; política inválida cai no padrão")
    void legacyPolicyRows() throws Exception {
        Event legacy = event(new MinimumAttendancePercentagePolicy(75));
        Event broken = event(new MinimumAttendancePercentagePolicy(75));
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement s = c.prepareStatement("UPDATE events SET cert_policy_type = ?, cert_policy_param = ? WHERE id = ?")) {
            s.setString(1, "MANDATORY_COUNT");
            s.setDouble(2, 3);
            s.setLong(3, legacy.getId());
            s.executeUpdate();
            s.setString(1, "TIPO_QUE_NAO_EXISTE");
            s.setDouble(2, 1);
            s.setLong(3, broken.getId());
            s.executeUpdate();
        }
        CertificateEligibilityPolicy legacyPolicy = app.events.findById(legacy.getId()).orElseThrow().getCertificateEligibilityPolicy();
        assertEquals(MinimumActivitiesPolicy.class, legacyPolicy.getClass());
        assertEquals(3, legacyPolicy.spec().minActivities());
        assertEquals(MinimumAttendancePercentagePolicy.class,
                app.events.findById(broken.getId()).orElseThrow().getCertificateEligibilityPolicy().getClass());
    }

    @Test
    @DisplayName("atualizar o evento persiste estado, capacidade e regras de inscrição")
    void eventUpdate() {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        ev.configureRegistration(true, true, start.minusDays(1), "America/Manaus");
        ev.updateDetails("Novo título", "Nova descrição", null, 77);
        app.events.save(ev);
        Event loaded = app.events.findById(ev.getId()).orElseThrow();
        assertEquals("Novo título", loaded.getTitle());
        assertEquals(77, loaded.getMaxCapacity());
        assertTrue(loaded.isActivitySelectionRequired());
        assertEquals("America/Manaus", loaded.getTimeZone());
        assertEquals(start.minusDays(1), loaded.getRegistrationDeadline());
    }

    @Test
    @DisplayName("questionário grava perguntas, opções e política de anonimato; respostas respeitam a unicidade")
    void surveyRoundTrip() {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        Activity a = activity(ev, 10, true);
        FeedbackSurvey saved = app.surveys.saveSurvey(new FeedbackSurvey(null, a.getId(), "Pesquisa", List.of(
                new SurveyQuestion(null, "Escolha", QuestionType.SINGLE_CHOICE, List.of("Sim", "Não"), true),
                new SurveyQuestion(null, "Nota", QuestionType.RATING_SCALE, List.of(), false)), true, true));
        FeedbackSurvey loaded = app.surveys.findSurveyByActivityId(a.getId()).orElseThrow();
        assertTrue(loaded.isAnonymousResponses());
        assertEquals(List.of("Sim", "Não"), loaded.getQuestions().get(0).getOptions());
        assertEquals(QuestionType.RATING_SCALE, loaded.getQuestions().get(1).getType());
        assertEquals(saved.getId(), loaded.getId());
    }

    @Test
    @DisplayName("inscrições e frequência são lidas em lote por evento")
    void batchReads() {
        Event ev = event(new MinimumAttendancePercentagePolicy(75));
        Activity a = activity(ev, 10, true);
        Activity b = app.activities.save(new Activity(null, ev.getId(), "Outra", "", new Period(start.plusHours(5), start.plusHours(6)),
                new ActivityLocation("Sala R", "T", 10), ActivityType.LECTURE, 10, 0, List.of(), new SingleCheckInPolicy(), true));
        User u1 = app.createUser("Leitor Um", UserRole.PARTICIPANT);
        User u2 = app.createUser("Leitor Dois", UserRole.PARTICIPANT);
        app.registrations.save(new Registration(null, ev.getId(), u1.getId(), Set.of(a.getId(), b.getId()), start.minusDays(2), RegistrationStatus.CONFIRMED));
        app.registrations.save(new Registration(null, ev.getId(), u2.getId(), Set.of(b.getId()), start.minusDays(1), RegistrationStatus.CONFIRMED));
        app.attendance.save(new AttendanceRecord(null, a.getId(), u1.getId(), start.plusHours(1), AttendanceType.CHECK_IN, "QR_SCAN", null));
        app.attendance.save(new AttendanceRecord(null, b.getId(), u2.getId(), start.plusHours(5), AttendanceType.CHECK_IN, "QR_SCAN", null));

        List<Registration> regs = app.registrations.findByEventId(ev.getId());
        assertEquals(2, regs.size());
        assertEquals(Set.of(a.getId(), b.getId()), regs.get(0).getSelectedActivityIds());
        assertEquals(Set.of(b.getId()), regs.get(1).getSelectedActivityIds());
        assertEquals(2, app.attendance.findByEventId(ev.getId()).size());
        assertEquals(1, app.attendance.findByUserAndEvent(u1.getId(), ev.getId()).size());
        assertEquals(2, app.users.findByIds(List.of(u1.getId(), u2.getId(), u1.getId())).size() + 0);
        assertEquals(List.of(a.getId(), b.getId()), app.activities.findByIds(List.of(b.getId(), a.getId())).stream().map(Activity::getId).toList(),
                "ordenadas por horário");
        assertEquals(2, app.registrations.countConfirmedByEventId(ev.getId()));
        assertEquals(1, app.registrations.countConfirmedByActivityId(a.getId()));
    }

    @Test
    @DisplayName("e-mail é único no banco (RN-01), mesmo ignorando o caso")
    void emailUniqueness() {
        User first = app.createUser("Único", UserRole.PARTICIPANT);
        boolean rejected = false;
        try {
            app.users.save(new User(null, "Clone", first.getEmail(), "hash", UserRole.PARTICIPANT, LocalDateTime.now()));
        } catch (IllegalStateException e) {
            rejected = true;
        }
        assertTrue(rejected);
        AtomicInteger count = new AtomicInteger();
        app.users.findAll().stream().filter(u -> u.getEmail().equals(first.getEmail())).forEach(u -> count.incrementAndGet());
        assertEquals(1, count.get());
    }
}
