package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object que encapsula intervalo de datas/horários e invariantes de tempo (ROO-03, RN-20).
 */
public final class Period implements Serializable {
    private final LocalDateTime start;
    private final LocalDateTime end;

    public Period(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ValidationException("Data inicial e final do período são obrigatórias.");
        }
        if (start.isAfter(end)) {
            throw new ValidationException("A data inicial (" + start + ") não pode ser posterior à data final (" + end + ").");
        }
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public double getDurationHours() {
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    public boolean overlaps(Period other) {
        if (other == null) return false;
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    public boolean contains(LocalDateTime dateTime) {
        if (dateTime == null) return false;
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    public boolean isWithin(Period outer) {
        if (outer == null) return false;
        return !this.start.isBefore(outer.start) && !this.end.isAfter(outer.end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Period period = (Period) o;
        return Objects.equals(start, period.start) && Objects.equals(end, period.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return start + " até " + end;
    }
}
