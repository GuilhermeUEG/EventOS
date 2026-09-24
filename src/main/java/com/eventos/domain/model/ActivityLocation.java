package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object para localização física ou virtual de atividade (ROO-03).
 */
public final class ActivityLocation implements Serializable {
    private final String room;
    private final String spaceOrTrack;
    private final int capacity;

    public ActivityLocation(String room, String spaceOrTrack, int capacity) {
        if (room == null || room.trim().isEmpty()) {
            throw new ValidationException("O local/sala da atividade é obrigatório.");
        }
        if (capacity <= 0) {
            throw new ValidationException("A capacidade da sala deve ser maior que zero.");
        }
        this.room = room.trim();
        this.spaceOrTrack = (spaceOrTrack == null || spaceOrTrack.trim().isEmpty()) ? "Geral" : spaceOrTrack.trim();
        this.capacity = capacity;
    }

    public String getRoom() {
        return room;
    }

    public String getSpaceOrTrack() {
        return spaceOrTrack;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActivityLocation that = (ActivityLocation) o;
        return capacity == that.capacity && Objects.equals(room, that.room) && Objects.equals(spaceOrTrack, that.spaceOrTrack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(room, spaceOrTrack, capacity);
    }

    @Override
    public String toString() {
        return room + " (Trilha/Espaço: " + spaceOrTrack + ", Cap: " + capacity + ")";
    }
}
