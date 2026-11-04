package com.eventos.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import com.eventos.support.ApiTestClient;
import com.eventos.support.ApiTestClient.Response;
import com.eventos.support.TestApp;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Página inicial: eventos em destaque e rota pública /api/home (RF-11, RF-14, RN-04)")
class HomePageTest {
    private static TestApp app;
    private static ApiTestClient api;
    private static User organizer;
    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeAll
    static void start() {
        app = TestApp.start();
        api = new ApiTestClient(app.baseUrl);
        organizer = app.createUser("Organização da Home", UserRole.ORGANIZER);
        // Do mais próximo ao mais distante: Logo (em 1h), Depois (em 20 dias); já passados: Passado, Antigo.
        publishedEvent("Home Logo", NOW.plusHours(1), NOW.plusHours(6), true);
        publishedEvent("Home Depois", NOW.plusDays(20), NOW.plusDays(21), false);
        publishedEvent("Home Passado", NOW.minusDays(10), NOW.minusDays(9), false);
        publishedEvent("Home Antigo", NOW.minusDays(40), NOW.minusDays(39), false);
        // Rascunho com data mais próxima que todos: não pode aparecer.
        app.eventUseCase.createEvent(new Event(null, "Home Rascunho", "x", new Period(NOW.plusMinutes(30), NOW.plusHours(2)),
                EventStatus.DRAFT, organizer.getId(), null, 10, new MinimumAttendancePercentagePolicy(50)));
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private static void publishedEvent(String title, LocalDateTime start, LocalDateTime end, boolean sameSpeakerTwice) {
        Event event = app.eventUseCase.createEvent(new Event(null, title, "Descrição de " + title, new Period(start, end),
                EventStatus.DRAFT, organizer.getId(), null, 100, new MinimumAttendancePercentagePolicy(50)));
        Speaker speaker = new Speaker(null, "Dra. Home Silva", "Palestrante", "Bio da pessoa em destaque", null);
        app.eventUseCase.addActivityToEvent(event.getId(), new Activity(null, event.getId(), title + " - abertura", "d",
                new Period(start, start.plusHours(1)), new ActivityLocation("Sala 1", "Trilha H", 50),
                ActivityType.LECTURE, 50, 0, List.of(speaker), new SingleCheckInPolicy(), true));
        if (sameSpeakerTwice) {
            app.eventUseCase.addActivityToEvent(event.getId(), new Activity(null, event.getId(), title + " - encerramento", "d",
                    new Period(start.plusHours(2), start.plusHours(3)), new ActivityLocation("Sala 2", "Trilha H", 50),
                    ActivityType.LECTURE, 50, 0, List.of(speaker), new SingleCheckInPolicy(), true));
        }
        app.eventUseCase.publishEvent(event.getId());
    }

    @Test
    @DisplayName("destaques: primeiro os que ainda não terminaram (do mais próximo), depois os passados (do mais recente)")
    void upcomingFirstThenMostRecentPast() {
        List<String> titles = app.eventUseCase.featuredEvents(1000, NOW).stream()
                .map(Event::getTitle).filter(t -> t.startsWith("Home ")).toList();
        assertEquals(List.of("Home Logo", "Home Depois", "Home Passado", "Home Antigo"), titles,
                "o rascunho não entra (RN-04)");
        assertEquals(2, app.eventUseCase.featuredEvents(2, NOW).size(), "respeita o limite");
        assertFalse(app.eventUseCase.featuredEvents(1, NOW).get(0).getActivities().isEmpty(), "vem com a programação");
    }

    @Test
    @DisplayName("/api/home é pública, só expõe eventos publicados e não repete pessoas em destaque")
    void homeEndpointIsPublic() {
        Response response = api.get("/api/home", null);
        assertEquals(200, response.status());
        JsonNode home = response.json();
        assertTrue(home.path("publicEvents").asInt() >= 4);
        assertTrue(home.path("upcomingEvents").asInt() >= 2);

        List<String> eventTitles = new ArrayList<>();
        home.path("events").forEach(node -> eventTitles.add(node.path("title").asText()));
        assertTrue(eventTitles.contains("Home Logo"));
        assertFalse(eventTitles.contains("Home Rascunho"), "rascunho nunca aparece na página pública");
        assertTrue(home.path("events").size() <= 6 && home.path("activities").size() <= 6 && home.path("speakers").size() <= 6);

        JsonNode first = home.path("events").get(0);
        assertEquals("Home Logo", first.path("title").asText(), "o evento mais próximo vem primeiro");
        assertEquals(2, first.path("activityCount").asInt());
        assertTrue(first.path("upcoming").asBoolean());

        long silva = 0;
        for (JsonNode speaker : home.path("speakers")) {
            if (speaker.path("name").asText().equals("Dra. Home Silva")) silva++;
        }
        assertEquals(1, silva, "a mesma pessoa em várias atividades aparece uma só vez");
        home.path("activities").forEach(node -> assertFalse(node.path("eventTitle").asText().isBlank()));
    }
}
