package com.eventos;

import com.eventos.adapters.input.desktop.SwingDesktopApp;
import com.eventos.adapters.input.rest.EventRestController;
import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.EventPersistenceAdapter;
import com.eventos.adapters.output.persistence.ParticipantPersistenceAdapter;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.ParticipantUseCase;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.ports.output.ParticipantRepository;
import com.eventos.application.services.EventServiceImpl;
import com.eventos.application.services.ParticipantServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import javax.swing.*;
import java.awt.*;

public class EventosApplication {

    public static void main(String[] args) {
        System.out.println("=== Inicializando o EventOS (Stack Leve) ===");

        // 1. Inicializar Banco de Dados H2
        DatabaseManager.initializeDatabase();

        // 2. Fiação Manual (Injeção de Dependências - Hexagonal Architecture)
        EventRepository eventRepository = new EventPersistenceAdapter();
        ParticipantRepository participantRepository = new ParticipantPersistenceAdapter();

        EventUseCase eventUseCase = new EventServiceImpl(eventRepository);
        ParticipantUseCase participantUseCase = new ParticipantServiceImpl(participantRepository);

        // 3. Inicializar e Configurar Servidor de API REST (Javalin)
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Formato ISO-8601
            }));
        });

        // Registrar o Adaptador REST na aplicação Javalin
        new EventRestController(eventUseCase, app);

        // Iniciar Servidor Web
        int port = 8080;
        try {
            app.start(port);
            System.out.println("Servidor API REST rodando na porta " + port);
        } catch (Exception e) {
            System.err.println("Erro ao iniciar servidor REST: " + e.getMessage());
        }

        // 4. Inicializar Interface Gráfica Desktop (Swing) se não for headless
        if (!GraphicsEnvironment.isHeadless()) {
            EventQueue.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    // Fallback
                }
                
                try {
                    SwingDesktopApp desktopApp = new SwingDesktopApp(eventUseCase);
                    desktopApp.setVisible(true);
                    System.out.println("Interface gráfica Desktop inicializada com sucesso.");
                } catch (Exception e) {
                    System.err.println("Erro ao inicializar interface Desktop: " + e.getMessage());
                }
            });
        } else {
            System.out.println("Modo headless ativo: Interface gráfica Swing não será aberta.");
        }
    }
}
