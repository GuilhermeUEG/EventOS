package com.eventos.application.dtos;

/**
 * QR Code de frequência de uma atividade: conteúdo assinado (sem dado pessoal), imagem PNG e validade.
 */
public record QrCodeDto(Long activityId, String token, byte[] pngImage, long validForSeconds) {
}
