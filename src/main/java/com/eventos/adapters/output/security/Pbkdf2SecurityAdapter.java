package com.eventos.adapters.output.security;

import com.eventos.application.ports.output.SecurityPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * PBKDF2 para senhas e HMAC para tokens QR temporários.
 */
public class Pbkdf2SecurityAdapter implements SecurityPort {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final Duration QR_VALIDITY = Duration.ofMinutes(15);
    private static final byte[] QR_SECRET =
            "EventOS_QR_HMAC_2026_change_in_production".getBytes(StandardCharsets.UTF_8);
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.length() < 6) {
            throw new IllegalArgumentException("A senha deve possuir ao menos 6 caracteres.");
        }
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(plainPassword.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) return false;
        if (!storedHash.startsWith("pbkdf2$")) {
            return MessageDigest.isEqual(
                    legacySha256(plainPassword).getBytes(StandardCharsets.UTF_8),
                    storedHash.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(plainPassword.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(String hashedPassword) {
        return hashedPassword == null || !hashedPassword.startsWith("pbkdf2$");
    }

    @Override
    public String generateToken(String subject) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String generateQrToken(Long activityId) {
        long timestamp = System.currentTimeMillis();
        String payload = "ACT_" + activityId + "_" + timestamp;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "_" + signature).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Long validateQrToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("_", 4);
            if (parts.length != 4 || !"ACT".equals(parts[0])) return null;
            Long activityId = Long.parseLong(parts[1]);
            long createdAt = Long.parseLong(parts[2]);
            long age = System.currentTimeMillis() - createdAt;
            if (age < 0 || age > QR_VALIDITY.toMillis()) return null;
            String payload = "ACT_" + activityId + "_" + createdAt;
            if (!MessageDigest.isEqual(
                    sign(payload).getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            return activityId;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o hash de senha.", e);
        } finally {
            spec.clearPassword();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(QR_SECRET, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar o token QR.", e);
        }
    }

    private String legacySha256(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("EventOS_POO2_Secure_Salt_2026!" + plainPassword)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível validar o hash legado.", e);
        }
    }
}
