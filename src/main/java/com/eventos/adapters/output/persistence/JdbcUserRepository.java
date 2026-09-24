package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            String sql = "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, user.getName());
                stmt.setString(2, user.getEmail().getValue());
                stmt.setString(3, user.getPasswordHash());
                stmt.setString(4, user.getRole().name());
                stmt.setTimestamp(5, Timestamp.valueOf(user.getCreatedAt()));
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return new User(rs.getLong(1), user.getName(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.getCreatedAt());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao inserir usuário: " + e.getMessage(), e);
            }
        } else {
            String sql = "UPDATE users SET name = ?, password_hash = ?, role = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, user.getName());
                stmt.setString(2, user.getPasswordHash());
                stmt.setString(3, user.getRole().name());
                stmt.setLong(4, user.getId());
                stmt.executeUpdate();
                return user;
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao atualizar usuário: " + e.getMessage(), e);
            }
        }
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário por ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.getValue());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário por e-mail: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY name";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar usuários: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("name"),
                new Email(rs.getString("email")),
                rs.getString("password_hash"),
                UserRole.valueOf(rs.getString("role")),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
