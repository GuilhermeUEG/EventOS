package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.ParticipantRepository;
import com.eventos.domain.Participant;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParticipantPersistenceAdapter implements ParticipantRepository {

    @Override
    public Participant save(Participant participant) {
        String insertSql = "INSERT INTO participants (name, email, password_hash, role) VALUES (?, ?, ?, ?)";
        String updateSql = "UPDATE participants SET name = ?, email = ?, password_hash = ?, role = ? WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection()) {
            if (participant.getId() == null) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, participant.getName());
                    ps.setString(2, participant.getEmail());
                    ps.setString(3, participant.getPasswordHash());
                    ps.setString(4, participant.getRole());
                    ps.executeUpdate();
                    
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            participant.setId(rs.getLong(1));
                        }
                    }
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, participant.getName());
                    ps.setString(2, participant.getEmail());
                    ps.setString(3, participant.getPasswordHash());
                    ps.setString(4, participant.getRole());
                    ps.setLong(5, participant.getId());
                    ps.executeUpdate();
                }
            }
            return participant;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar participante no banco: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Participant> findById(Long id) {
        String selectSql = "SELECT * FROM participants WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapParticipant(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar participante por ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Participant> findByEmail(String email) {
        String selectSql = "SELECT * FROM participants WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapParticipant(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar participante por e-mail: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Participant> findAll() {
        List<Participant> list = new ArrayList<>();
        String selectAll = "SELECT * FROM participants";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectAll);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapParticipant(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar participantes: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void deleteById(Long id) {
        String deleteSql = "DELETE FROM participants WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar participante: " + e.getMessage(), e);
        }
    }

    private Participant mapParticipant(ResultSet rs) throws SQLException {
        return new Participant(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role")
        );
    }
}
