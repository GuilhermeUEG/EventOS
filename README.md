# EventOS — Plataforma Integrada de Gestão de Eventos
**Disciplina:** Programação Orientada a Objetos II (POO II)  
**Projeto Integrador Acadêmico**
**Stack Tecnológica:** Java 21, Gradle, Javalin 6.x, Java Swing, OpenPDF, H2/JDBC, HTML/CSS/JavaScript e JUnit 5.

---

## 1. Visão Geral do Projeto e Arquitetura Hexagonal

O **EventOS** foi construído estritamente sob os princípios de **Clean Architecture / Arquitetura Hexagonal (Ports & Adapters)**, blindando o modelo de domínio de quaisquer dependências de frameworks, interfaces ou bancos de dados.

```
       [ Site Público Web ]                    [ Painel Administrativo Desktop Swing ]
                  │ (HTTP / JSON)                             │ (HTTP / JSON)
                  ▼                                           ▼
         ┌─────────────────────────────────────────────────────────────┐
         │             ADAPTADORES DE ENTRADA (Input Adapters)         │
         │          (RestApiController, SwingDesktopApp)               │
         └──────────────────────────────┬──────────────────────────────┘
                                        │
                                        ▼ (Chama Portas de Entrada)
         ┌─────────────────────────────────────────────────────────────┐
         │              PORTAS DE ENTRADA (Use Cases)                  │
         │ (AuthUseCase, EventUseCase, RegistrationUseCase, etc.)      │
         └──────────────────────────────┬──────────────────────────────┘
                                        │
                                        ▼
         ┌─────────────────────────────────────────────────────────────┐
         │                CAMADA DE APLICAÇÃO (Services)               │
         │   (AuthServiceImpl, EventServiceImpl, SurveyServiceImpl...) │
         └──────────────────────────────┬──────────────────────────────┘
                                        │
                                        ▼ (Manipula Entidades Ricas)
         ┌─────────────────────────────────────────────────────────────┐
         │                     NÚCLEO DO DOMÍNIO                       │
         │   • Entidades: Event, Activity, Registration, FeedbackSurvey│
         │   • Value Objects: Email, Period, ActivityLocation, Status  │
         │   • Strategies: AttendancePolicy, CertificatePolicy         │
         │   • Domain Services: ConflictValidator                      │
         └──────────────────────────────┬──────────────────────────────┘
                                        │
                                        ▼ (Implementa Portas de Saída)
         ┌─────────────────────────────────────────────────────────────┐
         │             ADAPTADORES DE SAÍDA (Output Adapters)          │
         │  • Persistência: JdbcEventRepository, JdbcUserRepository... │
         │  • Segurança: PBKDF2 (senhas), HMAC (QR) e sessão opaca    │
         │  • Emissão de Documentos: OpenPdfGeneratorAdapter (OpenPDF) │
         └─────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                            [ Banco H2 Relacional ]
```

---

## 2. Como Executar o Projeto

### Pré-requisitos
* **JDK 21 ou superior** instalado (OpenJDK 21, 22 ou 23).
* Conexão local para download automático de dependências pelo Gradle Wrapper.

### 2.1. Executar a Aplicação Completa (Servidor Web + Desktop Swing)
Abra o terminal na pasta do projeto e execute:
```bash
./gradlew run
# No Windows (PowerShell/CMD):
.\gradlew.bat run
```

Ao iniciar:
* O banco de dados **H2** é automaticamente configurado e semeado com dados ricos de demonstração.
* A **API REST** e o **Site Público Integrado** estarão disponíveis em: **`http://localhost:7000`**
* A **Interface Administrativa Swing** abrirá automaticamente em janela desktop gráfica local.

Documentação complementar:
* [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md)
* [`docs/API.md`](docs/API.md)

### 2.2. Executar a Suíte de Testes Automatizados (JUnit 5)
```bash
.\gradlew.bat test
```

---

## 3. Credenciais de Demonstração (Seed Demo Data)

Para facilitar a avaliação diante do professor (Cenários CA-01 a CA-08), o sistema inicializa com as seguintes contas pré-configuradas:

| Perfil | Nome | E-mail | Senha | Ações Permitidas |
|---|---|---|---|---|
| **Organizador** | Prof. Marcio Giovane | `marcio.giovane@universidade.edu.br` | `marcio123` | Gestão do evento, frequência, questionários e relatórios. |
| **Participante 1** | Guilherme Barbosa | `guilherme.barbosa@aluno.edu.br` | `gui123` | Inscrito e com presença de demonstração. |
| **Participante 2** | Murilo Mendes | `murilo.mendes@aluno.edu.br` | `murilo123` | Participante inscrito. |
| **Administrador** | Prof. Guiliano Rangel | `guiliano.rangel@universidade.edu.br` | `guiliano123` | Acesso administrativo. |

*(Dica: A barra superior do site público conta com botões de 1 clique para alternar entre qualquer usuário de teste imediatamente!)*

---

## 4. Matriz de Rastreabilidade (Requisitos Funcionais & Critérios de POO)

| ID Requisito | ROO Relacionado | Responsabilidade / Implementação | Evidência / Teste / Demonstração | Status |
|---|---|---|---|---|
| **RF-01, RF-02** | ROO-01, ROO-03 | `User`, `Email`, `AuthUseCase`, `Pbkdf2SecurityAdapter`, `SessionStore` | Cadastro, PBKDF2, sessão opaca e autorização server-side | **Pronto** |
| **RF-03** | ROO-02 | `User.updateProfile()`, `AuthUseCase.updateProfile()` | Edição de dados do usuário preservando invariantes | **Pronto** |
| **RF-04** | ROO-01, ROO-02 | `Event` (Agregado Raiz), `EventStatus`, `EventUseCase` | Ciclo de vida de eventos: Rascunho, Publicado, Cancelado, Encerrado | **Pronto** |
| **RF-05, RF-06** | ROO-01, ROO-03 | `Activity`, `ActivityType`, `ActivityLocation` | Palestras, oficinas, pôsteres e trilhas configuráveis | **Pronto** |
| **RF-07, RF-18** | ROO-04, ROO-11 | `ConflictValidator`, `ConflictException`, `Event.addActivity()` | Detecção e bloqueio de choque de sala e conflito de horário na agenda | **Pronto** |
| **RF-08** | ROO-01, ROO-04 | `Speaker`, `Activity.addSpeaker()` | Identificação de palestrantes com foto opcional (RN-02) | **Pronto** |
| **RF-09, RF-10** | ROO-07, ROO-09 | `RestApiController.listEvents()`, `index.html` | Catálogo público com filtros combináveis (busca, trilha, tipo) | **Pronto** |
| **RF-11, RF-12** | ROO-09 | `RegistrationUseCase.registerForEvent()`, Site Web | Inscrição ponta a ponta a partir da página pública | **Pronto** |
| **RF-13, RF-14** | ROO-02 | `Activity.bookSlot()`, `Activity.hasAvailableSlots()` | Controle rigoroso de vagas e limite de capacidade | **Pronto** |
| **RF-15** | ROO-02 | `Registration.cancel()`, `Activity.releaseSlot()` | Cancelamento com devolução automática de vaga | **Pronto** |
| **RF-16, RF-17** | ROO-01, ROO-03 | `RegistrationUseCase.getParticipantAgenda()` | Agenda personalizada ordenada cronologicamente por horário | **Pronto** |
| **RF-19, RF-23** | ROO-05, ROO-10 | `AttendancePolicy` (Strategy: `SingleCheckIn`, `CheckInCheckOut`, `ManualOnly`) | Cálculo polimórfico de situação de presença (Presente, Parcial, Ausente) | **Pronto** |
| **RF-20, RF-21** | ROO-03, ROO-09 | `AttendanceUseCase.recordQrAttendance()`, `Pbkdf2SecurityAdapter` | Token HMAC temporário sem dados pessoais e check-in idempotente | **Pronto** |
| **RF-22** | ROO-02, ROO-11 | `AttendanceUseCase.recordManualAttendance()`, `AttendanceRecord` | Lançamento manual com identificador do auditor e justificativa (RN-11, RN-12) | **Pronto** |
| **RF-24, RF-25** | ROO-01, ROO-05 | `FeedbackSurvey`, `SurveyQuestion`, `QuestionType` | Questionários com validação polimórfica (Escala 1 a 5, Escolha Única, Texto) | **Pronto** |
| **RF-26, RF-27** | ROO-02, ROO-11 | `SurveyUseCase.canUserEvaluate()`, `SurveyUseCase.submitResponse()` | Bloqueio de avaliação para não-inscritos ou ausentes e 1 resposta por usuário | **Pronto** |
| **RF-28** | ROO-01, ROO-09 | `SurveyUseCase.getConsolidatedResults()`, `SurveyResultsDto` | Relatório consolidado com médias de estrelas, distribuição e comentários | **Pronto** |
| **RF-29, RF-30** | ROO-09 | `ReportUseCase.getEnrolledReport()`, `getAttendanceReport()` | Relatórios operacionais com totalizadores e taxas de presença | **Pronto** |
| **RF-31** | ROO-09, ROO-10 | `ReportUseCase.exportEnrolledCsv()`, `OpenPdfGeneratorAdapter` | Exportação de relatórios em CSV e PDF autônomos | **Pronto** |
| **RF-32, RF-33** | ROO-05, ROO-10 | `CertificateEligibilityPolicy`, `Certificate`, `OpenPdfGeneratorAdapter` | Emissão de certificados em PDF com código de verificação criptográfico | **Pronto** |

---

## 5. Roteiro de Demonstração Ponta a Ponta dos Cenários de Aceitação (CA-01 a CA-08)

1. **CA-01 (Publicação de Evento & Programação):**
   * No **Swing Desktop**, acesse a aba *Gestão de Eventos*, crie um evento de teste e na aba *Atividades & Salas* adicione uma atividade. Clique em *Publicar Evento*. Acesse o **Site Público** (`http://localhost:7000`) e veja o evento refletido instantaneamente.
2. **CA-02 (Cadastro & Inscrição):**
   * No site público, cadastre um novo visitante ou clique em *Entrar* como Lucas Silva. Clique em *Ver Programação & Inscrever* e confirme a inscrição.
3. **CA-03 (Agenda & Detecção de Conflitos):**
   * Na aba *Minha Agenda*, visualize a listagem cronológica. Tente adicionar uma atividade concorrente no mesmo horário para visualizar o alerta de conflito em tempo real.
4. **CA-04 (Frequência por QR Code):**
   * No **Swing**, gere o token de QR Code na aba *Frequência & QR Code*. No site público (aba *Registrar Frequência*), cole o token. A presença será validada e computada pela Strategy configurada.
5. **CA-05 (Lançamento Manual & Auditoria):**
   * No Swing, selecione um participante na tabela e clique em *Lançar Presença Manual*. Digite a justificativa. O registro fica gravado com log do auditor e motivo.
6. **CA-06 (Avaliação de Atividade):**
   * Na agenda do participante presente (ex: Lucas Silva no Keynote), clique em *Avaliar Atividade*. Responda às notas e comentários. Em seguida, acesse a aba *Avaliações & Métricas* no Swing e veja as médias calculadas.
7. **CA-07 (Relatórios Operacionais em CSV e PDF):**
   * Na aba *Relatórios & Certificados* do Swing, clique em *Exportar CSV* e *Exportar PDF*. Abra os arquivos gerados.
8. **CA-08 (Emissão e Validação de Certificado):**
   * No site público, acesse a aba *Certificados* e solicite a emissão do PDF. Copie o código verificador e cole na aba *Validar Certificado* para atestar a autenticidade.

---

## 6. Registro de Decisões Arquiteturais (D-01 a D-08)

* **D-01 (Interface Desktop):** Java Swing estruturado em abas, atuando como cliente HTTP autenticado da mesma API consumida pelo site.
* **D-02 (API REST & Autenticação):** Javalin 6.x/Jetty, Jackson, senhas PBKDF2 com salt aleatório e sessões opacas mantidas no servidor por oito horas. Operações protegidas validam perfil e titularidade no backend.
* **D-03 (Banco Relacional & Migração):** H2 relacional com esquema DDL automático na inicialização e transações ACID gerenciadas via PreparedStatements.
* **D-04 (Site Público):** Single Page Application em HTML5/CSS3/JavaScript moderna e responsiva servida diretamente pelo Javalin, comunicando-se exclusivamente via REST API.
* **D-05 (Estratégia de QR Code):** Payload `ACT_{id}_{timestamp}_{assinatura}` assinado por HMAC, Base64 URL-safe e validade de 15 minutos, sem dados pessoais.
* **D-06 (Políticas Configuráveis):** Padrão *Strategy* aplicado a `AttendancePolicy` e `CertificateEligibilityPolicy`, permitindo variar os cálculos sem condicionais espalhadas pelo código.
* **D-07 (Padrões de Projeto):**
  * *Strategy / Policy Pattern:* Variação de cálculo de presença e certificados.
  * *Domain Service / Specification Pattern:* `ConflictValidator` para isolar checagem de salas e horários.
  * *Adapter Pattern:* `OpenPdfGeneratorAdapter` e repositórios JDBC desacoplando bibliotecas externas do domínio.
  * *Factory Pattern:* `AttendancePolicyFactory` para instanciação limpa das políticas.
* **D-08 (Testes e Qualidade):** Testes JUnit 5 de domínio, segurança, sessões e fluxo integrado dos principais casos de uso. Execute com `.\gradlew.bat test`.
