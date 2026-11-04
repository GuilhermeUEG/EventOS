package com.eventos.adapters.input.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;

/**
 * Configuração única de JSON da API: datas em ISO-8601 e tolerância a campos desconhecidos.
 */
public final class ApiJson {
    private ApiJson() {
    }

    public static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static JavalinJackson javalinMapper() {
        return new JavalinJackson(mapper(), true);
    }
}
