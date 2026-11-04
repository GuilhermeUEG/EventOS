package com.eventos.application.dtos;

/**
 * Mensagem de e-mail com um anexo, entregue à porta {@code EmailSenderPort}.
 */
public record EmailMessage(String to, String subject, String body,
                           String attachmentName, byte[] attachment, String attachmentContentType) {
}
