package com.eventos.application.ports.output;

public interface QrCodeGeneratorPort {
    /** Imagem PNG quadrada com o conteúdo informado. */
    byte[] generatePng(String content, int sizeInPixels);
}
