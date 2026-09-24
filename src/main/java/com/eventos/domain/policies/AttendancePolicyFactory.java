package com.eventos.domain.policies;

public class AttendancePolicyFactory {
    public static AttendancePolicy create(String key) {
        if (key == null) return new SingleCheckInPolicy();
        return switch (key.toUpperCase().trim()) {
            case "CHECKIN_CHECKOUT", "ENTRADA_SAIDA" -> new CheckInCheckOutPolicy();
            case "MANUAL_ONLY", "MANUAL" -> new ManualOnlyPolicy();
            default -> new SingleCheckInPolicy();
        };
    }
}
