package com.eventos.adapters.output.email;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.application.dtos.EmailMessage;
import com.eventos.application.ports.output.EmailDeliveryException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Envio de e-mail por SMTP (RF-34): adaptador real contra um servidor SMTP falso")
class SmtpEmailAdapterTest {

    /** Servidor SMTP mínimo em socket: aceita a mensagem e guarda destinatários e conteúdo. */
    private static final class FakeSmtpServer implements AutoCloseable {
        final ServerSocket server;
        final List<String> recipients = new CopyOnWriteArrayList<>();
        final List<String> messages = new CopyOnWriteArrayList<>();

        FakeSmtpServer() throws IOException {
            server = new ServerSocket(0);
            Thread thread = new Thread(this::serve, "fake-smtp");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve() {
            try {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        handle(socket);
                    }
                }
            } catch (IOException ignored) {
                // servidor encerrado
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            reply(out, "220 fake ESMTP pronto");
            String line;
            while ((line = in.readLine()) != null) {
                String upper = line.toUpperCase();
                if (upper.startsWith("EHLO")) {
                    reply(out, "250-fake\r\n250 8BITMIME");
                } else if (upper.startsWith("RCPT TO")) {
                    recipients.add(line);
                    reply(out, "250 OK");
                } else if (upper.equals("DATA")) {
                    reply(out, "354 envie os dados e termine com ponto");
                    StringBuilder body = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) body.append(line).append('\n');
                    messages.add(body.toString());
                    reply(out, "250 OK enfileirado");
                } else if (upper.startsWith("QUIT")) {
                    reply(out, "221 tchau");
                    return;
                } else {
                    reply(out, "250 OK");
                }
            }
        }

        private void reply(Writer out, String text) throws IOException {
            out.write(text + "\r\n");
            out.flush();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    private SmtpSettings settings(int port, String user, String password) {
        return new SmtpSettings("localhost", port, user, password, "eventos@teste.com", SmtpSettings.Security.NONE);
    }

    @Test
    @DisplayName("entrega a mensagem ao servidor com destinatário, assunto acentuado e o PDF como anexo")
    void deliversMessageWithPdfAttachment() throws Exception {
        // Conteúdo binário (como um PDF real, com bytes fora do ASCII), maior que uma linha MIME.
        byte[] pdf = new byte[600];
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, pdf, 0, header.length);
        for (int i = header.length; i < pdf.length; i++) pdf[i] = (byte) (i * 7);
        try (FakeSmtpServer smtp = new FakeSmtpServer()) {
            new SmtpEmailAdapter(settings(smtp.port(), null, null)).send(new EmailMessage(
                    "ana@teste.com", "Seu certificado - Simpósio", "Olá, Ana!\nSegue o certificado.",
                    "certificado_ABC123.pdf", pdf, "application/pdf"));

            assertTrue(smtp.recipients.stream().anyMatch(r -> r.contains("ana@teste.com")), smtp.recipients.toString());
            assertTrue(smtp.messages.size() == 1, "uma mensagem entregue");
            String raw = smtp.messages.get(0);
            assertTrue(raw.contains("Subject:"), raw);
            assertTrue(raw.contains("multipart/mixed"), "corpo + anexo");
            assertTrue(raw.contains("application/pdf"), raw);
            assertTrue(raw.contains("certificado_ABC123.pdf"), raw);
            assertTrue(raw.contains("Content-Transfer-Encoding: base64"), raw);
            assertArrayEquals(pdf, extractAttachment(raw), "o anexo chega idêntico ao PDF enviado");
        }
    }

    /** Lê a parte do anexo (depois do cabeçalho com o nome do arquivo) e decodifica o base64. */
    private byte[] extractAttachment(String rawMessage) {
        String[] lines = rawMessage.split("\n");
        int filename = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("certificado_ABC123.pdf")) filename = i;
        }
        int bodyStart = filename;
        while (!lines[bodyStart].isBlank()) bodyStart++;
        StringBuilder base64 = new StringBuilder();
        for (int i = bodyStart + 1; i < lines.length && !lines[i].startsWith("--"); i++) base64.append(lines[i].trim());
        return Base64.getDecoder().decode(base64.toString());
    }

    @Test
    @DisplayName("servidor inacessível vira EmailDeliveryException clara, sem vazar usuário nem senha")
    void unreachableServerFailsWithoutLeakingCredentials() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        SmtpEmailAdapter adapter = new SmtpEmailAdapter(settings(closedPort, "usuario-secreto", "SENHA-SECRETA-123"));
        EmailDeliveryException e = assertThrows(EmailDeliveryException.class, () -> adapter.send(new EmailMessage(
                "ana@teste.com", "Assunto", "Corpo", "a.pdf", new byte[]{1, 2, 3}, "application/pdf")));
        assertTrue(e.getMessage().contains("SMTP"), e.getMessage());
        assertFalse(e.getMessage().contains("SENHA-SECRETA-123"));
        assertFalse(e.getMessage().contains("usuario-secreto"));
    }

    @Test
    @DisplayName("sem SMTP configurado, a tentativa falha explicando como configurar (nunca finge que enviou)")
    void unconfiguredAdapterExplainsWhy() {
        EmailDeliveryException e = assertThrows(EmailDeliveryException.class, () -> new UnconfiguredEmailAdapter().send(
                new EmailMessage("ana@teste.com", "Assunto", "Corpo", "a.pdf", new byte[0], "application/pdf")));
        assertTrue(e.getMessage().contains("EVENTOS_SMTP_HOST"));
        assertTrue(e.getMessage().contains("Baixe"), "orienta o download como alternativa");
    }
}
