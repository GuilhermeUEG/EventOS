package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class JdbcAttendanceRepository implements AttendanceRepository {

    @Override
    public AttendanceRecord save(AttendanceRecord record) {
        String sql = "INSERT INTO attendance_records (activity_id, user_id, timestamp, type, recorded_by, notes) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, record.getActivityId());
            stmt.setLong(2, record.getUserId());
            stmt.setTimestamp(3, Timestamp.valueOf(record.getTimestamp()));
            stmt.setString(4, record.getType().name());
            stmt.setString(5, record.getRecordedBy());
            stmt.setString(6, record.getNotes());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new AttendanceRecord(rs.getLong(1), record.getActivityId(), record.getUserId(),
                            record.getTimestamp(), record.getType(), record.getRecordedBy(), record.getNotes());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar registro de frequência: " + e.getMessage(), e);
        }
        return record;
    }

    @Override
    public List<AttendanceRecord> findByActivityId(Long activityId) {
        List<AttendanceRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance_records WHERE activity_id = ? ORDER BY timestamp ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar frequências: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<AttendanceRecord> findByActivityAndUser(Long activityId, Long userId) {
        List<AttendanceRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance_records WHERE activity_id = ? AND user_id = ? ORDER BY timestamp ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar frequências de participante: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<AttendanceRecord> findByUserAndEvent(Long userId, Long eventId) {
        List<AttendanceRecord> list = new ArrayList<>();
        String sql = "SELECT ar.* FROM attendance_records ar JOIN activities a ON a.id = ar.activity_id WHERE ar.user_id = ? AND a.event_id = ? ORDER BY ar.timestamp ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar frequências do evento: " + e.getMessage(), e);
        }
        return list;
    }

    private AttendanceRecord mapRow(ResultSet rs) throws SQLException {
        return new AttendanceRecord(
                rs.getLong("id"),
                rs.getLong("activity_id"),
                rs.getLong("user_id"),
                rs.getTimestamp("timestamp").toLocalDateTime(),
                AttendanceType.valueOf(rs.getString("type")),
                rs.getString("recorded_by"),
                rs.getString("notes")
        );
    }
}
