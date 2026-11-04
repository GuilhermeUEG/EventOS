package com.eventos.adapters.output;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.adapters.output.qr.ZxingQrCodeAdapter;
import com.eventos.adapters.output.security.InMemorySessionStore;
import com.eventos.adapters.output.security.Pbkdf2SecurityAdapter;
import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Segurança (PBKDF2, HMAC, sessões) e geração de QR Code (RNF-05, RN-10, D-05)")
class SecurityAndQrAdaptersTest {
    private static final byte[] SECRET = "segredo-de-teste-com-16-bytes+".getBytes(StandardCharsets.UTF_8);
    private final Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter(SECRET);

    @Test
    @DisplayName("senha: hash com sal aleatório, verificação correta e nenhuma senha em texto aberto")
    void passwordHashing() {
        String h1 = security.hashPassword("minhasenha");
        String h2 = security.hashPassword("minhasenha");
        assertNotEquals(h1, h2, "sal aleatório");
        assertTrue(h1.startsWith("pbkdf2$"));
        assertFalse(h1.contains("minhasenha"));
        assertTrue(security.verifyPassword("minhasenha", h1));
        assertTrue(security.verifyPassword("minhasenha", h2));
        assertFalse(security.verifyPassword("outrasenha", h1));
        assertFalse(security.verifyPassword(null, h1));
        assertFalse(security.verifyPassword("minhasenha", "pbkdf2$lixo"));
        assertFalse(security.needsRehash(h1));
        assertTrue(security.needsRehash("hash-antigo"));
        assertThrows(IllegalArgumentException.class, () -> security.hashPassword("123"));
    }

    private String craft(byte[] secret, long activityId, long timestamp) throws Exception {
        String payload = "ACT_" + activityId + "_" + timestamp;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "_" + signature).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("QR: token autêntico e vigente é aceito; adulterado, de outro segredo, expirado ou do futuro não")
    void qrTokens() throws Exception {
        String token = security.generateQrToken(42L);
        assertEquals(42L, security.validateQrToken(token));
        assertEquals(15, security.qrValidity().toMinutes());

        long now = System.currentTimeMillis();
        assertEquals(7L, security.validateQrToken(craft(SECRET, 7L, now - 60_000)), "o formato conhecido é aceito");
        assertNull(security.validateQrToken(craft(SECRET, 7L, now - 16 * 60_000)), "expirado");
        assertNull(security.validateQrToken(craft(SECRET, 7L, now + 5 * 60_000)), "do futuro");
        assertNull(security.validateQrToken(craft("outro-segredo-completamente-diferente".getBytes(StandardCharsets.UTF_8), 7L, now)),
                "assinado com outro segredo");
        assertNull(new Pbkdf2SecurityAdapter("segredo-de-outra-instancia-16b".getBytes(StandardCharsets.UTF_8)).validateQrToken(token));

        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded.replace("ACT_42_", "ACT_43_").getBytes(StandardCharsets.UTF_8));
        assertNull(security.validateQrToken(forged), "trocar a atividade invalida a assinatura");
        assertNull(security.validateQrToken("%%%"));
        assertNull(security.validateQrToken(null));
        assertNull(security.validateQrToken(" "));
    }

    @Test
    @DisplayName("segredo curto é recusado")
    void shortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new Pbkdf2SecurityAdapter("curto".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new Pbkdf2SecurityAdapter((byte[]) null));
    }

    @Test
    @DisplayName("sessões opacas: criar, consultar e revogar")
    void sessions() {
        InMemorySessionStore store = new InMemorySessionStore();
        User user = new User(5L, "Ana", new Email("ana@teste.com"), "hash", UserRole.ORGANIZER, LocalDateTime.now());
        AuthenticatedSession session = store.create(user);
        assertTrue(session.token().length() >= 40, "token de 256 bits");
        assertEquals(5L, store.findValid(session.token()).orElseThrow().userId());
        assertEquals(UserRole.ORGANIZER, store.findValid(session.token()).orElseThrow().role());
        assertNotEquals(session.token(), store.create(user).token());
        assertTrue(session.hasAnyRole(UserRole.ADMIN, UserRole.ORGANIZER));
        assertFalse(session.hasAnyRole(UserRole.PARTICIPANT));
        assertFalse(session.isExpired());
        store.revoke(session.token());
        assertTrue(store.findValid(session.token()).isEmpty());
        assertTrue(store.findValid(null).isEmpty());
        assertTrue(store.findValid("  ").isEmpty());
        assertTrue(new AuthenticatedSession("t", 1L, UserRole.ADMIN, java.time.Instant.now().minusSeconds(1)).isExpired());
    }

    @Test
    @DisplayName("o QR Code gerado localmente é um PNG que decodifica de volta ao conteúdo")
    void qrImageRoundTrip() throws Exception {
        String content = security.generateQrToken(99L);
        byte[] png = new ZxingQrCodeAdapter().generatePng(content, 300);
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, java.util.Arrays.copyOf(png, 4));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(300, image.getWidth());
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        assertEquals(content, decoded);
        assertEquals(99L, security.validateQrToken(decoded));
    }
}
