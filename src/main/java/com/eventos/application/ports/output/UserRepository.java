package com.eventos.application.ports.output;

import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmail(Email email);
    List<User> findAll();
    boolean existsByEmail(Email email);
}
