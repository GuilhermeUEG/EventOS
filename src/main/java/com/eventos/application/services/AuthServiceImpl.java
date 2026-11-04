package com.eventos.application.services;

import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.output.SecurityPort;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.ForbiddenException;
import com.eventos.domain.exceptions.UnauthorizedException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthServiceImpl implements AuthUseCase {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    static final int MIN_PASSWORD_LENGTH = 6;
    static final int MAX_PASSWORD_LENGTH = 128;

    private final UserRepository userRepository;
    private final SecurityPort securityPort;

    public AuthServiceImpl(UserRepository userRepository, SecurityPort securityPort) {
        this.userRepository = userRepository;
        this.securityPort = securityPort;
    }

    @Override
    public synchronized User register(String name, String emailStr, String password) {
        return createAccount(name, emailStr, password, UserRole.PARTICIPANT);
    }

    @Override
    public synchronized User createUser(Long adminId, String name, String emailStr, String password, UserRole role) {
        requireAdmin(adminId);
        if (role == null) {
            throw new ValidationException("Perfil de usuário é obrigatório.");
        }
        User created = createAccount(name, emailStr, password, role);
        log.info("Usuário {} criado com perfil {} pelo administrador {}", created.getId(), role, adminId);
        return created;
    }

    @Override
    public User login(String emailStr, String password) {
        Email email = new Email(emailStr);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !securityPort.verifyPassword(password, user.getPasswordHash())) {
            log.warn("Tentativa de login recusada para um e-mail informado.");
            throw new UnauthorizedException("Credenciais inválidas. E-mail ou senha incorretos.");
        }
        if (securityPort.needsRehash(user.getPasswordHash())) {
            user.changePassword(securityPort.hashPassword(password));
            user = userRepository.save(user);
        }
        return user;
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário com ID " + id + " não foi encontrado."));
    }

    @Override
    public User updateProfile(Long id, String name) {
        User user = getUserById(id);
        user.updateProfile(name);
        return userRepository.save(user);
    }

    @Override
    public synchronized void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);
        if (!securityPort.verifyPassword(currentPassword, user.getPasswordHash())) {
            // É um erro do pedido (400), não falta de sessão: o cliente não deve encerrar a sessão por causa disto.
            throw new BusinessRuleException("A senha atual informada está incorreta.");
        }
        validatePassword(newPassword);
        user.changePassword(securityPort.hashPassword(newPassword));
        userRepository.save(user);
    }

    @Override
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Override
    public synchronized User changeRole(Long adminId, Long targetUserId, UserRole newRole) {
        requireAdmin(adminId);
        User target = getUserById(targetUserId);
        if (newRole == null) {
            throw new ValidationException("Perfil de usuário inválido.");
        }
        if (target.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long admins = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.ADMIN).count();
            if (admins <= 1) {
                throw new BusinessRuleException("O último administrador do sistema não pode ser rebaixado.");
            }
        }
        target.changeRole(newRole);
        User saved = userRepository.save(target);
        log.info("Perfil do usuário {} alterado para {} pelo administrador {}", targetUserId, newRole, adminId);
        return saved;
    }

    private User createAccount(String name, String emailStr, String password, UserRole role) {
        Email email = new Email(emailStr);
        validatePassword(password);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("O e-mail '" + email + "' já está cadastrado no sistema.");
        }
        User user = new User(null, name, email, securityPort.hashPassword(password), role, LocalDateTime.now());
        return userRepository.save(user);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("A senha deve possuir ao menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("A senha deve possuir no máximo " + MAX_PASSWORD_LENGTH + " caracteres.");
        }
    }

    private void requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ForbiddenException("Somente administradores podem executar esta operação."));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Somente administradores podem executar esta operação.");
        }
    }
}
