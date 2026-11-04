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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JdbcRegistrationRepository implements RegistrationRepository {

    @Override
    public Registration save(Registration reg) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Registration saved = reg.getId() == null ? insert(conn, reg) : update(conn, reg);
                conn.commit();
                return saved;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao salvar inscrição: " + e.getMessage(), e);
        }
    }

    private Registration insert(Connection conn, Registration reg) throws SQLException {
        String sql = "INSERT INTO registrations (event_id, user_id, registration_date, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, reg.getEventId());
            stmt.setLong(2, reg.getUserId());
            stmt.setTimestamp(3, Timestamp.valueOf(reg.getRegistrationDate()));
            stmt.setString(4, reg.getStatus().name());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long regId = rs.getLong(1);
                    saveActivities(conn, regId, reg.getSelectedActivityIds());
                    return new Registration(regId, reg.getEventId(), reg.getUserId(), reg.getSelectedActivityIds(),
                            reg.getRegistrationDate(), reg.getStatus());
                }
            }
        }
        return reg;
    }

    private Registration update(Connection conn, Registration reg) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE registrations SET status = ?, registration_date = ? WHERE id = ?")) {
            stmt.setString(1, reg.getStatus().name());
            stmt.setTimestamp(2, Timestamp.valueOf(reg.getRegistrationDate()));
            stmt.setLong(3, reg.getId());
            stmt.executeUpdate();
        }
        saveActivities(conn, reg.getId(), reg.getSelectedActivityIds());
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
            throw new IllegalStateException("Erro ao buscar inscrição: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Registration> findByEventId(Long eventId) {
        String sql = "SELECT * FROM registrations WHERE event_id = ? ORDER BY registration_date ASC, id ASC";
        String activitiesSql = "SELECT ra.registration_id, ra.activity_id FROM registration_activities ra "
                + "JOIN registrations r ON r.id = ra.registration_id WHERE r.event_id = ?";
        return readAll(sql, activitiesSql, eventId, "Erro ao listar inscrições do evento");
    }

    @Override
    public List<Registration> findByUserId(Long userId) {
        String sql = "SELECT * FROM registrations WHERE user_id = ? ORDER BY registration_date DESC, id DESC";
        String activitiesSql = "SELECT ra.registration_id, ra.activity_id FROM registration_activities ra "
                + "JOIN registrations r ON r.id = ra.registration_id WHERE r.user_id = ?";
        return readAll(sql, activitiesSql, userId, "Erro ao listar inscrições do participante");
    }

    /** Lê as inscrições e suas atividades escolhidas com apenas duas consultas. */
    private List<Registration> readAll(String sql, String activitiesSql, Long param, String errorMessage) {
        try (Connection conn = DatabaseManager.getConnection()) {
            Map<Long, Set<Long>> activitiesByRegistration = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(activitiesSql)) {
                stmt.setLong(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        activitiesByRegistration.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getLong(2));
                    }
                }
            }
            List<Registration> list = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs, activitiesByRegistration.getOrDefault(rs.getLong("id"), new HashSet<>())));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException(errorMessage + ": " + e.getMessage(), e);
        }
    }

    @Override
    public int countConfirmedByEventId(Long eventId) {
        return count("SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'", eventId,
                "Erro ao contar inscritos");
    }

    @Override
    public int countConfirmedByActivityId(Long activityId) {
        return count("SELECT COUNT(*) FROM registration_activities ra JOIN registrations r ON r.id = ra.registration_id "
                + "WHERE ra.activity_id = ? AND r.status = 'CONFIRMED'", activityId, "Erro ao contar inscritos na atividade");
    }

    private int count(String sql, Long param, String errorMessage) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(errorMessage + ": " + e.getMessage(), e);
        }
        return 0;
    }

    private void saveActivities(Connection conn, Long regId, Set<Long> actIds) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM registration_activities WHERE registration_id = ?")) {
            del.setLong(1, regId);
            del.executeUpdate();
        }
        if (actIds == null || actIds.isEmpty()) return;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO registration_activities (registration_id, activity_id) VALUES (?, ?)")) {
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
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT activity_id FROM registration_activities WHERE registration_id = ?")) {
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
