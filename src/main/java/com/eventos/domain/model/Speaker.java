package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.Objects;

/**
 * Representa uma pessoa vinculada a uma atividade com papel específico (RF-08, RN-02).
 */
public final class Speaker implements Serializable {
    private final Long id;
    private final String name;
    private final String roleInActivity; // Palestrante, Apresentador, Moderador, etc.
    private final String bio;
    private final String photoUrl; // Opcional conforme RN-02

    public Speaker(Long id, String name, String roleInActivity, String bio, String photoUrl) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Nome do palestrante/responsável é obrigatório.");
        }
        this.id = id;
        this.name = name.trim();
        this.roleInActivity = (roleInActivity == null || roleInActivity.trim().isEmpty()) ? "Palestrante" : roleInActivity.trim();
        this.bio = bio != null ? bio.trim() : "";
        this.photoUrl = (photoUrl != null && !photoUrl.trim().isEmpty()) ? photoUrl.trim() : null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRoleInActivity() {
        return roleInActivity;
    }

    public String getBio() {
        return bio;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Speaker speaker = (Speaker) o;
        return Objects.equals(name, speaker.name) && Objects.equals(roleInActivity, speaker.roleInActivity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, roleInActivity);
    }
}
