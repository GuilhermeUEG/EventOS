package com.eventos.adapters.input.rest;

import com.eventos.adapters.input.rest.ApiDtos.ErrorResponse;
import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.exceptions.DomainException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.ForbiddenException;
import com.eventos.domain.exceptions.UnauthorizedException;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduz exceções em respostas HTTP com mensagem compreensível (RNF-07). Falhas de regra de negócio
 * são registradas como aviso; falhas inesperadas são registradas como erro, sem vazar detalhes ao cliente.
 */
final class ApiExceptionMapper {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionMapper.class);

    private ApiExceptionMapper() {
    }

    static void register(Javalin app) {
        app.exception(DomainException.class, (e, ctx) -> {
            log.warn("{} {} -> {}", ctx.method(), ctx.path(), e.getMessage());
            ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse(e.getMessage()));
        });
        app.exception(ConflictException.class, (e, ctx) -> {
            log.warn("{} {} -> conflito: {}", ctx.method(), ctx.path(), e.getMessage());
            ctx.status(HttpStatus.CONFLICT).json(new ErrorResponse(e.getMessage()));
        });
        app.exception(EntityNotFoundException.class, (e, ctx) ->
                ctx.status(HttpStatus.NOT_FOUND).json(new ErrorResponse(e.getMessage())));
        app.exception(UnauthorizedException.class, (e, ctx) ->
                ctx.status(HttpStatus.UNAUTHORIZED).json(new ErrorResponse(e.getMessage())));
        app.exception(ForbiddenException.class, (e, ctx) -> {
            log.warn("Acesso negado a {} {}: {}", ctx.method(), ctx.path(), e.getMessage());
            ctx.status(HttpStatus.FORBIDDEN).json(new ErrorResponse(e.getMessage()));
        });
        app.exception(HttpResponseException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(new ErrorResponse(e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Falha inesperada em {} {}", ctx.method(), ctx.path(), e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(new ErrorResponse("Ocorreu um erro interno. Tente novamente ou procure o suporte."));
        });
    }
}
