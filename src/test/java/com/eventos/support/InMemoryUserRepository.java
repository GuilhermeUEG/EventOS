package com.eventos.support;

import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptador de saída em memória: prova que os casos de uso funcionam com qualquer implementação da
 * porta {@link UserRepository}, sem banco de dados (ROO-07, ROO-08, ROO-09).
 */
public class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> byId = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public User save(User user) {
        Long id = user.getId() != null ? user.getId() : sequence.getAndIncrement();
        User stored = new User(id, user.getName(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.getCreatedAt());
        byId.put(id, stored);
        return stored;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return byId.values().stream().filter(u -> u.getEmail().equals(email)).findFirst();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public List<User> findByIds(Collection<Long> ids) {
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }
}
