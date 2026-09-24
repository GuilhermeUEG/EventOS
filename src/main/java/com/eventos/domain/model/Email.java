package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object representando um e-mail válido (ROO-03, RN-01).
 */
public final class Email implements Serializable {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final String value;

    public Email(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("E-mail não pode ser vazio.");
        }
        String normalized = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Formato de e-mail inválido: " + value);
        }
        this.value = normalized;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
