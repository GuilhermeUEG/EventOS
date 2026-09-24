package com.eventos.application.ports.output;

public interface SecurityPort {
    String hashPassword(String plainPassword);
    boolean verifyPassword(String plainPassword, String hashedPassword);
    String generateToken(String subject);
    String generateQrToken(Long activityId);
    Long validateQrToken(String token);
}
