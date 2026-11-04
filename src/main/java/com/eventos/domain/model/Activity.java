package com.eventos.domain.model;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.policies.AttendanceDecision;
import com.eventos.domain.policies.AttendancePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Entidade de Atividade rica com invariantes de capacidade e política de frequência
 * (ROO-01, ROO-02, RF-05, RF-14, RN-09).
 *
 * <p>A política de frequência é definida na criação e não pode ser trocada depois: mudar a regra
 * de uma atividade já em andamento alteraria silenciosamente a situação de presença consolidada (RN-16).</p>
 */
public class Activity implements Serializable {
    private final Long id;
    private Long eventId;
    private final String title;
    private final String description;
    private final Period period;
    private final ActivityLocation location;
    private final ActivityType type;
    private final int maxCapacity;
    private int currentEnrollments;
    private final List<Speaker> speakers;
    private final AttendancePolicy attendancePolicy;
    private final boolean requiresRegistration;

    public Activity(Long id, Long eventId, String title, String description,
                    Period period, ActivityLocation location, ActivityType type,
                    int maxCapacity, int currentEnrollments, List<Speaker> speakers,
                    AttendancePolicy attendancePolicy, boolean requiresRegistration) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Título da atividade é obrigatório.");
        }
        if (period == null) {
            throw new ValidationException("Período da atividade é obrigatório.");
        }
        if (location == null) {
            throw new ValidationException("Local da atividade é obrigatório.");
        }
        this.id = id;
        this.eventId = eventId;
        this.title = title.trim();
        this.description = description != null ? description.trim() : "";
        this.period = period;
        this.location = location;
        this.type = type != null ? type : ActivityType.LECTURE;
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : location.getCapacity();
        this.currentEnrollments = Math.max(0, currentEnrollments);
        this.speakers = speakers != null ? new ArrayList<>(speakers) : new ArrayList<>();
        this.attendancePolicy = attendancePolicy != null ? attendancePolicy : new SingleCheckInPolicy();
        this.requiresRegistration = requiresRegistration;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    /** Somente o agregado {@link Event} vincula uma atividade a si mesmo. */
    void assignToEvent(Long eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Period getPeriod() {
        return period;
    }

    public ActivityLocation getLocation() {
        return location;
    }

    public ActivityType getType() {
        return type;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getCurrentEnrollments() {
        return currentEnrollments;
    }

    public List<Speaker> getSpeakers() {
        return Collections.unmodifiableList(speakers);
    }

    public AttendancePolicy getAttendancePolicy() {
        return attendancePolicy;
    }

    public boolean isRequiresRegistration() {
        return requiresRegistration;
    }

    public boolean hasAvailableSlots() {
        return !requiresRegistration || currentEnrollments < maxCapacity;
    }

    public int getAvailableSlots() {
        return Math.max(0, maxCapacity - currentEnrollments);
    }

    /** Falha se a atividade controla vagas e já está lotada. */
    public void assertHasAvailableSlot() {
        if (!hasAvailableSlots()) {
            throw new BusinessRuleException("A atividade '" + title + "' atingiu a capacidade máxima (" + maxCapacity + " vagas).");
        }
    }

    public void bookSlot() {
        assertHasAvailableSlot();
        this.currentEnrollments++;
    }

    public void releaseSlot() {
        if (this.currentEnrollments > 0) {
            this.currentEnrollments--;
        }
    }

    public void addSpeaker(Speaker speaker) {
        if (speaker == null) throw new ValidationException("Palestrante não pode ser nulo.");
        this.speakers.add(speaker);
    }

    /** Mesmo local, com horários sobrepostos (RF-07, RN-07). */
    public boolean conflictsWith(Activity other) {
        if (other == null || (this.id != null && Objects.equals(this.id, other.id))) return false;
        return this.location.getRoom().equalsIgnoreCase(other.location.getRoom()) && this.period.overlaps(other.period);
    }

    /** Horários sobrepostos, independentemente do local (conflito na agenda do participante). */
    public boolean overlapsInTimeWith(Activity other) {
        if (other == null || (this.id != null && Objects.equals(this.id, other.id))) return false;
        return this.period.overlaps(other.period);
    }

    public boolean occursOn(LocalDate date) {
        return date != null && !date.isBefore(period.getStart().toLocalDate()) && !date.isAfter(period.getEnd().toLocalDate());
    }

    public AttendanceStatus evaluateAttendance(List<AttendanceRecord> records) {
        return this.attendancePolicy.evaluate(AttendanceRecord.effective(records));
    }

    public AttendanceDecision decideQrMark(List<AttendanceRecord> records, LocalDateTime now) {
        return this.attendancePolicy.decideQrMark(AttendanceRecord.effective(records), now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Activity activity = (Activity) o;
        return id != null && Objects.equals(id, activity.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
