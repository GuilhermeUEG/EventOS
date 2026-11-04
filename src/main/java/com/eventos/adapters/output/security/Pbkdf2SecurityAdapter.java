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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PBKDF2 para senhas e HMAC-SHA256 para o conteúdo dos QR Codes de frequência (RNF-05, RN-10, D-05).
 *
 * <p>O segredo do HMAC vem da variável de ambiente {@value #SECRET_ENV}. Sem ela, um segredo
 * aleatório é gerado a cada inicialização: os QR Codes antigos deixam de valer ao reiniciar,
 * o que é aceitável porque eles já expiram em poucos minutos.</p>
 */
public class Pbkdf2SecurityAdapter implements SecurityPort {
    public static final String SECRET_ENV = "EVENTOS_QR_SECRET";

    private static final Logger log = LoggerFactory.getLogger(Pbkdf2SecurityAdapter.class);
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final Duration QR_VALIDITY = Duration.ofMinutes(15);
    private static final String LEGACY_SHA256_SALT = "EventOS_POO2_Secure_Salt_2026!";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] qrSecret;

    public Pbkdf2SecurityAdapter() {
        this(resolveSecret());
    }

    public Pbkdf2SecurityAdapter(byte[] qrSecret) {
        if (qrSecret == null || qrSecret.length < 16) {
            throw new IllegalArgumentException("O segredo do QR Code deve ter ao menos 16 bytes.");
        }
        this.qrSecret = qrSecret.clone();
    }

    private static byte[] resolveSecret() {
        String configured = System.getenv(SECRET_ENV);
        if (configured != null && configured.length() >= 16) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        log.warn("Variável {} ausente ou curta: usando segredo aleatório desta execução para os QR Codes.", SECRET_ENV);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }

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
    public String generateQrToken(Long activityId) {
        long timestamp = System.currentTimeMillis();
        String payload = "ACT_" + activityId + "_" + timestamp;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "_" + signature).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Long validateQrToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token.trim()), StandardCharsets.UTF_8);
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
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Duration qrValidity() {
        return QR_VALIDITY;
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
            mac.init(new SecretKeySpec(qrSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar o token QR.", e);
        }
    }

    /** Hash de versões antigas do sistema; aceito apenas para migrar a senha no próximo login. */
    private String legacySha256(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((LEGACY_SHA256_SALT + plainPassword).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível validar o hash legado.", e);
        }
    }
}
