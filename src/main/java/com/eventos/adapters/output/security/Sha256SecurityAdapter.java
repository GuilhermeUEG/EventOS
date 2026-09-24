package com.eventos.adapters.output.security;

import com.eventos.application.ports.output.SecurityPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

public class Sha256SecurityAdapter implements SecurityPort {
    private static final String STATIC_SALT = "EventOS_POO2_Secure_Salt_2026!";

    @Override
    public String hashPassword(String plainPassword) {
        if (plainPassword == null) plainPassword = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((STATIC_SALT + plainPassword).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao gerar hash SHA-256", e);
        }
    }

    @Override
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        String computed = hashPassword(plainPassword);
        return computed.equals(hashedPassword);
    }

    @Override
    public String generateToken(String subject) {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String generateQrToken(Long activityId) {
        // Payload no formato: ACT_{activityId}_{timestamp}_{signature} (RN-10: não expõe dados sensíveis)
        long timestamp = System.currentTimeMillis();
        String raw = "ACT_" + activityId + "_" + timestamp;
        String sign = hashPassword(raw).substring(0, 10);
        return Base64.getUrlEncoder().withoutPadding().encodeToString((raw + "_" + sign).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Long validateQrToken(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String str = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = str.split("_");
            if (parts.length < 4 || !parts[0].equals("ACT")) {
                return null;
            }
            Long activityId = Long.parseLong(parts[1]);
            long timestamp = Long.parseLong(parts[2]);
            String expectedSign = hashPassword("ACT_" + activityId + "_" + timestamp).substring(0, 10);
            if (!expectedSign.equals(parts[3])) {
                return null;
            }
            return activityId;
        } catch (Exception e) {
            return null;
        }
    }
}
