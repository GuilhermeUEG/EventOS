package com.eventos.application.ports.input;

import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import java.util.List;

public interface AuthUseCase {
    /** Cadastro público: sempre cria um participante (RF-01, RN-01). */
    User register(String name, String email, String password);

    User login(String email, String password);

    User getUserById(Long id);

    User updateProfile(Long id, String name);

    void changePassword(Long userId, String currentPassword, String newPassword);

    List<User> listUsers();

    /** Somente administradores criam usuários com perfil de organizador ou administrador. */
    User createUser(Long adminId, String name, String email, String password, UserRole role);

    /** Somente administradores alteram perfis; o último administrador não pode ser rebaixado. */
    User changeRole(Long adminId, Long targetUserId, UserRole newRole);
}
