package com.eventos.adapters.output.email;

import java.util.Optional;

/**
 * Configuração do servidor SMTP, lida de variáveis de ambiente (nada de credencial no código ou no repositório).
 *
 * <ul>
 *   <li>{@code EVENTOS_SMTP_HOST} (obrigatória para ativar o envio) e {@code EVENTOS_SMTP_PORT} (padrão 587)</li>
 *   <li>{@code EVENTOS_SMTP_USER} / {@code EVENTOS_SMTP_PASSWORD} (opcionais; sem usuário não há autenticação)</li>
 *   <li>{@code EVENTOS_SMTP_FROM} (remetente; padrão: o usuário)</li>
 *   <li>{@code EVENTOS_SMTP_SECURITY}: {@code STARTTLS} (padrão), {@code SSL} ou {@code NONE}</li>
 * </ul>
 */
public record SmtpSettings(String host, int port, String username, String password, String from, Security security) {
    public enum Security { STARTTLS, SSL, NONE }

    public static Optional<SmtpSettings> fromEnvironment() {
        String host = System.getenv("EVENTOS_SMTP_HOST");
        if (host == null || host.isBlank()) return Optional.empty();
        String user = blankToNull(System.getenv("EVENTOS_SMTP_USER"));
        String from = blankToNull(System.getenv("EVENTOS_SMTP_FROM"));
        return Optional.of(new SmtpSettings(host.trim(), parsePort(System.getenv("EVENTOS_SMTP_PORT")), user,
                blankToNull(System.getenv("EVENTOS_SMTP_PASSWORD")), from != null ? from : user,
                parseSecurity(System.getenv("EVENTOS_SMTP_SECURITY"))));
    }

    private static int parsePort(String text) {
        try {
            return text == null || text.isBlank() ? 587 : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 587;
        }
    }

    private static Security parseSecurity(String text) {
        if (text == null || text.isBlank()) return Security.STARTTLS;
        try {
            return Security.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Security.STARTTLS;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
