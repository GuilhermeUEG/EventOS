package com.eventos.application.services;

import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.output.SecurityPort;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.UnauthorizedException;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import java.time.LocalDateTime;
import java.util.List;

public class AuthServiceImpl implements AuthUseCase {
    private final UserRepository userRepository;
    private final SecurityPort securityPort;

    public AuthServiceImpl(UserRepository userRepository, SecurityPort securityPort) {
        this.userRepository = userRepository;
        this.securityPort = securityPort;
    }

    @Override
    public User register(String name, String emailStr, String password, UserRole role) {
        Email email = new Email(emailStr);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("O e-mail '" + email + "' já está cadastrado no sistema.");
        }
        String passwordHash = securityPort.hashPassword(password);
        User user = new User(null, name, email, passwordHash, role != null ? role : UserRole.PARTICIPANT, LocalDateTime.now());
        return userRepository.save(user);
    }

    @Override
    public User login(String emailStr, String password) {
        Email email = new Email(emailStr);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas. E-mail ou senha incorretos."));
        if (!securityPort.verifyPassword(password, user.getPasswordHash())) {
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
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
