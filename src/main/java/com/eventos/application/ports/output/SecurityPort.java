package com.eventos.application.ports.output;

import java.time.Duration;

public interface SecurityPort {
    String hashPassword(String plainPassword);

    boolean verifyPassword(String plainPassword, String hashedPassword);

    boolean needsRehash(String hashedPassword);

    /** Conteúdo assinado do QR Code de uma atividade; não contém dado pessoal (RN-10). */
    String generateQrToken(Long activityId);

    /** Id da atividade se o token for autêntico e vigente; nulo caso contrário. */
    Long validateQrToken(String token);

    Duration qrValidity();
}
