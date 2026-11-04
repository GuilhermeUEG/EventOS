# EventOS — Plataforma Integrada de Gestão de Eventos

**Disciplina:** Programação Orientada a Objetos II (POO II) · **Projeto Integrador**
**Stack:** Java 21 · Gradle · Javalin 6 (Jetty) · Java Swing · H2 + JDBC · OpenPDF · ZXing · JUnit 5 · HTML/CSS/JavaScript.
**Sem Spring Boot** (orientação do professor): a ligação entre as partes é manual, no *composition root*.

Plataforma configurável para cadastrar eventos e atividades, publicar uma página web, receber inscrições, montar a agenda do participante,
registrar frequência por QR Code (com alternativa manual), coletar avaliações e emitir relatórios e certificados. O **desktop** e o **site**
usam a **mesma API REST** e o **mesmo banco**.

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/ARQUITETURA.md](docs/ARQUITETURA.md) | Diagrama hexagonal, pacotes, onde ficam as regras, segurança, banco |
| [docs/MODELO_DOMINIO.md](docs/MODELO_DOMINIO.md) | Glossário, diagrama de classes, mapa de responsabilidades, invariantes |
| [docs/DECISOES.md](docs/DECISOES.md) | Decisões D-01 a D-08 (com alternativas), 9 refatorações antes/depois, rastreabilidade ROO-01 a ROO-12, simplificações |
| [docs/API.md](docs/API.md) | Todos os endpoints, perfis, corpos e códigos de erro |
| [DocumentacaoAux.md](DocumentacaoAux.md) | Escolhas de ferramentas (Gradle, remoção do Spring Boot, JDK) |

## 1. Como executar

### Pré-requisitos

- **JDK 21 ou superior.** Confira com `java -version` **e** `echo %JAVA_HOME%` (Windows) / `echo $JAVA_HOME` (Linux/macOS): o `gradlew` usa o `JAVA_HOME`.
  Se ele apontar para um JDK antigo (por exemplo, 8), o build falha com *"invalid source release: 21"* ou *"requires JVM 17+"*; ajuste a variável para um JDK 21+.
- Internet na primeira execução (o Gradle baixa o wrapper e as dependências).

### Subir a aplicação completa

```bash
./gradlew run            # Linux/macOS
.\gradlew.bat run        # Windows
```

Ao iniciar: o banco H2 é criado/migrado, a **base de demonstração** é povoada, a API e o site ficam em **http://localhost:7000** e o
**painel desktop** abre (pede login). Em ambiente sem interface gráfica sobe só a API e o site.

| Opção | Efeito |
|---|---|
| `./gradlew run --args="--no-desktop"` | Sobe só API e site |
| `./gradlew run --args="--seed-large"` | Cria também a base de carga: 500 participantes e 100 atividades (RNF-08) |
| `EVENTOS_PORT` | Porta HTTP (padrão 7000) |
| `EVENTOS_DB_URL` | URL JDBC do H2 (padrão: arquivo `./eventosdb`) |
| `EVENTOS_DEMO_MODE=false` | Não povoa o seed e desliga o seletor de contas de demonstração |
| `EVENTOS_QR_SECRET` | Segredo (≥ 16 caracteres) do HMAC dos QR Codes; sem ele, um segredo aleatório por execução |
| `EVENTOS_ADMIN_EMAIL`, `EVENTOS_ADMIN_PASSWORD` (e `EVENTOS_ADMIN_NAME`) | Cria uma conta de **administrador** se o e-mail ainda não existir (idempotente; não altera a senha de conta existente). Necessário num servidor sem modo demonstração, para poder criar organizadores |
| `EVENTOS_PUBLIC_URL` | Endereço público do sistema (ex.: `https://eventos.escola.edu.br`), usado no link de validação impresso nos certificados; padrão `http://localhost:<porta>` |
| `EVENTOS_SMTP_HOST` (+ `_PORT`, `_USER`, `_PASSWORD`, `_FROM`, `_SECURITY`) | Servidor SMTP para enviar certificados por e-mail (RF-34). `_PORT` padrão 587; `_SECURITY` = `STARTTLS` (padrão), `SSL` ou `NONE`. Sem `_HOST`, o envio é registrado como falha explicada e o download continua disponível |

Para recomeçar do zero, apague os arquivos `eventosdb*` da pasta do projeto.

### Executar em um servidor (opcional)

A especificação **não exige** hospedagem (a demonstração é "em ambiente preparado pela equipe", e a infraestrutura de produção está fora do escopo, seção 2.3), mas o sistema já funciona em qualquer máquina acessível pela rede:

```bash
EVENTOS_PORT=8080 EVENTOS_PUBLIC_URL=https://eventos.exemplo.edu.br \
EVENTOS_QR_SECRET=<segredo-longo> EVENTOS_DEMO_MODE=false \
EVENTOS_ADMIN_EMAIL=admin@exemplo.edu.br EVENTOS_ADMIN_PASSWORD=<senha-forte> \
./gradlew run --args="--no-desktop"
```

- A aplicação escuta em todas as interfaces; o site em `/` e a API em `/api` saem da mesma porta, então basta liberá-la.
- **Não há HTTPS embutido**: em uso real, coloque um proxy reverso com TLS (Nginx, Caddy…) na frente.
- Com `EVENTOS_DEMO_MODE=false` não há contas nem dados de exemplo: `EVENTOS_ADMIN_EMAIL`/`EVENTOS_ADMIN_PASSWORD` criam o primeiro administrador, que então cadastra os organizadores. Para uma demonstração em servidor, deixe o modo demonstração ligado.
- O desktop se conecta à API da própria máquina; o acesso remoto de organizadores é feito pelo site, cujo *Painel do organizador* também cria, edita, publica, encerra e cancela eventos (os mesmos recursos da aba *Eventos* do desktop).

### Executar os testes

```bash
./gradlew test           # .\gradlew.bat test no Windows
```

169 testes automatizados (23 classes) sem depender de interface, usando H2 em **memória** (não tocam o banco da aplicação). Relatório HTML em
`build/reports/tests/test/index.html`. O teste que monta a janela Swing é ignorado automaticamente quando não há display.

## 2. Credenciais de demonstração

Criadas no primeiro uso (todas com PBKDF2; as senhas abaixo valem **somente** para demonstração):

| Perfil | Nome | E-mail | Senha |
|---|---|---|---|
| Organizador (dono do evento de demonstração) | Prof. Marcio Giovane | `marcio.giovane@universidade.edu.br` | `marcio123` |
| Organizador | Prof. Joilson Brito | `joilson.brito@universidade.edu.br` | `joilson123` |
| Organizador | Profa. Juliana Braga | `juliana.braga@universidade.edu.br` | `juliana123` |
| Administrador | Prof. Guiliano Rangel | `guiliano.rangel@universidade.edu.br` | `guiliano123` |
| Participante | Guilherme Barbosa | `guilherme.barbosa@aluno.edu.br` | `gui123` |
| Participante | Murilo Mendes | `murilo.mendes@aluno.edu.br` | `murilo123` |
| Participante | Danielly Mendes | `danielly.mendes@aluno.edu.br` | `dani123` |
| Participante | Samir Santana | `samir.santana@aluno.edu.br` | `samir123` |

O menu da conta no site tem atalhos de 1 clique para essas contas (lidos de `/api/demo/accounts`, somente no modo demonstração).
Com `--seed-large`, os 500 participantes são `participante001@carga.demo` … `participante500@carga.demo`, senha `carga123`.

**Eventos de vitrine.** Além do simpósio, a demonstração cria a *Feira de Carreiras em Tecnologia* (organizadora: Profa. Juliana Braga) e o *Hackathon Solidário 24h* (organizador: Prof. Joilson Brito). Eles fazem a página inicial e a busca terem mais de um evento e mostram que a plataforma não se restringe a eventos acadêmicos (seção 1.1 da especificação). São criados uma única vez, inclusive em uma base de demonstração já existente.

**Estado do evento de demonstração** ("XV Simpósio de Tecnologia da Informação", começa no dia seguinte às 08:00, inscrições abertas):
3 atividades — Palestra Magna (check-in único), Workshop (**entrada e saída**) e Sessão Técnica (sem controle de vagas).
Guilherme tem presença na palestra e no workshop (**elegível** ao certificado); Murilo e Danielly estão inscritos sem presença
(**inelegíveis**, para mostrar a justificativa). A palestra tem um questionário (escala, escolha única e comentário).

## 3. Roteiro dos cenários de aceitação (CA-01 a CA-08)

Abra o painel desktop com o organizador e o site em `http://localhost:7000` em outra janela.

1. **CA-01 — Publicação.** Desktop → *Eventos*: **Novo evento** (rascunho) → *Atividades e Salas*: **Adicionar atividade** com palestrantes (uma sala ocupada no mesmo horário é recusada) → *Eventos*: **Publicar**. (O mesmo fluxo existe no site: *Painel do organizador* → **Novo evento**, cadastrar atividade, **Publicar**.) No site, *Programação* passa a exibir os mesmos dados; antes de publicar o rascunho não aparece para visitantes.
2. **CA-02 — Inscrição.** No site, *Entrar ou criar conta* (cadastro cria participante) → escolher o evento → inscrever-se nele e em atividades → a confirmação fica em *Minha Agenda*.
3. **CA-03 — Agenda.** Em *Minha Agenda*, as atividades aparecem em ordem cronológica; tentar escolher outra atividade no mesmo horário mostra o conflito explicado.
4. **CA-04 — Frequência por QR.** Desktop → *Frequência e QR Code*: escolher a atividade e **Gerar QR Code** (a imagem é gerada localmente; o conteúdo aparece abaixo). No site, o participante inscrito usa *Registrar Presença*, **cola o conteúdo** do QR e vê a situação calculada pela política da atividade. Ler de novo não duplica o registro.
5. **CA-05 — Manual e correção.** Na mesma aba, selecionar o participante e usar **Lançar presença manual…** (justificativa obrigatória); **Revogar presença…** corrige um lançamento errado. **Histórico de registros…** mostra todos os registros, com autor, horário e motivo.
6. **CA-06 — Avaliação.** No site, Guilherme (presente na palestra) abre a avaliação: o aviso de identificação/anonimato aparece antes do envio. Murilo (sem presença) é impedido e vê o motivo. No desktop, *Avaliações e Métricas*: **Criar questionário…** (construtor) e resultados consolidados.
7. **CA-07 — Relatórios.** Desktop → *Relatórios e Certificados*: **Ver inscritos** / **Ver frequência** e exportar **Inscritos (CSV/PDF)** e **Frequência (CSV/PDF)** (frequência por participante e atividade); abra o arquivo salvo fora do sistema.
8. **CA-08 — Certificado (desejável).** No site, *Certificados & Validação*: Guilherme emite e baixa o PDF, pode **Enviar por e-mail** (o resultado e o histórico aparecem no cartão) e valida o código; Murilo vê a justificativa da inelegibilidade. O organizador define a política (percentual, quantidade mínima ou atividades obrigatórias) em **Critério de certificado…** e pode conferir a situação de um participante com **Consultar elegibilidade**.

## 4. Matriz de rastreabilidade (seção 11.3)

Status: **Pronto** = implementado, integrado (API + banco reais) e testado. Os testes estão em [`src/test/java/com/eventos`](src/test/java/com/eventos).

| RF | ROO | Responsabilidade / implementação | Teste ou demonstração | Situação |
|---|---|---|---|---|
| RF-01 Cadastro | 01, 03 | `AuthServiceImpl.register`, `Email`, `User` (cadastro público sempre cria participante) | `UserManagementTest`, `ApiSecurityTest` | Pronto |
| RF-02 Autenticação e permissões | 02, 08 | `AuthServiceImpl.login`, `InMemorySessionStore`, `AuthorizationHandler`, `ApiSupport.requireEventManager` | `ApiSecurityTest` (401/403, posse do evento) | Pronto |
| RF-03 Dados do participante | 02 | `AuthServiceImpl.updateProfile/changePassword`, `PUT /users/{id}` | `UserManagementTest`, `ApiSecurityTest` | Pronto |
| RF-04 Eventos | 01, 02 | `Event` (ciclo de vida), `EventServiceImpl`, `EventController` | `EventTest`, `RegistrationRulesTest` | Pronto |
| RF-05 Tipos de atividade | 01, 03, 06 | `Activity`, `ActivityType` (inclui "outro"), sem subclasses por tipo | `PersistenceAdaptersTest` | Pronto |
| RF-06 Trilhas e espaços | 01, 03 | `ActivityLocation`, `ActivityFilter` | `ActivityFilterTest` | Pronto |
| RF-07 Horários, locais e conflitos | 04, 11 | `Event.addActivity` + `ConflictValidator` (conflito de sala **bloqueado**) | `EventTest`, `ApiSecurityTest` (409) | Pronto |
| RF-08 Pessoas e papéis | 01, 03 | `Speaker` (foto opcional), `Activity.addSpeaker` | `ActivityAndRegistrationTest`, `PersistenceAdaptersTest` | Pronto |
| RF-09 Consulta com filtros | 03, 04 | `ActivityFilter` (data, horário, trilha, tipo, sala, texto), `GET /events/{id}/activities` | `ActivityFilterTest`, `LargeDatasetTest` | Pronto |
| RF-10 Página web | 09 | `EventController.listEvents`, `index.html` + `app.js`; rascunhos ocultos | `ApiSecurityTest.draftsAreHiddenFromVisitors` | Pronto |
| RF-11 Programação e pessoas no site | 09 | `GET /events/{id}`, programação com palestrantes; página inicial com eventos, atividades e pessoas em destaque (`GET /home`) | `ApiSecurityTest`, `HomePageTest`, script de contrato do site | Pronto |
| RF-12 Cadastro e inscrição pelo site | 09 | `AuthController`, `RegistrationController`, `RegistrationServiceImpl` | `EndToEndScenarioTest` | Pronto |
| RF-13 Regra de inscrição configurável | 04, 05 | `Event.configureRegistration/assertCanRegister`, `Activity.requiresRegistration` | `EventTest`, `RegistrationRulesTest.configurableRegistrationRules` | Pronto |
| RF-14 Controle de vagas | 02, 08 | `Activity.bookSlot`, `ActivityRepository.reserveSlot` (atômico) | `RegistrationRulesTest`, `PersistenceAdaptersTest` (concorrência) | Pronto |
| RF-15 Cancelamento | 02 | `Registration.cancel`, `Event.assertCanCancelRegistration` | `RegistrationRulesTest.cancelReleasesSlots` | Pronto |
| RF-16 Seleção de atividades | 01, 04 | `RegistrationServiceImpl.addActivityToRegistration` | `RegistrationRulesTest` | Pronto |
| RF-17 Agenda cronológica | 01 | `getParticipantAgenda` | `RegistrationRulesTest.agendaConflict` | Pronto |
| RF-18 Conflitos na agenda | 04, 11 | `ConflictValidator.validateParticipantAgendaConflict` (bloqueia) | `RegistrationRulesTest.agendaConflict` | Pronto |
| RF-19 Critério de presença | 05, 07, 10 | `AttendancePolicy` (check-in único, entrada e saída, manual), `AttendancePolicyFactory` | `AttendancePolicyTest` | Pronto |
| RF-20 Gerar QR Code | 07, 09 | `Pbkdf2SecurityAdapter` (HMAC), `ZxingQrCodeAdapter` (portas) | `SecurityAndQrAdaptersTest` (decodifica a imagem) | Pronto |
| RF-21 Registrar por QR | 05, 11 | `AttendanceServiceImpl.recordQrAttendance` + `AttendancePolicy.decideQrMark` | `AttendanceRulesTest` | Pronto |
| RF-22 Lançamento manual | 02, 11 | `recordManualAttendance`, `revokeAttendance`, `AttendanceRecord` (imutável) | `AttendanceRulesTest` | Pronto |
| RF-23 Situação de presença | 05 | `Activity.evaluateAttendance` → política | `AttendancePolicyTest`, `AttendanceRulesTest` | Pronto |
| RF-24 Questionário configurável | 01, 05 | `FeedbackSurvey`, `SurveyQuestion`; construtor no desktop e no site | `SurveyDomainTest`, `DesktopApiClientIntegrationTest` | Pronto |
| RF-25 Tipos de resposta | 05 | `QuestionType` (texto, escolha única, escala) | `SurveyDomainTest` | Pronto |
| RF-26 Avaliar só inscrito com presença | 02, 11 | `FeedbackSurvey.checkEligibility` (com motivo) | `AttendanceRulesTest`, `EndToEndScenarioTest` | Pronto |
| RF-27 Uma resposta por participante | 02, 11 | `SurveyServiceImpl.submitResponse` + `UNIQUE` no banco | `EndToEndScenarioTest` | Pronto |
| RF-28 Consolidar resultados | 01, 05 | `FeedbackSurvey.summarize`, `SurveyResultsDto` | `SurveyDomainTest`, `EndToEndScenarioTest` | Pronto |
| RF-29 Relatório de inscritos | 09 | `ReportServiceImpl.getEnrolledReport` | `ReportAndCertificateTest` | Pronto |
| RF-30 Relatório de frequência | 09 | `getAttendanceReport` (por participante e atividade) | `ReportAndCertificateTest`, `LargeDatasetTest` | Pronto |
| RF-31 Exportação portátil | 09, 10 | CSV e PDF (`ReportServiceImpl`, `OpenPdfGeneratorAdapter`) | `ReportAndCertificateTest` | Pronto |
| RF-32 Critério de certificado (D) | 05, 10 | `CertificateEligibilityPolicy` (3 estratégias) | `CertificatePolicyTest`, `ReportAndCertificateTest` | Pronto |
| RF-33 Certificado em PDF (D) | 09 | `CertificateServiceImpl`, `OpenPdfGeneratorAdapter` (código verificável) | `EndToEndScenarioTest`, `SecurityAndQrAdaptersTest` | Pronto |
| RF-34 Envio por e-mail (D) | 07, 09, 10 | `CertificateServiceImpl.sendCertificateByEmail`, porta `EmailSenderPort`, `SmtpEmailAdapter` (Jakarta Mail), `CertificateDelivery` (cada tentativa é gravada); falha vira registro `FAILED` e não impede o download; reenvio em 60 s não duplica | `ReportAndCertificateTest`, `SmtpEmailAdapterTest` (servidor SMTP falso, anexo conferido byte a byte), `ApiSecurityTest` | Pronto (precisa de `EVENTOS_SMTP_HOST`) |
| RF-35 Declaração de palestrante (D) | 09 | `CertificateServiceImpl.exportSpeakerDeclarationPdf`; botão "Declaração" ao lado de cada palestrante no site e "Declaração de palestrante…" no desktop | `ReportAndCertificateTest`, `ApiSecurityTest`, `DesktopApiClientIntegrationTest` | Pronto |
| RF-36 Interação social (P) | — | — | — | **Não implementado** |

| RN / RNF | Onde está atendido |
|---|---|
| RN-01 e-mail único · RN-02 foto opcional | `Email`, `UNIQUE` no banco · `Speaker.photoUrl` |
| RN-04 estado controla visibilidade e inscrição | `Event.isPubliclyVisible/isEnrollmentOpen` |
| RN-07 conflitos com política uniforme | `ConflictValidator`: sempre bloqueia, com mensagem que cita a colisão |
| RN-09 frequência por atividade · RN-10 QR sem dado pessoal | `Activity.attendancePolicy` · HMAC sem identificadores pessoais |
| RN-11/12 autoria e rastreabilidade | `AttendanceRecord.recordedBy/notes`, revogação auditável |
| RN-14 política de anonimato informada antes do envio | `FeedbackSurvey.identificationNotice`, exibido na avaliação |
| RN-16 histórico não muda em silêncio | política de frequência imutável; certificado emitido persiste; política congela ao encerrar |
| RN-19 fuso do evento | `Event.timeZone/now()`, usado em inscrição e frequência |
| RNF-05/06 hash e autorização no servidor | PBKDF2; `AuthorizationHandler` |
| RNF-08 desempenho (500 participantes, 100 atividades) | `LargeDatasetTest`: relatórios e consultas em dezenas/centenas de ms |
| RNF-10 sem duplicidade | leitura de QR idempotente; vaga reservada atomicamente |
| RNF-12 observabilidade | SLF4J, sem senhas nem tokens nos logs |

## 5. Limitações conhecidas

- A leitura do QR Code é feita **colando o conteúdo**; não há leitura por câmera (D-05).
- A lista de espera e o mural social (extensões opcionais) não foram implementados.
- O envio de certificado por e-mail só funciona com um servidor SMTP configurado; **não foi testado contra um provedor real** (o adaptador foi validado contra um servidor SMTP falso). Sem configuração, a tentativa é registrada como falha explicada.
- Sessões ficam em memória (reiniciar o servidor encerra todas).
- A interface gráfica (desktop e site) foi validada por testes do cliente/da API, testes de fumaça e inspeção de código; a aparência em tela não tem teste automatizado.
- Veja a lista completa de simplificações em [docs/DECISOES.md](docs/DECISOES.md#simplificações-declaradas-seção-101).

## 6. Estrutura do repositório

```
src/main/java/com/eventos/
  domain/        modelo, políticas (Strategy), serviço de domínio, exceções
  application/   portas de entrada e saída, serviços de caso de uso, DTOs
  adapters/
    input/rest/      API REST (controllers por área, DTOs, autorização)
    input/desktop/   cliente Swing da API
    output/          persistence (JDBC, migrador, seed) · security · pdf · qr
  EventosApplication.java   composition root
src/main/resources/  public/ (site) · db/migration/ (esquema versionado)
src/test/java/       testes (domínio, casos de uso, adaptadores, API, arquitetura, desempenho)
docs/                arquitetura, modelo, decisões, API
```
