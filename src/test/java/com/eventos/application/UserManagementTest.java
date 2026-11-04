package com.eventos.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.adapters.output.security.Pbkdf2SecurityAdapter;
import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.services.AuthServiceImpl;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ForbiddenException;
import com.eventos.domain.exceptions.UnauthorizedException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.support.InMemoryUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Roda os casos de uso de autenticação sobre um repositório em memória: as regras não dependem do banco.
 */
@DisplayName("Cadastro, autenticação e perfis (RF-01 a RF-03, RN-01, RN-17, RNF-05)")
class UserManagementTest {
    private final Pbkdf2SecurityAdapter security = new Pbkdf2SecurityAdapter("segredo-de-teste-com-16+".getBytes(StandardCharsets.UTF_8));
    private InMemoryUserRepository users;
    private AuthUseCase auth;
    private User admin;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        auth = new AuthServiceImpl(users, security);
        admin = users.save(new User(null, "Administrador", new Email("admin@teste.com"), security.hashPassword("admin123"),
                UserRole.ADMIN, LocalDateTime.now()));
    }

    @Test
    @DisplayName("o cadastro público cria participante e guarda somente o hash da senha")
    void registerCreatesParticipantWithHashedPassword() {
        User user = auth.register("Ana", "Ana@Teste.com", "minhasenha");
        assertEquals(UserRole.PARTICIPANT, user.getRole());
        assertEquals("ana@teste.com", user.getEmail().getValue(), "e-mail é normalizado");
        assertTrue(user.getPasswordHash().startsWith("pbkdf2$"));
        assertFalse(user.getPasswordHash().contains("minhasenha"));
    }

    @Test
    @DisplayName("e-mail identifica uma única conta, sem diferenciar maiúsculas (RN-01)")
    void duplicateEmailIsRejected() {
        auth.register("Ana", "ana@teste.com", "minhasenha");
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> auth.register("Outra Ana", "ANA@teste.com", "outrasenha"));
        assertTrue(e.getMessage().contains("já está cadastrado"));
    }

    @Test
    @DisplayName("dados inválidos são recusados com mensagem clara")
    void validation() {
        assertThrows(ValidationException.class, () -> auth.register("Ana", "sem-arroba", "minhasenha"));
        assertThrows(ValidationException.class, () -> auth.register(" ", "ana@teste.com", "minhasenha"));
        ValidationException shortPassword = assertThrows(ValidationException.class, () -> auth.register("Ana", "ana@teste.com", "123"));
        assertTrue(shortPassword.getMessage().contains("6"));
        assertThrows(ValidationException.class, () -> auth.register("Ana", "ana@teste.com", null));
        assertThrows(ValidationException.class, () -> auth.register("Ana", "ana@teste.com", "x".repeat(200)));
        assertEquals(1, users.findAll().size(), "nada foi gravado além do administrador");
    }

    @Test
    @DisplayName("login válido autentica; senha errada e e-mail desconhecido recebem a mesma resposta")
    void login() {
        auth.register("Ana", "ana@teste.com", "minhasenha");
        assertEquals("Ana", auth.login("ana@teste.com", "minhasenha").getName());
        UnauthorizedException wrong = assertThrows(UnauthorizedException.class, () -> auth.login("ana@teste.com", "errada"));
        UnauthorizedException unknown = assertThrows(UnauthorizedException.class, () -> auth.login("ninguem@teste.com", "minhasenha"));
        assertEquals(wrong.getMessage(), unknown.getMessage(), "não revela se o e-mail existe");
    }

    @Test
    @DisplayName("hashes antigos (SHA-256) são aceitos uma vez e migrados para PBKDF2")
    void legacyHashIsUpgraded() throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        String legacy = java.util.Base64.getEncoder().encodeToString(
                digest.digest(("EventOS_POO2_Secure_Salt_2026!" + "antiga123").getBytes(StandardCharsets.UTF_8)));
        users.save(new User(null, "Antigo", new Email("antigo@teste.com"), legacy, UserRole.PARTICIPANT, LocalDateTime.now()));
        assertEquals("Antigo", auth.login("antigo@teste.com", "antiga123").getName());
        assertTrue(users.findByEmail(new Email("antigo@teste.com")).orElseThrow().getPasswordHash().startsWith("pbkdf2$"));
        assertEquals("Antigo", auth.login("antigo@teste.com", "antiga123").getName(), "continua entrando após a migração");
    }

    @Test
    @DisplayName("participante atualiza o próprio nome")
    void updateProfile() {
        User user = auth.register("Ana", "ana@teste.com", "minhasenha");
        assertEquals("Ana Maria", auth.updateProfile(user.getId(), "  Ana Maria ").getName());
        assertEquals("Ana Maria", auth.getUserById(user.getId()).getName(), "persistiu");
        assertThrows(ValidationException.class, () -> auth.updateProfile(user.getId(), " "));
    }

    @Test
    @DisplayName("troca de senha exige a senha atual e valida a nova")
    void changePassword() {
        User user = auth.register("Ana", "ana@teste.com", "minhasenha");
        BusinessRuleException wrongCurrent = assertThrows(BusinessRuleException.class,
                () -> auth.changePassword(user.getId(), "errada", "novasenha"));
        assertTrue(wrongCurrent.getMessage().contains("senha atual"));
        assertThrows(ValidationException.class, () -> auth.changePassword(user.getId(), "minhasenha", "123"));
        auth.changePassword(user.getId(), "minhasenha", "novasenha");
        assertThrows(UnauthorizedException.class, () -> auth.login("ana@teste.com", "minhasenha"));
        assertEquals("Ana", auth.login("ana@teste.com", "novasenha").getName());
    }

    @Test
    @DisplayName("somente administrador cria organizadores")
    void onlyAdminCreatesStaff() {
        User organizer = auth.createUser(admin.getId(), "Org", "org@teste.com", "orgsenha", UserRole.ORGANIZER);
        assertEquals(UserRole.ORGANIZER, organizer.getRole());

        User participant = auth.register("Ana", "ana@teste.com", "minhasenha");
        assertThrows(ForbiddenException.class, () -> auth.createUser(participant.getId(), "X", "x@teste.com", "xsenha1", UserRole.ORGANIZER));
        assertThrows(ForbiddenException.class, () -> auth.createUser(organizer.getId(), "Y", "y@teste.com", "ysenha1", UserRole.ADMIN));
        assertThrows(ForbiddenException.class, () -> auth.createUser(999L, "Z", "z@teste.com", "zsenha1", UserRole.ADMIN));
        assertThrows(ValidationException.class, () -> auth.createUser(admin.getId(), "W", "w@teste.com", "wsenha1", null));
    }

    @Test
    @DisplayName("somente administrador altera perfis, e o último administrador não pode ser rebaixado")
    void roleChanges() {
        User participant = auth.register("Ana", "ana@teste.com", "minhasenha");
        assertThrows(ForbiddenException.class, () -> auth.changeRole(participant.getId(), participant.getId(), UserRole.ADMIN));

        User promoted = auth.changeRole(admin.getId(), participant.getId(), UserRole.ORGANIZER);
        assertEquals(UserRole.ORGANIZER, promoted.getRole());
        assertEquals(UserRole.ORGANIZER, auth.getUserById(participant.getId()).getRole(), "persistiu");

        BusinessRuleException lastAdmin = assertThrows(BusinessRuleException.class,
                () -> auth.changeRole(admin.getId(), admin.getId(), UserRole.PARTICIPANT));
        assertTrue(lastAdmin.getMessage().contains("último administrador"));

        auth.changeRole(admin.getId(), participant.getId(), UserRole.ADMIN);
        assertEquals(UserRole.PARTICIPANT, auth.changeRole(admin.getId(), admin.getId(), UserRole.PARTICIPANT).getRole(),
                "com outro administrador, o rebaixamento é permitido");
        assertNotEquals(UserRole.ADMIN, auth.getUserById(admin.getId()).getRole());
    }
}
