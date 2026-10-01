# Arquitetura do EventOS

## Fluxo em execução

```text
Site HTML/JS ─┐
              ├─ HTTP/JSON ─> API Javalin ─> Portas de entrada ─> Serviços de aplicação
Desktop Swing ┘                                              │
                                                              ├─ Domínio
                                                              └─ Portas de saída
                                                                 ├─ JDBC/H2
                                                                 ├─ OpenPDF
                                                                 ├─ PBKDF2/HMAC
                                                                 └─ Sessões em memória
```

O site e o desktop utilizam a mesma API REST. Somente os adaptadores JDBC acessam o
banco H2. `EventosApplication` é o *composition root*: cria adaptadores, casos de uso,
inicia o servidor e, quando existe ambiente gráfico, inicia o cliente Swing.

## Limites

- `domain.model`, `domain.policies`, `domain.services`: regras e invariantes sem dependência de interface.
- `application.ports.input`: casos de uso oferecidos pelos serviços.
- `application.ports.output`: persistência, segurança, PDF e sessão.
- `application.services`: coordenação dos casos de uso.
- `adapters.input.rest`: contrato HTTP e autorização.
- `adapters.input.desktop`: cliente HTTP Swing.
- `adapters.output`: JDBC, PDF, PBKDF2/HMAC e sessões.

## Segurança

- Cadastro público sempre cria `PARTICIPANT`.
- Login gera token opaco de 256 bits com duração de oito horas.
- A API verifica identidade, perfil e responsabilidade pelo evento.
- Senhas usam PBKDF2-HMAC-SHA256 com salt aleatório e 120.000 iterações.
- QR usa HMAC, não contém dado pessoal e expira em 15 minutos.

## Padrões

- Strategy: frequência e elegibilidade de certificado.
- Factory: criação das políticas de frequência.
- Ports and Adapters: REST/Swing/JDBC/PDF/segurança.
- Domain Service: validação de conflitos.
