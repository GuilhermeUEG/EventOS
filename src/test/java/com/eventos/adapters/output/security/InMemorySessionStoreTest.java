package com.eventos.adapters.output.security;

import static org.junit.jupiter.api.Assertions.*;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {
    @Test
    void createsFindsAndRevokesServerSideSession() {
        InMemorySessionStore store = new InMemorySessionStore();
        User user = new User(7L, "Participante", new Email("participante@test.com"),
                "hash-seguro", UserRole.PARTICIPANT, LocalDateTime.now());

        AuthenticatedSession session = store.create(user);

        assertEquals(7L, store.findValid(session.token()).orElseThrow().userId());
        store.revoke(session.token());
        assertTrue(store.findValid(session.token()).isEmpty());
    }
}
