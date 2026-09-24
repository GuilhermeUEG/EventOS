package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.policies.AttendancePolicyFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcActivityRepository implements ActivityRepository {

    @Override
    public Activity save(Activity activity) {
        if (activity.getId() == null) {
            String sql = "INSERT INTO activities (event_id, title, description, start_date, end_date, room, space_or_track, capacity, current_enrollments, type, attendance_policy, requires_registration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, activity.getEventId());
                stmt.setString(2, activity.getTitle());
                stmt.setString(3, activity.getDescription());
                stmt.setTimestamp(4, Timestamp.valueOf(activity.getPeriod().getStart()));
                stmt.setTimestamp(5, Timestamp.valueOf(activity.getPeriod().getEnd()));
                stmt.setString(6, activity.getLocation().getRoom());
                stmt.setString(7, activity.getLocation().getSpaceOrTrack());
                stmt.setInt(8, activity.getLocation().getCapacity());
                stmt.setInt(9, activity.getCurrentEnrollments());
                stmt.setString(10, activity.getType().name());
                stmt.setString(11, activity.getAttendancePolicy().getPolicyKey());
                stmt.setBoolean(12, activity.isRequiresRegistration());
                stmt.executeUpdate();
                
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long actId = rs.getLong(1);
                        saveSpeakers(conn, actId, activity.getSpeakers());
                        return findById(actId).orElse(activity);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao inserir atividade: " + e.getMessage(), e);
            }
        } else {
            String sql = "UPDATE activities SET title = ?, description = ?, start_date = ?, end_date = ?, room = ?, space_or_track = ?, capacity = ?, current_enrollments = ?, type = ?, attendance_policy = ?, requires_registration = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, activity.getTitle());
                stmt.setString(2, activity.getDescription());
                stmt.setTimestamp(3, Timestamp.valueOf(activity.getPeriod().getStart()));
                stmt.setTimestamp(4, Timestamp.valueOf(activity.getPeriod().getEnd()));
                stmt.setString(6, activity.getLocation().getRoom());
                stmt.setString(7, activity.getLocation().getSpaceOrTrack());
                stmt.setInt(8, activity.getLocation().getCapacity());
                stmt.setInt(9, activity.getCurrentEnrollments());
                stmt.setString(10, activity.getType().name());
                stmt.setString(11, activity.getAttendancePolicy().getPolicyKey());
                stmt.setBoolean(12, activity.isRequiresRegistration());
                stmt.setLong(13, activity.getId());
                stmt.executeUpdate();
                saveSpeakers(conn, activity.getId(), activity.getSpeakers());
                return findById(activity.getId()).orElse(activity);
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao atualizar atividade: " + e.getMessage(), e);
            }
        }
        return activity;
    }

    @Override
    public Optional<Activity> findById(Long id) {
        String sql = "SELECT * FROM activities WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    List<Speaker> speakers = findSpeakersByActivityId(conn, id);
                    return Optional.of(mapRow(rs, speakers));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar atividade: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Activity> findByEventId(Long eventId) {
        List<Activity> list = new ArrayList<>();
        String sql = "SELECT * FROM activities WHERE event_id = ? ORDER BY start_date ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    List<Speaker> speakers = findSpeakersByActivityId(conn, rs.getLong("id"));
                    list.add(mapRow(rs, speakers));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar atividades do evento: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Activity> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        List<Activity> list = new ArrayList<>();
        for (Long id : ids) {
            findById(id).ifPresent(list::add);
        }
        return list;
    }

    @Override
    public void updateEnrollments(Long activityId, int currentEnrollments) {
        String sql = "UPDATE activities SET current_enrollments = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.max(0, currentEnrollments));
            stmt.setLong(2, activityId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar contagem de vagas: " + e.getMessage(), e);
        }
    }

    private void saveSpeakers(Connection conn, Long activityId, List<Speaker> speakers) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM speakers WHERE activity_id = ?")) {
            del.setLong(1, activityId);
            del.executeUpdate();
        }
        if (speakers == null || speakers.isEmpty()) return;
        String sql = "INSERT INTO speakers (activity_id, name, role_in_activity, bio, photo_url) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Speaker s : speakers) {
                stmt.setLong(1, activityId);
                stmt.setString(2, s.getName());
                stmt.setString(3, s.getRoleInActivity());
                stmt.setString(4, s.getBio());
                stmt.setString(5, s.getPhotoUrl());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private List<Speaker> findSpeakersByActivityId(Connection conn, Long activityId) throws SQLException {
        List<Speaker> list = new ArrayList<>();
        String sql = "SELECT * FROM speakers WHERE activity_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Speaker(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("role_in_activity"),
                            rs.getString("bio"),
                            rs.getString("photo_url")
                    ));
                }
            }
        }
        return list;
    }

    private Activity mapRow(ResultSet rs, List<Speaker> speakers) throws SQLException {
        return new Activity(
                rs.getLong("id"),
                rs.getLong("event_id"),
                rs.getString("title"),
                rs.getString("description"),
                new Period(rs.getTimestamp("start_date").toLocalDateTime(), rs.getTimestamp("end_date").toLocalDateTime()),
                new ActivityLocation(rs.getString("room"), rs.getString("space_or_track"), rs.getInt("capacity")),
                ActivityType.valueOf(rs.getString("type")),
                rs.getInt("capacity"),
                rs.getInt("current_enrollments"),
                speakers,
                AttendancePolicyFactory.create(rs.getString("attendance_policy")),
                rs.getBoolean("requires_registration")
        );
    }
}
