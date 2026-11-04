# Decisões arquiteturais, refatorações e rastreabilidade de POO

Registro curto exigido pela especificação (seção 11.1, itens 4 e 8; seção 6.1): para cada decisão, o motivo, **uma alternativa
considerada e descartada** e o efeito em modelagem, integração, testes e execução.

## Decisões D-01 a D-08

### D-01 — Interface desktop: Java Swing como cliente HTTP

- **Decisão:** Swing (já incluído no JDK), falando apenas com a API REST; login próprio; uma classe por aba; chamadas HTTP fora da thread de interface.
- **Alternativa descartada:** JavaFX. Exige dependência e configuração de módulos a mais para ser executado em outro computador (RNF de portabilidade) e não traz ganho para o que a disciplina avalia.
- **Efeito:** *modelagem* — o desktop tem modelos de visão próprios e não importa o domínio; *integração* — usa a mesma API do site (RNF-02); *testes* — o cliente é testado contra a API real sem abrir janela, e um teste de fumaça monta a janela quando há display; *execução* — `gradlew run` abre o painel sozinho (`--no-desktop` sobe só a API).

### D-02 — API: Javalin 6 e sessão opaca no servidor

- **Decisão:** Javalin (Jetty embarcado) + Jackson, com roteamento explícito por controllers de área. Autenticação por **token opaco de 256 bits guardado no servidor** (8 h); autorização por perfil e por responsabilidade sobre o evento, sempre no servidor. **Sem Spring Boot**, conforme a orientação do professor.
- **Alternativa descartada:** JWT. Exige segredo de assinatura, não se revoga no logout e não traz benefício com um único servidor.
- **Efeito:** *modelagem* — nada de anotações de framework no domínio; a fiação é manual em `EventosApplication`, o que deixa as dependências visíveis; *integração* — erros padronizados (401/403/404/409/400) por um único mapeador; *testes* — a API sobe em porta aleatória nos testes; *execução* — uma única JVM, sem servidor externo.

### D-03 — Banco: H2 relacional, JDBC puro e migrações próprias

- **Decisão:** H2 em arquivo, acesso por JDBC e SQL explícito; esquema criado/evoluído por scripts versionados (`db/migration`) aplicados por um `SchemaMigrator` de ~100 linhas que registra as versões em `schema_version`. Transações onde há mais de uma escrita; reserva de vaga por `UPDATE` condicional.
- **Alternativa descartada:** JPA/Hibernate (e Flyway). O mapeamento objeto-relacional tende a empurrar o domínio para entidades anêmicas com anotações; e um migrador de uma página resolve a necessidade sem dependência extra.
- **Efeito:** *modelagem* — o domínio não conhece o banco; os repositórios traduzem; *integração* — banco compartilhado só pela API (RN-18); *testes* — os testes usam H2 em memória (`EVENTOS_DB_URL`/propriedade), nunca o arquivo da aplicação; *execução* — qualquer banco anterior é adotado sem perda (testado).

### D-04 — Site público: HTML/CSS/JavaScript puro, cliente da API

- **Decisão:** página servida pelo Javalin (`index.html`, `app.js`, `site.css`), comunicando-se somente por `fetch` com a API; escapa todo dado antes de inseri-lo na página; seletor de contas de demonstração vem de `/api/demo/accounts`.
- **Alternativa descartada:** framework de front-end com *build* (React/Vue). Acrescentaria uma cadeia de ferramentas fora de Java, sem relação com o núcleo avaliado.
- **Efeito:** *integração* — nenhum acesso ao banco; *testes* — o contrato usado pelo `app.js` foi exercitado por script contra o servidor real; *execução* — nada a instalar além do JDK. *Limite declarado:* a aparência em navegador não tem teste automatizado.

### D-05 — QR Code: conteúdo assinado, validade curta e sem duplicidade

- **Decisão:** o conteúdo é `ACT_<atividade>_<instante>_<HMAC-SHA256>` em Base64 seguro para URL; vale 15 minutos; **não contém dado pessoal** (RN-10). A imagem é gerada **localmente** (ZXing), sem enviar o conteúdo a serviço externo. O QR é **por atividade** (projetado na sala); quem registra a presença é o participante **autenticado e inscrito**, identificado pela sessão. Repetições acidentais não duplicam: na política de check-in único a leitura repetida devolve o registro existente; na de entrada e saída, uma segunda leitura em menos de 30 s é ignorada.
- **Alternativa descartada:** QR por participante, lido pelo organizador. Exigiria leitor/câmera na porta e distribuição de um código por pessoa; foge do recorte.
- **Risco aceito:** alguém pode fotografar o QR projetado e repassá-lo durante os 15 minutos. Mitigações: exige sessão e inscrição do próprio participante, validade curta, histórico auditável e **revogação** de presença indevida (RN-12). **Simplificação declarada:** a leitura é feita colando o conteúdo do QR; não há leitura por câmera.
- **Segredo:** `EVENTOS_QR_SECRET`; sem ela, um segredo aleatório por execução (os QR antigos deixam de valer ao reiniciar).

### D-06 — Políticas configuráveis: Strategy e composição, não hierarquias

- **Decisão:**
  - **Frequência** (por atividade): `AttendancePolicy` com `SingleCheckInPolicy`, `CheckInCheckOutPolicy` e `ManualOnlyPolicy`. A política responde a *situação* e *o que fazer numa leitura de QR*.
  - **Certificado** (por evento): `CertificateEligibilityPolicy` com percentual de presença, quantidade mínima e **atividades obrigatórias**, avaliadas sobre um `ParticipationSummary`.
  - **Inscrição:** parâmetros do `Event` (seleção habilitada/obrigatória, prazo, fuso, capacidade) mais `requiresRegistration` e capacidade na `Activity`.
  - **Questionário:** `QuestionType` com comportamento próprio por tipo; política de anonimato no `FeedbackSurvey`.
- **Alternativas descartadas:** (1) subclasses por tipo de evento/atividade — explosão de classes sem diferença de comportamento; (2) `if/switch` por tipo nos serviços — é o que as refatorações R-1 e R-2 removeram; (3) *Specification* composta com E/OU para o certificado — mais flexível, porém desnecessária para o recorte; fica como extensão.
- **Efeito:** uma regra nova é uma classe nova (ou uma constante nova) e não altera serviço nem controller; as políticas são testáveis sem banco. Lista de espera (opcional) **não foi implementada**; `RegistrationStatus.WAITLIST` apenas reserva o conceito.

### D-07 — Padrões de projeto, problema resolvido e alternativa descartada

| Padrão | Onde | Problema que resolve | Efeito no acoplamento | Alternativa descartada |
|---|---|---|---|---|
| Strategy | `AttendancePolicy`, `CertificateEligibilityPolicy`, `QuestionType` | Regras que variam por configuração | Serviços não conhecem as variantes | `instanceof`/`switch` por tipo |
| Factory | `AttendancePolicyFactory`, `CertificatePolicyFactory` | Traduzir chave/descritor persistido em estratégia, em um só lugar | Só a fábrica conhece as classes concretas | `if` espalhado no controller e no repositório |
| Ports and Adapters | `application.ports.*` e `adapters.*` | Trocar banco, PDF, QR, segurança e interface sem tocar nas regras | Dependências apontam para dentro | Serviços chamando JDBC/OpenPDF direto |
| Domain Service | `ConflictValidator` | Política de conflito única, usada pela programação e pela agenda | Mensagens e regra num só lugar | Duplicar a verificação em `Event` e no serviço |
| Value Object | `Period`, `Email`, `ActivityLocation`, `Speaker`, `ParticipationSummary` | Validar ao construir; comparar por valor | Validação acompanha o conceito | Strings e `LocalDateTime` soltos |
| Aggregate Root | `Event` | Garantir invariantes da programação e da inscrição num só ponto | Quem altera a programação passa pelo evento | Serviço manipulando `Activity` diretamente |

Padrões só aparecem onde há variação ou fronteira real; não há classe criada apenas para "ter um padrão".

### D-09 — Envio de e-mail do certificado como porta de saída

- **Decisão:** `EmailSenderPort` (porta) com `SmtpEmailAdapter` (Jakarta Mail) e `UnconfiguredEmailAdapter` quando não há `EVENTOS_SMTP_HOST`. Cada tentativa vira um `CertificateDelivery` (SENT/FAILED, motivo, horário) na tabela `certificate_deliveries`. A falha **não é exceção**: é um resultado registrado, para cumprir "falha não elimina a possibilidade de download" (RF-34). Reenvio em até 60 s após um sucesso não duplica (RNF-10).
- **Alternativa descartada:** chamar o SMTP direto no serviço, ou responder erro 500 quando o e-mail falha. Acoplaria a aplicação à biblioteca e quebraria o fluxo principal por uma falha periférica.
- **Efeito:** os testes de serviço usam um adaptador falso; o adaptador real é testado contra um servidor SMTP em socket que confere destinatário, assunto e o anexo PDF byte a byte; as credenciais vêm só de variáveis de ambiente e nunca aparecem em mensagens de erro.

### D-08 — Testes, refatorações e dados de demonstração

- **Estratégia:** 169 testes JUnit 5 (23 classes), sem depender de interface:
  - **domínio** (sem I/O): regras de `Event`, `Activity`, `Registration`, políticas, questionários, filtros;
  - **casos de uso** com banco H2 real em memória: cenários CA-01 a CA-08, inscrições, frequência, relatórios, certificados;
  - **adaptadores**: JDBC (inclusive concorrência), migrador, segurança e QR (decodificando a imagem gerada);
  - **API por HTTP**: 401/403/404/409/400, rascunhos ocultos, propriedade do evento;
  - **arquitetura**: regra de dependência lida dos imports reais;
  - **desempenho**: 500 participantes e 100 atividades.
- **Refatorações:** seção abaixo.
- **Dados:** o seed de demonstração (8 contas e 1 evento) roda no primeiro uso; `--seed-large` cria a base de carga de 500 participantes e 100 atividades (RNF-08).

## Refatorações relevantes do modelo (ROO-12)

Cada uma com o problema do código anterior e o efeito observável.

| # | Antes (problema) | Alternativa | Depois (efeito) |
|---|---|---|---|
| R-1 | `AttendanceServiceImpl` testava `instanceof` de 3 políticas para decidir o que fazer numa leitura de QR; uma segunda leitura imediata na política de entrada e saída virava uma **saída acidental** | Manter o `if` e acrescentar um caso por política nova | `AttendancePolicy.decideQrMark` devolve registrar / já registrado / recusar. Política nova não altera o serviço. Leituras em menos de 30 s são ignoradas (teste `inOutAccidentalRepeat`) |
| R-2 | Validação (`SurveyQuestion`) e consolidação (`SurveyServiceImpl`) ramificavam por `type ==` em dois lugares; escolha única aceitava qualquer resposta se não houvesse opções | Uma subclasse de pergunta por tipo (mapeamento de persistência mais pesado, sem estado extra) | `QuestionType` valida a definição, valida a resposta e consolida. A escala passou a ter distribuição; escolha única exige 2 opções (`SurveyDomainTest`) |
| R-3 | `RegistrationServiceImpl` (199 linhas) decidia prazo, capacidade e seleção; a regra "inscrição no evento basta" não existia, e então o participante **não conseguia registrar presença nem avaliar** | Corrigir só no serviço | Regras no agregado: `Event.assertCanRegister`, `Event.admits`, `Registration.grantsAccessTo`, `Activity.assertHasAvailableSlot`. Testáveis sem banco; o defeito foi corrigido e coberto (`wholeEventTicketGrantsAccessToEveryActivity`) |
| R-4 | `isEligible(int, int, double, double)` ignorava `totalRequiredActivities`; a "política de obrigatórias" só contava atividades; controller, repositório e cliente desktop usavam `instanceof` para ler a política | Passar mais parâmetros soltos | `ParticipationSummary` (valor) + `CertificatePolicySpec`/`CertificatePolicyFactory`; nova `RequiredActivitiesPolicy` real. Fim dos `instanceof` fora do domínio |
| R-5 | Setters públicos permitiam trocar a política de frequência de uma atividade em andamento, mudando **silenciosamente** presenças já consolidadas (RN-16); `setEventId` era público | Documentar "não faça isso" | Política da atividade imutável; política de certificado congela ao encerrar o evento; `assignToEvent` visível só ao agregado (`ActivityAndRegistrationTest`, `ReportAndCertificateTest`) |
| R-6 | Não havia como corrigir uma presença lançada por engano (RN-12) | Permitir apagar/editar o registro | Registros imutáveis; correção é um registro `REVOCATION` com autor, hora e motivo. A situação ignora o que veio antes da última revogação (`AttendanceRecord.effective`) |
| R-7 | `RestApiController` com 667 linhas, `Map<String,Object>` e *casts* (tipo errado virava erro 500) e entidades de domínio serializadas direto | Dividir só o arquivo | 15 classes pequenas, a maior com 186 linhas (6 controllers por área, `ApiSupport`, `ApiMapper`, mapeador de erros), DTOs de entrada e saída como `record`, erros mapeados para 400/403/404/409 |
| R-8 | Contador de vagas lido, alterado em memória e regravado (`updateEnrollments`): duas inscrições simultâneas podiam passar da capacidade | Só `synchronized` | `UPDATE … WHERE current_enrollments < capacity` (atômico) + serviços serializados + desfazer reservas se uma falhar (`reserveSlotNeverExceedsCapacity`, `concurrentRegistrationsNeverOversell`) |
| R-9 | `UPDATE` de atividade com parâmetros fora de posição (nunca exercitado) | — | Reescrito com preenchimento compartilhado com o `INSERT`; teste de regressão `activityUpdate` |

Refatorações anteriores, já registradas: unificação do domínio (dois modelos concorrentes), autorização no servidor em vez de perfis simulados no navegador, e troca de SHA-256 com sal fixo por PBKDF2 com sal aleatório.

## Rastreabilidade ROO-01 a ROO-12

| ROO | Evidência no código | Teste ou demonstração |
|---|---|---|
| ROO-01 Modelo de domínio | `Event`, `Activity`, `Registration`, `FeedbackSurvey` com regras próprias; vocabulário do glossário | `EventTest`, `ActivityAndRegistrationTest`, `SurveyDomainTest` |
| ROO-02 Encapsulamento | Coleções devolvidas imutáveis; sem setters de estado crítico; transições por métodos (`publish`, `cancel`, `reconfirm`) | `EventTest.terminalStates`, `ActivityAndRegistrationTest.cancelledRegistrationIsFrozen` |
| ROO-03 Objetos de valor | `Period`, `Email`, `ActivityLocation`, `Speaker`, `ParticipationSummary`, `QuestionSummary` — imutáveis e validados | `PeriodTest`, `UserManagementTest` |
| ROO-04 Composição e colaboração | `Event` compõe atividades e política; `Activity` compõe política de frequência; direção das dependências documentada | `ARQUITETURA.md`, `MODELO_DOMINIO.md` |
| ROO-05 Polimorfismo | Frequência, certificado e tipos de pergunta resolvidos por polimorfismo, sem `if` por tipo | `AttendancePolicyTest`, `CertificatePolicyTest`, `SurveyDomainTest` |
| ROO-06 Herança | Usada só onde há relação substituível (políticas implementam interfaces; exceções herdam de `DomainException`). Atividades **não** têm subclasses por tipo | D-06 |
| ROO-07 Interfaces | Portas de entrada e de saída pequenas e orientadas ao cliente; `AttendancePolicy`, `CertificateEligibilityPolicy` | `UserManagementTest` roda os casos de uso sobre um repositório em memória |
| ROO-08 SOLID | Coesão (um controller por área da API, nenhum com mais de 186 linhas), inversão de dependência (portas), aberto/fechado (políticas novas sem mexer em serviços); refatorações R-1 a R-4 | `ArchitectureRulesTest` e a tabela de refatorações |
| ROO-09 Arquitetura hexagonal | Camadas e dependências conforme `ARQUITETURA.md`, verificadas por teste | `ArchitectureRulesTest` (6 regras) |
| ROO-10 Padrões de projeto | Strategy, Factory, Ports and Adapters, Domain Service, Value Object, Aggregate Root — com efeito descrito em D-07 | testes das estratégias e das fábricas |
| ROO-11 Erros do domínio | Hierarquia `DomainException` com mensagens para o usuário; estados inválidos recusados no construtor | `ApiSecurityTest.errorMapping` |
| ROO-12 Testes e refatoração | 169 testes sem depender de interface; 9 refatorações registradas com antes/depois | `./gradlew test` |

## Simplificações declaradas (seção 10.1)

Nenhuma elimina regra central, persistência, integração ou autorização.

| Tema | Simplificação |
|---|---|
| Leitura do QR Code | O participante cola o conteúdo do QR; não há leitura por câmera |
| Sessões | Em memória: reiniciar o servidor encerra as sessões |
| Tipos de atividade | Conjunto fixo (palestra, oficina, apresentação oral, pôster, mesa, outro), com "outro" para os demais casos |
| Cancelamento de inscrição | O prazo configurável é o mesmo da inscrição |
| Fuso horário | Datas são horários locais do fuso do evento; "agora" é calculado nesse fuso |
| Extensões desejáveis/opcionais | Certificado em PDF (RF-32/33), envio por e-mail (RF-34) e declaração de palestrante (RF-35) implementados; lista de espera e mural social **não** implementados |
| Envio de e-mail | Exige servidor SMTP configurado por variáveis de ambiente; validado contra um servidor SMTP falso, **não** contra um provedor real. Sem configuração a tentativa é registrada como falha, nunca como sucesso |
| Execução em servidor | Não é exigida pela especificação; o sistema roda em qualquer host, mas não traz HTTPS (usar proxy reverso) |
