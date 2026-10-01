package com.eventos.adapters.output.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Pbkdf2SecurityAdapterTest {
    private final Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter();

    @Test
    void hashesPasswordWithRandomSaltAndVerifiesIt() {
        String first = security.hashPassword("senhaSegura123");
        String second = security.hashPassword("senhaSegura123");

        assertNotEquals(first, second);
        assertTrue(security.verifyPassword("senhaSegura123", first));
        assertFalse(security.verifyPassword("senhaErrada", first));
        assertFalse(security.needsRehash(first));
    }

    @Test
    void validatesOnlyUntamperedQrTokens() {
        String token = security.generateQrToken(42L);

        assertEquals(42L, security.validateQrToken(token));
        assertNull(security.validateQrToken(token + "adulterado"));
    }
}
