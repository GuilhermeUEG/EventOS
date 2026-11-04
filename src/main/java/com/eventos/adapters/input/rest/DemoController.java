package com.eventos.adapters.input.rest;

import com.eventos.adapters.input.rest.ApiDtos.DemoAccountResponse;
import io.javalin.Javalin;
import java.util.List;

/**
 * Lista as contas de demonstração para o seletor de perfis do site. É registrado somente quando o modo
 * de demonstração está ativo (variável EVENTOS_DEMO_MODE diferente de "false"); fora dele a rota não existe.
 */
public class DemoController {
    public record Account(String name, String email, String password, String role) {
    }

    private final List<Account> accounts;

    public DemoController(List<Account> accounts) {
        this.accounts = accounts;
    }

    public void register(Javalin app) {
        app.get("/api/demo/accounts", ctx -> ctx.json(accounts.stream()
                .map(a -> new DemoAccountResponse(a.name(), a.email(), a.password(), a.role()))
                .toList()));
    }
}
