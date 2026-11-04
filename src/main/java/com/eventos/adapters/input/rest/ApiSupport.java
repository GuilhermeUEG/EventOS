package com.eventos.adapters.input.rest;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.domain.exceptions.ForbiddenException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.UserRole;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/**
 * Utilitários compartilhados pelos controllers: leitura segura do corpo, conversões e autorização
 * por responsabilidade sobre o evento.
 */
final class ApiSupport {
    private static final DateTimeFormatter BR_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthorizationHandler authorization;
    private final EventUseCase eventUseCase;

    ApiSupport(AuthorizationHandler authorization, EventUseCase eventUseCase) {
        this.authorization = authorization;
        this.eventUseCase = eventUseCase;
    }

    AuthorizationHandler authorization() {
        return authorization;
    }

    /** Lê o corpo JSON como o tipo pedido; corpo ausente ou malformado vira erro de validação (400). */
    <T> T body(Context ctx, Class<T> type) {
        if (ctx.body().isBlank()) {
            throw new ValidationException("O corpo da requisição é obrigatório.");
        }
        try {
            return ctx.bodyAsClass(type);
        } catch (HttpResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Corpo da requisição inválido: verifique os campos e seus tipos.");
        }
    }

    Long pathId(Context ctx, String name) {
        try {
            return Long.parseLong(ctx.pathParam(name));
        } catch (NumberFormatException e) {
            throw new ValidationException("Identificador inválido: " + name + ".");
        }
    }

    Long required(Long value, String label) {
        if (value == null) throw new ValidationException("O campo '" + label + "' é obrigatório.");
        return value;
    }

    String required(String value, String label) {
        if (value == null || value.isBlank()) throw new ValidationException("O campo '" + label + "' é obrigatório.");
        return value;
    }

    /** Organizador responsável pelo evento ou administrador (RN-17). */
    AuthenticatedSession requireEventManager(Context ctx, Long eventId) {
        AuthenticatedSession session = authorization.requireRole(ctx, UserRole.ADMIN, UserRole.ORGANIZER);
        if (session.role() == UserRole.ORGANIZER) {
            Event event = eventUseCase.getEvent(eventId);
            if (event.getOrganizerId() == null || !event.getOrganizerId().equals(session.userId())) {
                throw new ForbiddenException("Somente o organizador responsável ou um administrador pode alterar este evento.");
            }
        }
        return session;
    }

    /** Eventos fora de estado público só podem ser vistos por quem os gerencia (RN-04). */
    void requireVisibleOrManager(Context ctx, Event event) {
        if (!event.isPubliclyVisible()) {
            requireEventManager(ctx, event.getId());
        }
    }

    LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Data e horário são obrigatórios.");
        }
        String value = text.trim();
        try {
            if (value.contains("T")) return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
            if (value.contains("/")) return LocalDateTime.parse(value, BR_FORMAT);
            return LocalDateTime.parse(value, SPACE_FORMAT);
        } catch (Exception e) {
            throw new ValidationException("Data/hora inválida. Use ISO-8601, dd/MM/yyyy HH:mm ou yyyy-MM-dd HH:mm.");
        }
    }

    LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            throw new ValidationException("Data inválida. Use o formato yyyy-MM-dd.");
        }
    }

    LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalTime.parse(text.trim());
        } catch (Exception e) {
            throw new ValidationException("Horário inválido. Use o formato HH:mm.");
        }
    }

    /** Converte texto em constante do enum, aceitando qualquer caixa e listando os valores válidos no erro. */
    <E extends Enum<E>> E parseEnum(Class<E> type, String text, String label) {
        if (text == null || text.isBlank()) return null;
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Valor inválido para '" + label + "': " + text + ". Valores aceitos: "
                    + Arrays.toString(type.getEnumConstants()) + ".");
        }
    }
}
