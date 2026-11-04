package com.eventos.adapters.output.email;

import com.eventos.application.dtos.EmailMessage;
import com.eventos.application.ports.output.EmailDeliveryException;
import com.eventos.application.ports.output.EmailSenderPort;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envia e-mails com anexo por SMTP (Jakarta Mail). As mensagens de erro devolvidas ao chamador nunca
 * incluem usuário nem senha.
 */
public class SmtpEmailAdapter implements EmailSenderPort {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailAdapter.class);

    private final SmtpSettings settings;

    public SmtpEmailAdapter(SmtpSettings settings) {
        this.settings = settings;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            Session session = Session.getInstance(properties(), authenticator());
            MimeMessage mime = new MimeMessage(session);
            mime.setFrom(new InternetAddress(settings.from() != null ? settings.from() : "eventos@localhost"));
            mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(message.to(), false));
            mime.setSubject(message.subject(), "UTF-8");

            MimeBodyPart text = new MimeBodyPart();
            text.setText(message.body(), "UTF-8");
            MimeMultipart content = new MimeMultipart();
            content.addBodyPart(text);
            if (message.attachment() != null) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(message.attachment(), message.attachmentContentType())));
                attachment.setFileName(message.attachmentName());
                content.addBodyPart(attachment);
            }
            mime.setContent(content);
            Transport.send(mime);
        } catch (MessagingException e) {
            log.warn("Falha SMTP ao enviar para {}: {}", settings.host(), e.getMessage());
            throw new EmailDeliveryException("Não foi possível entregar o e-mail pelo servidor SMTP configurado ("
                    + settings.host() + "): " + shortReason(e), e);
        }
    }

    private Properties properties() {
        Properties props = new Properties();
        props.put("mail.smtp.host", settings.host());
        props.put("mail.smtp.port", String.valueOf(settings.port()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        props.put("mail.smtp.auth", String.valueOf(settings.username() != null));
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.security() == SmtpSettings.Security.STARTTLS));
        props.put("mail.smtp.ssl.enable", String.valueOf(settings.security() == SmtpSettings.Security.SSL));
        return props;
    }

    private Authenticator authenticator() {
        if (settings.username() == null) return null;
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.username(), settings.password() == null ? "" : settings.password());
            }
        };
    }

    private String shortReason(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String text = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
