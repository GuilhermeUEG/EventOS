package com.eventos;

import com.eventos.adapters.input.rest.EventRestController;
import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.EventPersistenceAdapter;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.services.EventServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventosApplicationTests {

    private static Javalin app;
    private static int port;

    @BeforeAll
    static void setUp() {
        // Inicializar Banco H2
        DatabaseManager.initializeDatabase();

        // Inicializar Repositórios e Serviços do Domínio
        EventRepository eventRepository = new EventPersistenceAdapter();
        EventUseCase eventUseCase = new EventServiceImpl(eventRepository);

        // Configurar Jackson para LocalDateTime no Javalin
        app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
            }));
        });

        // Registrar rotas
        new EventRestController(eventUseCase, app);

        // Iniciar Javalin em uma porta aleatória disponível (porta 0)
        app.start(0);
        port = app.port();
    }

    @AfterAll
    static void tearDown() {
        app.stop();
    }

    @Test
    void testListEventsApiSuccess() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/events"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }
}
