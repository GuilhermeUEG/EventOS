package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.CertificateRepository;
import com.eventos.domain.model.Certificate;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcCertificateRepository implements CertificateRepository {

    @Override
    public Certificate save(Certificate cert) {
        if (cert.getId() == null) {
            String sql = "INSERT INTO certificates (event_id, user_id, participant_name, event_title, completion_hours, issue_date, verification_code) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, cert.getEventId());
                stmt.setLong(2, cert.getUserId());
                stmt.setString(3, cert.getParticipantName());
                stmt.setString(4, cert.getEventTitle());
                stmt.setDouble(5, cert.getCompletionHours());
                stmt.setDate(6, Date.valueOf(cert.getIssueDate()));
                stmt.setString(7, cert.getVerificationCode());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return new Certificate(rs.getLong(1), cert.getEventId(), cert.getUserId(),
                                cert.getParticipantName(), cert.getEventTitle(), cert.getCompletionHours(),
                                cert.getIssueDate(), cert.getVerificationCode());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao emitir certificado: " + e.getMessage(), e);
            }
        }
        return cert;
    }

    @Override
    public Optional<Certificate> findById(Long id) {
        String sql = "SELECT * FROM certificates WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar certificado: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Certificate> findByEventAndUser(Long eventId, Long userId) {
        String sql = "SELECT * FROM certificates WHERE event_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar certificado: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Certificate> findByVerificationCode(String code) {
        String sql = "SELECT * FROM certificates WHERE verification_code = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code != null ? code.trim() : "");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao validar certificado: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Certificate> findByUserId(Long userId) {
        List<Certificate> list = new ArrayList<>();
        String sql = "SELECT * FROM certificates WHERE user_id = ? ORDER BY issue_date DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar certificados: " + e.getMessage(), e);
        }
        return list;
    }

    private Certificate mapRow(ResultSet rs) throws SQLException {
        return new Certificate(
                rs.getLong("id"),
                rs.getLong("event_id"),
                rs.getLong("user_id"),
                rs.getString("participant_name"),
                rs.getString("event_title"),
                rs.getDouble("completion_hours"),
                rs.getDate("issue_date").toLocalDate(),
                rs.getString("verification_code")
        );
    }
}
