# Documentação auxiliar — EventOS

Escolhas de ferramentas e de execução do projeto de POO II. As decisões arquiteturais D-01 a D-08, as refatorações e a
rastreabilidade ROO estão em [docs/DECISOES.md](docs/DECISOES.md); a arquitetura em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

## 1. Ferramenta de build: Gradle (com *wrapper*)

O núcleo é Java 21 (RNF-03). Para bibliotecas (Javalin, Jackson, H2, OpenPDF, ZXing) e ciclo de vida usamos **Gradle** em vez de Maven.

- **Wrapper (`gradlew`/`gradlew.bat`):** compila e executa em qualquer computador sem instalar Gradle nem configurar `PATH`; o próprio script baixa a versão certa na primeira execução. Só é preciso ter um **JDK 21+** e o `JAVA_HOME` apontando para ele.
- **Arquivo declarativo curto:** [build.gradle](build.gradle) lê-se melhor que um `pom.xml` e facilita adicionar dependências e configurar a tarefa de teste.
- **Compilação incremental** e *daemon*, que deixam os ciclos de teste mais rápidos.

Problema comum em outro computador: o `gradlew` usa o `JAVA_HOME`. Se ele apontar para um JDK antigo (por exemplo, 8), o build falha com
*"invalid source release: 21"* ou *"requires JVM 17+"*. A correção é apontar `JAVA_HOME` para um JDK 21 ou superior.

## 2. Sem Spring Boot

Por orientação do professor, **não usamos Spring Boot** (nem Spring em qualquer parte; um teste automático garante que nenhum arquivo importa `org.springframework`).
Em troca:

1. **Injeção de dependências manual** em [EventosApplication](src/main/java/com/eventos/EventosApplication.java): o *composition root* cria os adaptadores, os casos de uso e a API e os liga pelas portas. As dependências ficam à vista, o que ajuda a explicar a arquitetura.
2. **API REST com Javalin 6** (Jetty embarcado): roteamento explícito, sem anotações de controller. Os controllers são pequenos e por área.
3. **Persistência com JDBC puro** e SQL explícito. O esquema vem de scripts versionados em [db/migration](src/main/resources/db/migration), aplicados por um migrador próprio ([SchemaMigrator](src/main/java/com/eventos/adapters/output/persistence/SchemaMigrator.java)).

## 3. Execução sem interface gráfica (modo *headless*)

O painel desktop só abre quando existe ambiente gráfico (`GraphicsEnvironment.isHeadless()`); caso contrário a aplicação sobe apenas a API e o
site, sem erro. Também é possível pedir isso explicitamente com `--no-desktop`. É o que permite executar o projeto em servidores e em
máquinas de avaliação sem monitor.

## 4. Testes isolados do banco da aplicação

Os testes usam H2 **em memória**: o Gradle define a propriedade `eventos.db.url` para a tarefa `test`
([build.gradle](build.gradle)). Assim, rodar a suíte nunca altera o arquivo `eventosdb` usado pela aplicação, e a ordem dos testes não importa
(cada teste cria usuários com e-mails únicos). A mesma propriedade (ou a variável `EVENTOS_DB_URL`) permite apontar a aplicação para outro banco.

## 5. Mudanças de rumo registradas

| Mudança | Motivo |
|---|---|
| Unificação do modelo de domínio (`com.eventos.domain.model`) | Existiam duas representações concorrentes e o projeto não compilava |
| Autorização no servidor | A interface simulava perfis no navegador; agora há sessão opaca e verificação de perfil e de posse do evento na API |
| PBKDF2 com sal aleatório | Substituiu SHA-256 com sal fixo |
| Desktop como cliente HTTP puro, sem importar o domínio | Garante que site e desktop usam a mesma API (RNF-02) |
| QR Code gerado localmente (ZXing) | A versão anterior enviava o conteúdo do QR a um serviço externo |
| Migrações de esquema versionadas | A criação do banco ficava embutida no código |

O detalhamento (problema, alternativa, efeito) está em [docs/DECISOES.md](docs/DECISOES.md).
