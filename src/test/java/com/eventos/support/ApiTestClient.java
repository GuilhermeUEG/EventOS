package com.eventos.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Cliente HTTP mínimo para testar a API REST como um cliente real faria.
 */
public final class ApiTestClient {
    public record Response(int status, String body, HttpResponse<byte[]> raw) {
        public JsonNode json() {
            try {
                return new ObjectMapper().readTree(body);
            } catch (Exception e) {
                throw new IllegalStateException("Resposta não é JSON: " + body, e);
            }
        }

        public String error() {
            return json().path("error").asText();
        }
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiTestClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Response get(String path, String token) {
        return send("GET", path, token, null);
    }

    public Response post(String path, String token, Object body) {
        return send("POST", path, token, body);
    }

    public Response put(String path, String token, Object body) {
        return send("PUT", path, token, body);
    }

    public Response delete(String path, String token) {
        return send("DELETE", path, token, null);
    }

    /** Envia texto cru como corpo, para testar JSON malformado. */
    public Response postRaw(String path, String token, String rawBody) {
        return execute("POST", path, token, rawBody);
    }

    public Response send(String method, String path, String token, Object body) {
        try {
            return execute(method, path, token, body == null ? null : mapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Response execute(String method, String path, String token, String rawBody) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (rawBody == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(rawBody));
            }
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8), response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Faz login e devolve o token da sessão. */
    public String login(String email, String password) {
        Response r = post("/api/auth/login", null, Map.of("email", email, "password", password));
        if (r.status() != 200) throw new IllegalStateException("Login falhou: " + r.body());
        return r.json().path("token").asText();
    }
}
