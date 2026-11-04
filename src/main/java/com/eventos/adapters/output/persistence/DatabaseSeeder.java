package com.eventos.adapters.output.persistence;

import com.eventos.adapters.output.security.Pbkdf2SecurityAdapter;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Email;
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
import com.eventos.domain.policies.CheckInCheckOutPolicy;
import com.eventos.domain.policies.MinimumActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dados de demonstração (RNF-04): contas e evento do roteiro dos cenários CA-01 a CA-08 e, de forma
 * opcional, uma base de carga com 500 participantes e 100 atividades para o requisito de desempenho (RNF-08).
 */
public final class DatabaseSeeder {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    /** Conta de demonstração: nunca use estas credenciais fora de um ambiente de avaliação. */
    public record DemoAccount(String name, String email, String password, UserRole role) {
    }

    public static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
            new DemoAccount("Prof. Marcio Giovane", "marcio.giovane@universidade.edu.br", "marcio123", UserRole.ORGANIZER),
            new DemoAccount("Prof. Joilson Brito", "joilson.brito@universidade.edu.br", "joilson123", UserRole.ORGANIZER),
            new DemoAccount("Prof. Guiliano Rangel", "guiliano.rangel@universidade.edu.br", "guiliano123", UserRole.ADMIN),
            new DemoAccount("Profa. Juliana Braga", "juliana.braga@universidade.edu.br", "juliana123", UserRole.ORGANIZER),
            new DemoAccount("Guilherme Barbosa", "guilherme.barbosa@aluno.edu.br", "gui123", UserRole.PARTICIPANT),
            new DemoAccount("Murilo Mendes", "murilo.mendes@aluno.edu.br", "murilo123", UserRole.PARTICIPANT),
            new DemoAccount("Danielly Mendes", "danielly.mendes@aluno.edu.br", "dani123", UserRole.PARTICIPANT),
            new DemoAccount("Samir Santana", "samir.santana@aluno.edu.br", "samir123", UserRole.PARTICIPANT));

    public static final String LOAD_PASSWORD = "carga123";
    public static final int LOAD_PARTICIPANTS = 500;
    public static final int LOAD_ACTIVITIES = 100;
    private static final String LOAD_DOMAIN = "@carga.demo";

    private DatabaseSeeder() {
    }

    public static void seedDemoData(JdbcUserRepository userRepo,
                                    JdbcEventRepository eventRepo,
                                    JdbcActivityRepository actRepo,
                                    JdbcRegistrationRepository regRepo,
                                    JdbcAttendanceRepository attRepo,
                                    JdbcSurveyRepository surveyRepo) {
        Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter();
        Optional<User> existingStudent = userRepo.findByEmail(new Email("guilherme.barbosa@aluno.edu.br"));
        if (existingStudent.isPresent()) {
            upgradeLegacyDemoPasswords(userRepo, security);
            ensureShowcaseEvents(userRepo, eventRepo, actRepo);
            log.info("Base de demonstração já existente; nada a povoar.");
            return;
        }
        log.info("Povoando a base de demonstração...");

        User profMarcio = ensureUser(userRepo, security, DEMO_ACCOUNTS.get(0));
        ensureUser(userRepo, security, DEMO_ACCOUNTS.get(1));
        ensureUser(userRepo, security, DEMO_ACCOUNTS.get(2));
        ensureUser(userRepo, security, DEMO_ACCOUNTS.get(3));
        User alunoGui = ensureUser(userRepo, security, DEMO_ACCOUNTS.get(4));
        User alunoMurilo = ensureUser(userRepo, security, DEMO_ACCOUNTS.get(5));
        User alunaDani = ensureUser(userRepo, security, DEMO_ACCOUNTS.get(6));
        User alunoSamir = ensureUser(userRepo, security, DEMO_ACCOUNTS.get(7));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eventStart = now.plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime eventEnd = now.plusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0);

        Event simposio = eventRepo.save(new Event(null,
                "XV Simpósio de Tecnologia da Informação",
                "Congresso acadêmico de Tecnologia da Informação e Programação Orientada a Objetos. Apresentação dos projetos integradores, palestras magnas e workshops técnicos.",
                new Period(eventStart, eventEnd),
                EventStatus.PUBLISHED,
                profMarcio.getId(),
                null,
                250,
                new MinimumAttendancePercentagePolicy(50.0)));
        // Prazo de inscrição até o encerramento do evento, para que a demonstração funcione durante toda a janela.
        simposio.configureRegistration(true, false, eventEnd, Event.DEFAULT_TIME_ZONE);
        simposio = eventRepo.save(simposio);

        Activity keynote = actRepo.save(new Activity(null, simposio.getId(),
                "Abertura & Palestra Magna: Evolução da POO e Arquitetura Limpa",
                "Discussão sobre o papel da modelagem rica de objetos, encapsulamento de invariantes e independência de frameworks em sistemas modernos.",
                new Period(eventStart.plusHours(1), eventStart.plusHours(3)),
                new ActivityLocation("Auditório Principal", "Trilha Arquitetura", 200),
                ActivityType.LECTURE, 200, 0,
                List.of(new Speaker(null, "Prof. Marcio Giovane", "Palestrante Principal",
                        "Professor de POO II e especialista em Engenharia de Software", null)),
                new SingleCheckInPolicy(), true));

        Activity workshop = actRepo.save(new Activity(null, simposio.getId(),
                "Workshop Prático: Padrões de Projeto e Refatoração em Java",
                "Laboratório de boas práticas orientado por Strategy, Adapter, Factory e inversão de dependências (SOLID).",
                new Period(eventStart.plusHours(4), eventStart.plusHours(7)),
                new ActivityLocation("Laboratório de Informática 01", "Trilha Desenvolvimento", 40),
                ActivityType.WORKSHOP, 40, 0,
                List.of(
                        new Speaker(null, "Prof. Joilson Brito", "Instrutor", "Especialista em desenvolvimento Java e POO", null),
                        new Speaker(null, "Profa. Juliana Braga", "Instrutora", "Pesquisadora em Engenharia de Software", null)),
                new CheckInCheckOutPolicy(), true));

        Activity papers = actRepo.save(new Activity(null, simposio.getId(),
                "Sessão Técnica: Apresentação dos Projetos Integradores de POO II",
                "Banca examinadora e apresentação oral dos incrementos desenvolvidos pelas equipes discentes.",
                new Period(eventStart.plusDays(1).plusHours(2), eventStart.plusDays(1).plusHours(6)),
                new ActivityLocation("Sala de Conferências 04", "Trilha Avaliação", 60),
                ActivityType.ORAL_PRESENTATION, 60, 0,
                List.of(new Speaker(null, "Prof. Guiliano Rangel", "Presidente da Banca",
                        "Coordenador e avaliador de projetos", null)),
                new SingleCheckInPolicy(), false));

        surveyRepo.saveSurvey(new FeedbackSurvey(null, keynote.getId(), "Avaliação da Palestra Magna de POO II",
                List.of(
                        new SurveyQuestion(null, "Como você avalia a didática, a clareza e a profundidade dos conceitos apresentados?",
                                QuestionType.RATING_SCALE, List.of(), true),
                        new SurveyQuestion(null, "O conteúdo contribuiu para sua compreensão da Arquitetura Hexagonal e de POO?",
                                QuestionType.SINGLE_CHOICE,
                                List.of("Sim, contribuiu significativamente", "Contribuiu parcialmente", "Não contribuiu"), true),
                        new SurveyQuestion(null, "Espaço aberto para considerações, elogios ou sugestões:",
                                QuestionType.TEXT, List.of(), false)),
                true, false));

        // Guilherme: Palestra Magna + Workshop, com presença validada nas duas (habilita avaliação e certificado).
        register(regRepo, actRepo, simposio, alunoGui, List.of(keynote, workshop), now.minusHours(4));
        // Murilo: Palestra Magna + Sessão Técnica (esta não controla vagas).
        register(regRepo, actRepo, simposio, alunoMurilo, List.of(keynote, papers), now.minusHours(3));
        register(regRepo, actRepo, simposio, alunaDani, List.of(keynote), now.minusHours(2));
        register(regRepo, actRepo, simposio, alunoSamir, List.of(workshop), now.minusHours(1));

        attRepo.save(new AttendanceRecord(null, keynote.getId(), alunoGui.getId(), now.minusHours(2),
                AttendanceType.CHECK_IN, AttendanceRecord.ORIGIN_QR, "Presença validada via QR Code"));
        // Workshop de entrada e saída: com as duas marcações o Guilherme soma 5h de 9h (55%) e fica ELEGÍVEL ao
        // certificado (limite de 50%); Murilo e Danielly seguem inelegíveis, para mostrar a justificativa (CA-08).
        attRepo.save(new AttendanceRecord(null, workshop.getId(), alunoGui.getId(), now.minusHours(2),
                AttendanceType.CHECK_IN, AttendanceRecord.ORIGIN_QR, "Entrada validada via QR Code"));
        attRepo.save(new AttendanceRecord(null, workshop.getId(), alunoGui.getId(), now.minusHours(1),
                AttendanceType.CHECK_OUT, AttendanceRecord.ORIGIN_QR, "Saída validada via QR Code"));

        ensureShowcaseEvents(userRepo, eventRepo, actRepo);
        log.info("Base de demonstração criada.");
    }

    /**
     * Base de carga (RNF-08): 500 participantes e 100 atividades em um único evento, cada participante
     * com 3 atividades sem sobreposição e parte deles com presença registrada. Idempotente.
     */
    public static void seedLargeDataset(JdbcUserRepository userRepo,
                                        JdbcEventRepository eventRepo,
                                        JdbcActivityRepository actRepo,
                                        JdbcRegistrationRepository regRepo,
                                        JdbcAttendanceRepository attRepo) {
        if (userRepo.findByEmail(new Email("participante001" + LOAD_DOMAIN)).isPresent()) {
            log.info("Base de carga já existente.");
            return;
        }
        log.info("Criando base de carga com {} participantes e {} atividades...", LOAD_PARTICIPANTS, LOAD_ACTIVITIES);
        Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter();
        User organizer = ensureUser(userRepo, security, DEMO_ACCOUNTS.get(0));
        String sharedHash = security.hashPassword(LOAD_PASSWORD); // um único hash: o custo do PBKDF2 é intencional

        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(8).withMinute(0).withSecond(0).withNano(0);
        Event congress = eventRepo.save(new Event(null, "Congresso de Demonstração de Carga",
                "Base com 500 participantes e 100 atividades para avaliar o desempenho das consultas.",
                new Period(start, start.plusDays(4).withHour(18)), EventStatus.PUBLISHED, organizer.getId(),
                null, LOAD_PARTICIPANTS + 100, new MinimumAttendancePercentagePolicy(50.0)));
        congress.configureRegistration(true, false, start.plusDays(4), Event.DEFAULT_TIME_ZONE);
        congress = eventRepo.save(congress);

        // 5 dias x 2 turnos x 10 salas = 100 atividades, sem conflito de sala.
        Activity[] activities = new Activity[LOAD_ACTIVITIES];
        for (int day = 0; day < 5; day++) {
            for (int shift = 0; shift < 2; shift++) {
                for (int room = 0; room < 10; room++) {
                    int index = day * 20 + shift * 10 + room;
                    LocalDateTime from = start.plusDays(day).plusHours(shift * 3L);
                    ActivityType type = ActivityType.values()[index % ActivityType.values().length];
                    activities[index] = actRepo.save(new Activity(null, congress.getId(),
                            "Atividade " + (index + 1) + " - Sala " + (room + 1), "Atividade de carga número " + (index + 1) + ".",
                            new Period(from, from.plusHours(2)),
                            new ActivityLocation("Sala " + (room + 1), "Trilha " + (room % 5 + 1), 60),
                            type, 60, 0, List.of(new Speaker(null, "Palestrante " + (index + 1), "Palestrante", "", null)),
                            index % 4 == 0 ? new CheckInCheckOutPolicy() : new SingleCheckInPolicy(), true));
                }
            }
        }

        for (int u = 1; u <= LOAD_PARTICIPANTS; u++) {
            User user = userRepo.save(new User(null, String.format("Participante %03d", u),
                    new Email(String.format("participante%03d%s", u, LOAD_DOMAIN)), sharedHash, UserRole.PARTICIPANT,
                    LocalDateTime.now()));
            Set<Long> chosenIds = new HashSet<>();
            List<Activity> chosen = new java.util.ArrayList<>();
            for (int k = 0; k < 3; k++) {
                int day = (u + k) % 5;
                int shift = k % 2;
                int room = (u * 3 + k) % 10;
                Activity activity = activities[day * 20 + shift * 10 + room];
                if (chosenIds.add(activity.getId())) chosen.add(activity);
            }
            register(regRepo, actRepo, congress, user, chosen, LocalDateTime.now());
            if (u % 3 == 0) {
                Activity first = chosen.get(0);
                attRepo.save(new AttendanceRecord(null, first.getId(), user.getId(), LocalDateTime.now(),
                        AttendanceType.CHECK_IN, AttendanceRecord.ORIGIN_QR, "Base de carga"));
            }
        }
        log.info("Base de carga criada.");
    }

    /**
     * Eventos de vitrine, de outros tipos e de outros organizadores, para que a página inicial e a busca não
     * tenham um único evento e mostrem que a plataforma não é só para eventos acadêmicos. Idempotente: cria
     * apenas o que ainda não existe (pelo título), inclusive em uma base de demonstração já povoada.
     */
    private static void ensureShowcaseEvents(JdbcUserRepository userRepo, JdbcEventRepository eventRepo,
                                             JdbcActivityRepository actRepo) {
        Optional<User> joilson = userRepo.findByEmail(new Email(DEMO_ACCOUNTS.get(1).email()));
        Optional<User> juliana = userRepo.findByEmail(new Email(DEMO_ACCOUNTS.get(3).email()));
        if (joilson.isEmpty() || juliana.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        eventRepo.findAll().forEach(e -> existing.add(e.getTitle()));
        LocalDateTime base = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0);

        String fairTitle = "Feira de Carreiras em Tecnologia";
        if (!existing.contains(fairTitle)) {
            LocalDateTime start = base.plusDays(9).withHour(9);
            LocalDateTime end = base.plusDays(10).withHour(17);
            Event fair = eventRepo.save(new Event(null, fairTitle,
                    "Encontro entre estudantes, profissionais e empresas da região: painéis sobre o mercado de trabalho, oficinas de currículo e portfólio e conversas com quem já está na área.",
                    new Period(start, end), EventStatus.PUBLISHED, juliana.get().getId(), null, 400,
                    new MinimumActivitiesPolicy(1)));
            fair.configureRegistration(true, false, start.minusDays(1), Event.DEFAULT_TIME_ZONE);
            fair = eventRepo.save(fair);
            actRepo.save(new Activity(null, fair.getId(), "Painel: Carreira e Mercado de Trabalho em TI",
                    "Profissionais de diferentes empresas contam como entraram na área, o que o mercado procura e como se preparar.",
                    new Period(start.plusHours(1), start.plusHours(3)),
                    new ActivityLocation("Auditório Principal", "Trilha Mercado", 150),
                    ActivityType.ROUND_TABLE, 150, 0,
                    List.of(new Speaker(null, "Carla Menezes", "Mediadora", "Gerente de engenharia e mentora de carreira", null),
                            new Speaker(null, "Rafael Teixeira", "Painelista", "Tech lead em uma empresa de software da região", null)),
                    new SingleCheckInPolicy(), true));
            actRepo.save(new Activity(null, fair.getId(), "Oficina: Currículo, Portfólio e LinkedIn",
                    "Prática guiada para montar um currículo objetivo, organizar projetos em um portfólio e cuidar do perfil profissional.",
                    new Period(start.plusHours(4), start.plusHours(6)),
                    new ActivityLocation("Laboratório de Informática 02", "Trilha Carreira", 30),
                    ActivityType.WORKSHOP, 30, 0,
                    List.of(new Speaker(null, "Ana Paula Ribeiro", "Instrutora", "Especialista em recrutamento e seleção", null)),
                    new SingleCheckInPolicy(), true));
        }

        String hackTitle = "Hackathon Solidário 24h";
        if (!existing.contains(hackTitle)) {
            LocalDateTime start = base.plusDays(15);
            LocalDateTime end = base.plusDays(16).withHour(12);
            Event hack = eventRepo.save(new Event(null, hackTitle,
                    "Maratona de programação para criar soluções digitais para entidades sociais da cidade. Equipes multidisciplinares, mentoria durante toda a prova e apresentação final para uma banca.",
                    new Period(start, end), EventStatus.PUBLISHED, joilson.get().getId(), null, 120,
                    new MinimumAttendancePercentagePolicy(75.0)));
            hack.configureRegistration(true, false, start.minusDays(2), Event.DEFAULT_TIME_ZONE);
            hack = eventRepo.save(hack);
            actRepo.save(new Activity(null, hack.getId(), "Abertura, desafios e formação de equipes",
                    "Apresentação das entidades parceiras, dos desafios propostos e das regras; formação das equipes.",
                    new Period(start, start.plusHours(2)),
                    new ActivityLocation("Ginásio de Eventos", "Trilha Hackathon", 120),
                    ActivityType.OTHER, 120, 0,
                    List.of(new Speaker(null, "Prof. Joilson Brito", "Coordenador", "Especialista em desenvolvimento Java e POO", null)),
                    new SingleCheckInPolicy(), true));
            actRepo.save(new Activity(null, hack.getId(), "Mentorias e desenvolvimento das soluções",
                    "Plantão de mentores para dúvidas de arquitetura, banco de dados, interface e apresentação do produto.",
                    new Period(start.plusHours(3), start.plusHours(15)),
                    new ActivityLocation("Laboratório de Informática 01", "Trilha Hackathon", 120),
                    ActivityType.WORKSHOP, 120, 0,
                    List.of(new Speaker(null, "Carla Menezes", "Mentora", "Gerente de engenharia e mentora de carreira", null)),
                    new CheckInCheckOutPolicy(), true));
            actRepo.save(new Activity(null, hack.getId(), "Apresentação final e premiação",
                    "Cada equipe apresenta a solução em cinco minutos para a banca e para as entidades parceiras.",
                    new Period(end.minusHours(3), end),
                    new ActivityLocation("Auditório Principal", "Trilha Hackathon", 150),
                    ActivityType.ORAL_PRESENTATION, 150, 0,
                    List.of(new Speaker(null, "Profa. Juliana Braga", "Presidente da banca", "Pesquisadora em Engenharia de Software", null)),
                    new SingleCheckInPolicy(), false));
        }
    }

    private static void register(JdbcRegistrationRepository regRepo, JdbcActivityRepository actRepo,
                                 Event event, User user, List<Activity> activities, LocalDateTime when) {
        Set<Long> ids = new HashSet<>();
        for (Activity activity : activities) {
            if (activity.isRequiresRegistration() && !actRepo.reserveSlot(activity.getId())) {
                throw new IllegalStateException("Sem vagas ao povoar a atividade " + activity.getTitle());
            }
            ids.add(activity.getId());
        }
        regRepo.save(new Registration(null, event.getId(), user.getId(), ids, when, RegistrationStatus.CONFIRMED));
    }

    /**
     * Garante uma conta de administrador para quem sobe o sistema fora do modo de demonstração: sem ela ninguém
     * conseguiria criar organizadores (o cadastro público só cria participantes). Idempotente: se o e-mail já existe,
     * nada é alterado, nem a senha.
     *
     * @return {@code true} se a conta foi criada agora
     */
    public static boolean ensureAdministrator(JdbcUserRepository userRepo, String name, String email, String password) {
        Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter();
        Email normalized = new Email(email);
        if (userRepo.findByEmail(normalized).isPresent()) return false;
        userRepo.save(new User(null, name == null || name.isBlank() ? "Administrador" : name, normalized,
                security.hashPassword(password), UserRole.ADMIN, LocalDateTime.now()));
        log.info("Conta de administrador criada para {}.", email);
        return true;
    }

    private static User ensureUser(JdbcUserRepository userRepo, Pbkdf2SecurityAdapter security, DemoAccount account) {
        return userRepo.findByEmail(new Email(account.email()))
                .orElseGet(() -> userRepo.save(new User(null, account.name(), new Email(account.email()),
                        security.hashPassword(account.password()), account.role(), LocalDateTime.now())));
    }

    /** Contas criadas por versões anteriores usavam outro formato de hash; só essas são atualizadas. */
    private static void upgradeLegacyDemoPasswords(JdbcUserRepository userRepo, Pbkdf2SecurityAdapter security) {
        for (DemoAccount account : DEMO_ACCOUNTS) {
            userRepo.findByEmail(new Email(account.email())).ifPresent(user -> {
                if (security.needsRehash(user.getPasswordHash())) {
                    user.changePassword(security.hashPassword(account.password()));
                    userRepo.save(user);
                }
            });
        }
    }
}
