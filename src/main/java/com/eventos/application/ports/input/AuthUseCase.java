package com.eventos.application.ports.input;

import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import java.util.List;

public interface AuthUseCase {
    User register(String name, String email, String password, UserRole role);
    User login(String email, String password);
    User getUserById(Long id);
    User updateProfile(Long id, String name);
    List<User> listUsers();
}
