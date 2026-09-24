package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidade de Domínio representando um Usuário do sistema (RF-01, RF-02, ROO-01, ROO-02).
 */
public class User implements Serializable {
    private final Long id;
    private String name;
    private final Email email;
    private String passwordHash;
    private UserRole role;
    private final LocalDateTime createdAt;

    public User(Long id, String name, Email email, String passwordHash, UserRole role, LocalDateTime createdAt) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Nome do usuário é obrigatório.");
        }
        if (email == null) {
            throw new ValidationException("E-mail é obrigatório.");
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new ValidationException("Senha/Hash é obrigatória.");
        }
        this.id = id;
        this.name = name.trim();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : UserRole.PARTICIPANT;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void updateProfile(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new ValidationException("O novo nome não pode ser vazio.");
        }
        this.name = newName.trim();
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.trim().isEmpty()) {
            throw new ValidationException("Nova senha não pode ser vazia.");
        }
        this.passwordHash = newPasswordHash;
    }

    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new ValidationException("Perfil de usuário inválido.");
        }
        this.role = newRole;
    }

    public boolean isOrganizerOrAdmin() {
        return this.role == UserRole.ADMIN || this.role == UserRole.ORGANIZER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) || Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }
}
