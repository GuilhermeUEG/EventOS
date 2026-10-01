# API REST

Base local: `http://localhost:7000/api`

## Autenticação

- `POST /auth/register` — público; cria participante e devolve `{token, expiresAt, user}`.
- `POST /auth/login` — público; devolve sessão.
- `POST /auth/logout` — autenticado.

Envie `Authorization: Bearer <token>` nas operações protegidas.

## Eventos e atividades

- `GET /events` — público; sem autenticação retorna apenas publicados.
- `GET /events/{id}` e `GET /events/{id}/activities`.
- `POST /events`, `PUT /events/{id}`, `POST /events/{id}/publish|cancel|finish` — responsável/admin.
- `POST /events/{id}/activities` — responsável/admin.

Configuração de evento: `activitySelectionEnabled`, `activitySelectionRequired`,
`registrationDeadline` e `timeZone`.

## Inscrição e agenda

- `POST /registrations` — participante autenticado.
- `POST|DELETE /registrations/{eventId}/activities/{activityId}`.
- `POST /registrations/{eventId}/cancel`.
- `GET /registrations/agenda/{userId}/{eventId}`.

O usuário da operação deriva da sessão, não do JSON enviado pelo navegador.

## Frequência

- `GET /attendance/qr/generate/{activityId}` — responsável/admin.
- `POST /attendance/qr/scan` — participante autenticado.
- `POST /attendance/manual` — responsável/admin.
- `GET /attendance/activity/{activityId}/overview` — responsável/admin.

## Avaliações

- `POST /surveys` — responsável/admin.
- `GET /surveys/activity/{activityId}`.
- `GET /surveys/activity/{activityId}/can-evaluate/{userId}`.
- `POST /surveys/submit`.
- `GET /surveys/activity/{activityId}/results` — responsável/admin.

## Relatórios e certificados

- `GET /reports/enrolled/{eventId}` e `/csv|pdf` — responsável/admin.
- `GET /reports/attendance/{eventId}` e `/pdf` — responsável/admin.
- `POST /certificates/issue/{eventId}/{userId}`.
- `GET /certificates/user/{userId}`.
- `GET /certificates/verify/{code}` — público.
- `GET /certificates/download/{id}` — autenticado.

Erros são JSON no formato `{ "error": "mensagem compreensível" }`.
