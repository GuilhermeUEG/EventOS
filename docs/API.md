# API REST do EventOS

Base local: `http://localhost:7000/api` (porta alterável por `EVENTOS_PORT`). O site público e o desktop consomem **exatamente esta API**
(RNF-02); nenhum dos dois acessa o banco. Código: [`adapters/input/rest`](../src/main/java/com/eventos/adapters/input/rest).

## Convenções

| Item | Regra |
|---|---|
| Formato | JSON UTF-8. Datas em ISO-8601 (`2026-10-05T08:00:00`); nas entradas também são aceitos `dd/MM/yyyy HH:mm` e `yyyy-MM-dd HH:mm`. As datas são horários locais do fuso do evento (`timeZone`, RN-19). |
| Autenticação | `Authorization: Bearer <token>` (token opaco devolvido por login/cadastro, válido por 8 h). |
| Erros | Sempre `{"error": "mensagem compreensível"}`. Nunca expõem detalhes internos. |
| `400` | Dado inválido ou regra de negócio violada (inclui JSON malformado e valor de enum desconhecido). |
| `401` | Sem sessão válida. |
| `403` | Sessão válida, mas sem permissão para a operação. |
| `404` | Recurso inexistente. |
| `409` | Conflito (sala ocupada no mesmo horário, conflito de agenda). |
| `500` | Falha inesperada; a mensagem é genérica e o detalhe vai para o log. |

Perfis: `ADMIN`, `ORGANIZER`, `PARTICIPANT`. **"Responsável"** = organizador dono do evento (`organizerId`) ou administrador (RN-17).
A autorização é verificada no servidor em todas as rotas protegidas (RNF-06).

## Autenticação e usuários

| Método e rota | Quem | Descrição |
|---|---|---|
| `POST /auth/register` `{name,email,password}` | público | Cria **sempre** um participante (campo `role` é ignorado). Devolve `201 {token, expiresAt, user}`. E-mail duplicado → 400. Senha: 6 a 128 caracteres. |
| `POST /auth/login` `{email,password}` | público | Devolve `{token, expiresAt, user}`. Credenciais inválidas → 401 (mesma mensagem para e-mail inexistente e senha errada). |
| `POST /auth/logout` | autenticado | Invalida o token (204). |
| `GET /auth/me` | autenticado | Usuário da sessão. |
| `GET /users` | ADMIN | Lista usuários. |
| `POST /users` `{name,email,password,role}` | ADMIN | Cria usuário com qualquer perfil (é assim que nascem organizadores). |
| `GET /users/{id}` | o próprio, organizador ou admin | Dados básicos. |
| `PUT /users/{id}` `{name}` | o próprio, organizador ou admin | Atualiza o nome (RF-03). |
| `PUT /users/{id}/password` `{currentPassword,newPassword}` | somente o próprio | Troca de senha; senha atual errada → 400. |
| `PUT /users/{id}/role` `{role}` | ADMIN | Altera perfil. O último administrador não pode ser rebaixado. |

`user` = `{id, name, email, role, roleName, createdAt}`. O hash da senha nunca é exposto.

## Eventos e programação

| Método e rota | Quem | Descrição |
|---|---|---|
| `GET /events?search=&track=&type=&status=&mine=&limit=&offset=` | público | Sem sessão (ou como participante) só devolve eventos `PUBLISHED`/`IN_PROGRESS`, **mesmo que `status=DRAFT` seja pedido** (RN-04). Organizador vê também os próprios eventos; admin vê todos. **Cancelados ficam fora da listagem** a menos que se peça `status=CANCELLED`. `search` procura no título e na descrição. `mine=true` (só organização) limita aos eventos de que o usuário é responsável. `limit` (máx. 500) e `offset` paginam; o total antes da paginação vai no cabeçalho `X-Total-Count`. Sem `limit` a lista vem inteira (é o que o desktop usa). Ordem: início mais recente primeiro. |
| `GET /home` | público | Conteúdo da página inicial: `publicEvents`, `upcomingEvents`, até 6 `events` em destaque (os que ainda não terminaram, do mais próximo; depois os passados, do mais recente), até 6 `activities` (próximas da programação) e até 6 `speakers` (cada pessoa uma vez; `photoUrl` é opcional). Só expõe eventos publicamente visíveis (RN-04). |
| `GET /events/{id}` | público se publicado; responsável caso contrário | Evento com `activities`. |
| `POST /events` | ORGANIZER, ADMIN | Cria rascunho. Corpo abaixo. |
| `PUT /events/{id}` | responsável | Altera título, descrição, `start`, `end`, `maxCapacity`, `activitySelectionEnabled`, `activitySelectionRequired`, `registrationDeadline`, `timeZone` (todos opcionais). O novo período precisa comportar as atividades já cadastradas. |
| `PUT /events/{id}/certificate-policy` | responsável | Troca a política de certificado (congela ao encerrar o evento). |
| `POST /events/{id}/publish` · `/cancel` · `/finish` | responsável | Ciclo de vida. Publicar exige ao menos uma atividade. |
| `GET /events/{id}/activities?date=&from=&to=&track=&type=&room=&search=` | público se publicado | Programação com **filtros combináveis** (RF-09): data (`yyyy-MM-dd`), início a partir de/até (`HH:mm`), trilha/espaço, tipo, sala e busca livre (título, descrição, sala, trilha, palestrantes). Ordem cronológica. |
| `POST /events/{id}/activities` | responsável | Cria atividade. Conflito de sala/horário → 409; fora do período do evento → 400. |
| `GET /activities/{id}` | público se o evento for publicado | Atividade. |

Corpo de `POST /events`:

```json
{
  "title": "Semana de POO", "description": "…",
  "start": "2026-10-05T08:00", "end": "2026-10-06T18:00",
  "maxCapacity": 200,
  "activitySelectionEnabled": true, "activitySelectionRequired": false,
  "registrationDeadline": "2026-10-05T08:00", "timeZone": "America/Sao_Paulo",
  "certificatePolicy": { "type": "MIN_PERCENTAGE", "percentage": 75 }
}
```

`certificatePolicy.type`: `MIN_PERCENTAGE` (`percentage`), `MIN_ACTIVITIES` (`minActivities`) ou `REQUIRED_ACTIVITIES` (`activityIds`, que devem ser atividades do evento).

Corpo de `POST /events/{id}/activities`:

```json
{
  "title": "Oficina de Strategy", "description": "…",
  "start": "2026-10-05T10:00", "end": "2026-10-05T12:00",
  "room": "Lab 1", "track": "Desenvolvimento", "capacity": 40,
  "type": "WORKSHOP",
  "attendancePolicy": "CHECKIN_CHECKOUT",
  "requiresRegistration": true,
  "speakers": [ { "name": "Ana", "role": "Instrutora", "bio": "…", "photoUrl": null } ]
}
```

`type`: `LECTURE`, `WORKSHOP`, `ORAL_PRESENTATION`, `POSTER`, `ROUND_TABLE`, `OTHER`. `attendancePolicy`: `SINGLE_CHECKIN`, `CHECKIN_CHECKOUT`, `MANUAL_ONLY`.
`requiresRegistration` liga o controle de vagas da atividade. Foto do palestrante é opcional (RN-02).

## Inscrição e agenda

O participante da operação **sempre** vem da sessão; um `userId` enviado no corpo é ignorado.

| Método e rota | Quem | Descrição |
|---|---|---|
| `POST /registrations` `{eventId, activityIds[]}` | autenticado | Inscreve-se no evento (e nas atividades escolhidas). Valida estado do evento, prazo, capacidade, seleção obrigatória/desabilitada, conflitos e vagas. Tudo-ou-nada: nenhuma vaga fica reservada se algo falhar. `201`. |
| `POST /registrations/{eventId}/activities/{activityId}` | autenticado | Escolhe uma atividade (conflito → 409). |
| `DELETE /registrations/{eventId}/activities/{activityId}` | autenticado | Remove a escolha e devolve a vaga. |
| `POST /registrations/{eventId}/cancel` | autenticado | Cancela dentro do prazo e libera as vagas. |
| `GET /registrations/event/{eventId}` | responsável | Inscrições do evento. |
| `GET /registrations/user/{userId}` | o próprio ou equipe | Inscrições do usuário. |
| `GET /registrations/agenda/{userId}/{eventId}` | o próprio ou equipe | Agenda pessoal em ordem cronológica (com data, horário e local). Se o evento não permite escolher atividades, é a programação inteira. |

## Frequência

| Método e rota | Quem | Descrição |
|---|---|---|
| `GET /attendance/qr/generate/{activityId}` | responsável | `{activityId, qrToken, qrImage, validForSeconds}`. `qrImage` é um PNG em *data URI*, gerado localmente. O conteúdo é assinado (HMAC) e não contém dado pessoal (RN-10). |
| `POST /attendance/qr/scan` `{qrToken}` | autenticado | Registra a presença do participante da sessão. `201` se criou, `200` se a marcação já existia (repetição acidental não duplica). Devolve `{record, alreadyRecorded, status, statusName}`. |
| `POST /attendance/manual` `{activityId,userId,type,notes}` | responsável | Lançamento manual; `notes` (justificativa) é obrigatório; `type`: `CHECK_IN`, `CHECK_OUT` ou `MANUAL_ENTRY` (padrão). O participante precisa estar inscrito na atividade. |
| `POST /attendance/revoke` `{activityId,userId,notes}` | responsável | **Correção**: invalida as marcações vigentes do participante, sem apagar nada; autor, horário e motivo ficam registrados (RN-12). |
| `GET /attendance/activity/{activityId}` | responsável | Histórico completo (inclui revogações). |
| `GET /attendance/activity/{activityId}/overview` | responsável | Situação de cada inscrito na atividade: `PRESENT`, `PARTIAL` ou `ABSENT`. |

## Avaliações

| Método e rota | Quem | Descrição |
|---|---|---|
| `POST /surveys` `{activityId,title,anonymous,questions[]}` | responsável | Cria o questionário da atividade (um por atividade). Pergunta: `{questionText,type,options,required}`, com `type` = `TEXT`, `SINGLE_CHOICE` (mínimo de 2 opções) ou `RATING_SCALE` (1 a 5). `anonymous` define a política de identificação (RN-14). |
| `GET /surveys/activity/{activityId}` | autenticado | Questionário, com `anonymous` e `identificationNotice` (texto a exibir **antes** do envio). |
| `GET /surveys/activity/{activityId}/can-evaluate/{userId}` | o próprio ou equipe | `{canEvaluate, reason}`; quando não pode, `reason` explica o motivo (RN-13). |
| `POST /surveys/submit` `{activityId, answers:{"<idPergunta>":"resposta"}}` | autenticado | Só inscrito com presença validada, uma vez por atividade. A política de anonimato vem do questionário. |
| `GET /surveys/activity/{activityId}/results` | responsável | Quantidade, média, distribuição por pergunta, comentários (identificados ou anônimos conforme a política). |

## Relatórios e certificados

| Método e rota | Quem | Descrição |
|---|---|---|
| `GET /reports/enrolled/{eventId}` · `/csv` · `/pdf` | responsável | Inscritos: identificação, situação, totais (confirmadas × canceladas × capacidade). |
| `GET /reports/attendance/{eventId}` · `/csv` · `/pdf` | responsável | Frequência: totais por atividade e a situação calculada **por participante e atividade** (`rows`). |
| `GET /certificates/eligibility/{eventId}/{userId}` | o próprio ou equipe | `{eligible, justification, policyDescription, activitiesAttended, totalActivities, hoursAttended, totalHours, alreadyIssued}`. |
| `POST /certificates/issue/{eventId}/{userId}` | o próprio ou equipe | Emite o certificado se elegível (idempotente: devolve o já emitido). Inelegível → 400 com a justificativa. |
| `POST /certificates/{id}/send` | o titular ou equipe | Envia o PDF ao e-mail cadastrado do titular (RF-34). **Sempre `200`** quando o certificado existe: o resultado é `{id, certificateId, recipient, status, statusName, detail, attemptedAt}` com `status` = `SENT` ou `FAILED`. Falha de entrega (inclusive SMTP não configurado) vem como `FAILED` com o motivo, e o download continua disponível. Um reenvio em até 60 s após um envio bem-sucedido devolve o registro anterior, sem mandar outro e-mail. |
| `GET /certificates/{id}/deliveries` | o titular ou equipe | Histórico de tentativas de envio, da mais recente para a mais antiga. |
| `GET /certificates/user/{userId}` | o próprio ou equipe | Certificados do usuário. |
| `GET /certificates/download/{id}` | o titular ou equipe | PDF do certificado, com código verificável. |
| `GET /certificates/verify/{code}` | **público** | Confere a autenticidade; **não** devolve `userId` nem `eventId`. |
| `GET /activities/{activityId}/speakers/{speakerId}/declaration` | responsável | PDF de declaração para palestrante/apresentador, com a atividade e o papel (RF-35, desejável). |

Os CSV usam UTF-8 com BOM (abrem com acentos no Excel) e neutralizam fórmulas (`=`, `+`, `-`, `@`).

## Demonstração

| Método e rota | Descrição |
|---|---|
| `GET /demo/accounts` | Contas do seed para o seletor de perfis do site. **Só existe no modo demonstração** (`EVENTOS_DEMO_MODE` ≠ `false`); fora dele responde 404. |
