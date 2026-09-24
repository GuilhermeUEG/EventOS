package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.RegistrationStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JdbcRegistrationRepository implements RegistrationRepository {

    @Override
    public Registration save(Registration reg) {
        if (reg.getId() == null) {
            String sql = "INSERT INTO registrations (event_id, user_id, registration_date, status) VALUES (?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, reg.getEventId());
                stmt.setLong(2, reg.getUserId());
                stmt.setTimestamp(3, Timestamp.valueOf(reg.getRegistrationDate()));
                stmt.setString(4, reg.getStatus().name());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long regId = rs.getLong(1);
                        saveActivities(conn, regId, reg.getSelectedActivityIds());
                        return new Registration(regId, reg.getEventId(), reg.getUserId(), reg.getSelectedActivityIds(), reg.getRegistrationDate(), reg.getStatus());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao criar inscrição: " + e.getMessage(), e);
            }
        } else {
            String sql = "UPDATE registrations SET status = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, reg.getStatus().name());
                stmt.setLong(2, reg.getId());
                stmt.executeUpdate();
                saveActivities(conn, reg.getId(), reg.getSelectedActivityIds());
                return reg;
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao atualizar inscrição: " + e.getMessage(), e);
            }
        }
        return reg;
    }

    @Override
    public Optional<Registration> findByEventAndUser(Long eventId, Long userId) {
        String sql = "SELECT * FROM registrations WHERE event_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, eventId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Set<Long> acts = findActivitiesByRegId(conn, rs.getLong("id"));
                    return Optional.of(mapRow(rs, acts));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar inscrição: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Registration> findByEventId(Long eventId) {
        List<Registration> list = new ArrayList<>();
        String sql = "SELECT * FROM registrations WHERE event_id = ? ORDER BY registration_date ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Set<Long> acts = findActivitiesByRegId(conn, rs.getLong("id"));
                    list.add(mapRow(rs, acts));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar inscrições do evento: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Registration> findByUserId(Long userId) {
        List<Registration> list = new ArrayList<>();
        String sql = "SELECT * FROM registrations WHERE user_id = ? ORDER BY registration_date DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Set<Long> acts = findActivitiesByRegId(conn, rs.getLong("id"));
                    list.add(mapRow(rs, acts));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar inscrições do participante: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public int countConfirmedByEventId(Long eventId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar inscritos: " + e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public int countConfirmedByActivityId(Long activityId) {
        String sql = "SELECT COUNT(*) FROM registration_activities ra JOIN registrations r ON r.id = ra.registration_id WHERE ra.activity_id = ? AND r.status = 'CONFIRMED'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar inscritos na atividade: " + e.getMessage(), e);
        }
        return 0;
    }

    private void saveActivities(Connection conn, Long regId, Set<Long> actIds) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM registration_activities WHERE registration_id = ?")) {
            del.setLong(1, regId);
            del.executeUpdate();
        }
        if (actIds == null || actIds.isEmpty()) return;
        String sql = "INSERT INTO registration_activities (registration_id, activity_id) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Long id : actIds) {
                stmt.setLong(1, regId);
                stmt.setLong(2, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private Set<Long> findActivitiesByRegId(Connection conn, Long regId) throws SQLException {
        Set<Long> set = new HashSet<>();
        String sql = "SELECT activity_id FROM registration_activities WHERE registration_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, regId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) set.add(rs.getLong(1));
            }
        }
        return set;
    }

    private Registration mapRow(ResultSet rs, Set<Long> acts) throws SQLException {
        return new Registration(
                rs.getLong("id"),
                rs.getLong("event_id"),
                rs.getLong("user_id"),
                acts,
                rs.getTimestamp("registration_date").toLocalDateTime(),
                RegistrationStatus.valueOf(rs.getString("status"))
        );
    }
}
