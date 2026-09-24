package com.eventos.adapters.output.persistence;

import com.eventos.adapters.output.security.Sha256SecurityAdapter;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.CheckInCheckOutPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Povoamento oficial de dados para a disciplina de POO II com os Professores e Alunos reais.
 */
public class DatabaseSeeder {

    public static void seedDemoData(JdbcUserRepository userRepo,
                                   JdbcEventRepository eventRepo,
                                   JdbcActivityRepository actRepo,
                                   JdbcRegistrationRepository regRepo,
                                   JdbcAttendanceRepository attRepo,
                                   JdbcSurveyRepository surveyRepo) {
        if (!userRepo.findAll().isEmpty()) {
            System.out.println("Base de dados ja contem registros. Povoamento ignorado.");
            return;
        }

        System.out.println("Iniciando povoamento de dados reais de POO II...");
        Sha256SecurityAdapter sec = new Sha256SecurityAdapter();

        // 1. Professores (Organizadores e Administradores)
        User profMarcio = userRepo.save(new User(null, "Prof. Marcio Giovane", new Email("marcio.giovane@universidade.edu.br"), sec.hashPassword("marcio123"), UserRole.ORGANIZER, LocalDateTime.now()));
        User profJoilson = userRepo.save(new User(null, "Prof. Joilson Brito", new Email("joilson.brito@universidade.edu.br"), sec.hashPassword("joilson123"), UserRole.ORGANIZER, LocalDateTime.now()));
        User profGuiliano = userRepo.save(new User(null, "Prof. Guiliano Rangel", new Email("guiliano.rangel@universidade.edu.br"), sec.hashPassword("guiliano123"), UserRole.ADMIN, LocalDateTime.now()));
        User profaJuliana = userRepo.save(new User(null, "Profa. Juliana Braga", new Email("juliana.braga@universidade.edu.br"), sec.hashPassword("juliana123"), UserRole.ORGANIZER, LocalDateTime.now()));

        // 2. Alunos (Participantes)
        User alunoGui = userRepo.save(new User(null, "Guilherme Barbosa", new Email("guilherme.barbosa@aluno.edu.br"), sec.hashPassword("gui123"), UserRole.PARTICIPANT, LocalDateTime.now()));
        User alunoMurilo = userRepo.save(new User(null, "Murilo Mendes", new Email("murilo.mendes@aluno.edu.br"), sec.hashPassword("murilo123"), UserRole.PARTICIPANT, LocalDateTime.now()));
        User alunaDani = userRepo.save(new User(null, "Danielly Mendes", new Email("danielly.mendes@aluno.edu.br"), sec.hashPassword("dani123"), UserRole.PARTICIPANT, LocalDateTime.now()));
        User alunoSamir = userRepo.save(new User(null, "Samir Santana", new Email("samir.santana@aluno.edu.br"), sec.hashPassword("samir123"), UserRole.PARTICIPANT, LocalDateTime.now()));

        // 3. Evento Oficial da Disciplina
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eventStart = now.plusDays(1).withHour(8).withMinute(0);
        LocalDateTime eventEnd = now.plusDays(2).withHour(18).withMinute(0);

        Event simposio = new Event(null,
                "XV Simpósio de Tecnologia da Informação",
                "Congresso acadêmico oficial de Tecnologia da Informação e Programação Orientada a Objetos. Apresentação dos projetos integradores, palestras magnas e workshops técnicos.",
                new Period(eventStart, eventEnd),
                EventStatus.PUBLISHED,
                profMarcio.getId(),
                null,
                250,
                new MinimumAttendancePercentagePolicy(50.0));
        simposio = eventRepo.save(simposio);

        // 4. Atividades com Professores como Palestrantes/Responsáveis
        // Atividade 1: Palestra Magna
        Activity keynote = new Activity(null, simposio.getId(),
                "Abertura & Palestra Magna: Evolucao da POO e Arquitetura Limpa",
                "Discussao sobre o papel da modelagem rica de objetos, encapsulamento de invariantes e independencia de frameworks em sistemas modernos.",
                new Period(eventStart.plusHours(1), eventStart.plusHours(3)),
                new ActivityLocation("Auditorio Principal", "Trilha Arquitetura", 200),
                ActivityType.LECTURE,
                200, 0,
                List.of(new Speaker(null, "Prof. Marcio Giovane", "Palestrante Principal", "Professor de POO II e Especialista em Engenharia de Software", null)),
                new SingleCheckInPolicy(),
                true);
        keynote = actRepo.save(keynote);

        // Atividade 2: Workshop Prático
        Activity workshop = new Activity(null, simposio.getId(),
                "Workshop Pratico: Padroes de Projeto e Refatoracao em Java",
                "Laboratorio de boas praticas orientado por Strategy, Adapter, Factory e inversao de dependencias (SOLID).",
                new Period(eventStart.plusHours(4), eventStart.plusHours(7)),
                new ActivityLocation("Laboratorio de Informatica 01", "Trilha Desenvolvimento", 40),
                ActivityType.WORKSHOP,
                40, 0,
                List.of(
                        new Speaker(null, "Prof. Joilson Brito", "Instrutor", "Especialista em Desenvolvimento Java e POO", null),
                        new Speaker(null, "Profa. Juliana Braga", "Instrutora", "Pesquisadora em Engenharia de Software", null)
                ),
                new CheckInCheckOutPolicy(),
                true);
        workshop = actRepo.save(workshop);

        // Atividade 3: Sessão Técnica de Projetos
        Activity papers = new Activity(null, simposio.getId(),
                "Sessao Tecnica: Apresentacao dos Projetos Integradores de POO II",
                "Banca examinadora e apresentacao oral dos incrementos desenvolvidos pelas equipes discentes.",
                new Period(eventStart.plusDays(1).plusHours(2), eventStart.plusDays(1).plusHours(6)),
                new ActivityLocation("Sala de Conferencias 04", "Trilha Avaliacao", 60),
                ActivityType.ORAL_PRESENTATION,
                60, 0,
                List.of(new Speaker(null, "Prof. Guiliano Rangel", "Presidente da Banca", "Coordenador e Avaliador de Projetos", null)),
                new SingleCheckInPolicy(),
                false);
        papers = actRepo.save(papers);

        // 5. Questionário de Avaliação para a Palestra Magna
        FeedbackSurvey surveyKeynote = new FeedbackSurvey(null, keynote.getId(), "Avaliacao da Palestra Magna de POO II",
                List.of(
                        new SurveyQuestion(null, "Como voce avalia a didatica, clareza e profundidade dos conceitos apresentados?", QuestionType.RATING_SCALE, List.of(), true),
                        new SurveyQuestion(null, "O conteudo abordado contribuiu para sua compreensao da Arquitetura Hexagonal e POO?", QuestionType.SINGLE_CHOICE, List.of("Sim, contribuiu significativamente", "Contribuiu parcialmente", "Nao contribuiu"), true),
                        new SurveyQuestion(null, "Espaco aberto para consideracoes, elogios ou sugestoes:", QuestionType.TEXT, List.of(), false)
                ), true);
        surveyRepo.saveSurvey(surveyKeynote);

        // 6. Inscrições dos Alunos
        // Guilherme (inscrito no Keynote e Workshop com presença validada no Keynote para teste imediato de avaliação e certificado)
        com.eventos.domain.model.Registration regGui = new com.eventos.domain.model.Registration(null, simposio.getId(), alunoGui.getId(),
                Set.of(keynote.getId(), workshop.getId()), LocalDateTime.now().minusHours(4), com.eventos.domain.model.RegistrationStatus.CONFIRMED);
        regRepo.save(regGui);
        actRepo.updateEnrollments(keynote.getId(), 1);
        actRepo.updateEnrollments(workshop.getId(), 1);

        // Murilo (inscrito no Keynote e Sessão Técnica)
        com.eventos.domain.model.Registration regMurilo = new com.eventos.domain.model.Registration(null, simposio.getId(), alunoMurilo.getId(),
                Set.of(keynote.getId(), papers.getId()), LocalDateTime.now().minusHours(3), com.eventos.domain.model.RegistrationStatus.CONFIRMED);
        regRepo.save(regMurilo);
        actRepo.updateEnrollments(keynote.getId(), 2);

        // Danielly (inscrita no Simpósio)
        com.eventos.domain.model.Registration regDani = new com.eventos.domain.model.Registration(null, simposio.getId(), alunaDani.getId(),
                Set.of(keynote.getId()), LocalDateTime.now().minusHours(2), com.eventos.domain.model.RegistrationStatus.CONFIRMED);
        regRepo.save(regDani);
        actRepo.updateEnrollments(keynote.getId(), 3);

        // Samir (inscrito no Simpósio)
        com.eventos.domain.model.Registration regSamir = new com.eventos.domain.model.Registration(null, simposio.getId(), alunoSamir.getId(),
                Set.of(workshop.getId()), LocalDateTime.now().minusHours(1), com.eventos.domain.model.RegistrationStatus.CONFIRMED);
        regRepo.save(regSamir);
        actRepo.updateEnrollments(workshop.getId(), 2);

        // 7. Registro de Presença do Guilherme no Keynote (habilita avaliação e certificado)
        attRepo.save(new com.eventos.domain.model.AttendanceRecord(null, keynote.getId(), alunoGui.getId(),
                LocalDateTime.now().minusHours(2), com.eventos.domain.model.AttendanceType.CHECK_IN, "QR_SCAN", "Presenca validada via QR Code"));

        System.out.println("Povoamento de dados de POO II concluido com sucesso!");
    }
}
