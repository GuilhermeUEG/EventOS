# Documentação Auxiliar - EventOS

Este documento registra as decisões tecnológicas, escolhas de design e explicações de arquitetura do projeto **EventOS** para a disciplina de Programação Orientada a Objetos II (POO II).

---

## 1. Escolha da Ferramenta de Build: Gradle vs. Maven

### Motivação
O núcleo do software EventOS é desenvolvido em Java 21, conforme exigência do regulamento (RNF-03). Para gerenciar bibliotecas externas (Javalin, Jackson, H2 Database e OpenPDF) e o ciclo de vida do software, adotamos o **Gradle** em vez do **Maven** ou do gerenciamento manual de dependências.

### Justificativas Técnicas (Decisão D-01/D-08)
1.  **Gradle Wrapper (`gradlew.bat`)**:
    *   Permite a compilação e execução imediata do projeto em qualquer máquina sem a necessidade de pré-instalar o Gradle ou configurar variáveis de ambiente do sistema (`PATH`). O próprio script do wrapper baixa a versão correta do Gradle de forma automatizada na primeira execução.
2.  **Sintaxe Concisa (Groovy/Kotlin DSL)**:
    *   Diferente do Maven que usa arquivos XML (`pom.xml`) extremamente verbosos e difíceis de ler, o arquivo [build.gradle](file:///C:/Users/Guilherme/Documents/EventOS/build.gradle) utiliza uma linguagem declarativa limpa que facilita adições de novas dependências e a customização de tarefas.
3.  **Compilação Incremental e Desempenho**:
    *   O Gradle rastreia as entradas e saídas de cada tarefa de build e reconstrói apenas o que foi alterado. Aliado ao processo em segundo plano (*Daemon*), torna o tempo de compilação significativamente menor que o do Maven.

---

## 2. Escolha da Arquitetura: Hexagonal (Ports & Adapters)

### Motivação
A exigência principal do projeto (RNF-01 e ROO-09) é manter as regras de negócio centrais blindadas e independentes do banco de dados e das interfaces visuais.

### Estrutura do EventOS (Decisão D-06/D-07)
O projeto está dividido em pacotes que isolam a aplicação:
*   **Domínio (`com.eventos.domain`)**:
    *   Contém as entidades puras do Java (`Event`, `Activity`, `Participant`). Não há frameworks aqui. Toda lógica de validação de regras de negócio (ex: detecção de conflitos de horários em salas) é mantida diretamente nas classes de domínio.
*   **Aplicação (`com.eventos.application`)**:
    *   Define os casos de uso do sistema (`EventUseCase`) e as portas de entrada e saída (*Ports*). As portas de saída são interfaces que descrevem a comunicação externa (ex: `EventRepository` para salvar dados).
*   **Adaptadores (`com.eventos.adapters`)**:
    *   Implementações tecnológicas concretas (*Adapters*).
    *   *Entrada (REST API - Javalin)*: Micro-serviço web Javalin que expõe endpoints HTTP para o site público.
    *   *Entrada (Desktop - Swing)*: Interface desktop administrativa nativa escrita em Java Swing.
    *   *Saída (Persistência - JDBC)*: Implementação do repositório utilizando conexões JDBC puras e banco de dados relacional H2, realizando queries SQL brutas transacionadas.

---

## 3. Decisão de Design: Remoção do Spring Boot (Transição para Stack Leve)

### Por que removemos o Spring Boot?
Atendendo à diretriz de avaliação, removemos o ecossistema Spring Boot. A fiação manual de componentes e o acesso direto ao banco foram implementados para tornar o design orientado a objetos explícito e didático.

### Como funciona sem o Spring Boot?
1.  **Injeção de Dependências Manual**:
    *   Ao invés de deixar o Spring gerenciar os beans em segundo plano (`@Autowired`, `@Component`), as dependências são instanciadas e conectadas explicitamente no método `main` da classe [EventosApplication.java](file:///C:/Users/Guilherme/Documents/EventOS/src/main/java/com/eventos/EventosApplication.java). Isso melhora o entendimento do fluxo de controle.
2.  **API REST com Javalin**:
    *   Adotamos o **Javalin 6.x**, um framework extremamente leve rodando sob o Jetty embarcado. Ele oferece roteamento rápido e limpo sem a sobrecarga ou anotações complexas de controllers do Spring Boot.
3.  **Persistência com JDBC Puro (Java Database Connectivity)**:
    *   Substituímos o Spring Data JPA (Hibernate) por prepared statements SQL nativos. O [DatabaseManager.java](file:///C:/Users/Guilherme/Documents/EventOS/src/main/java/com/eventos/adapters/output/persistence/DatabaseManager.java) cria as tabelas automaticamente na inicialização e o [EventPersistenceAdapter.java](file:///C:/Users/Guilherme/Documents/EventOS/src/main/java/com/eventos/adapters/output/persistence/EventPersistenceAdapter.java) gerencia a gravação de forma transacional (`setAutoCommit(false)`), garantindo atomicidade e isolamento ao persistir um Evento e suas Atividades.

---

## 4. Compatibilidade Headless (Swing em Ambientes de Servidor/Teste)

### O Problema
A classe `SwingDesktopApp` estende `JFrame` do Java Swing. Ao rodar testes automatizados ou iniciar a aplicação em servidores em nuvem (onde não há ambiente gráfico/monitor disponível), a JVM tenta inicializar componentes gráficos e lança o erro `java.awt.HeadlessException`.

### A Solução Implementada
No método `main` de [EventosApplication.java](file:///C:/Users/Guilherme/Documents/EventOS/src/main/java/com/eventos/EventosApplication.java), verificamos se o ambiente é headless chamando `!GraphicsEnvironment.isHeadless()` antes de inicializar o Swing na Thread de Despacho de Eventos (EDT). Desta forma, o servidor Javalin pode rodar perfeitamente em modo somente-API no servidor de testes do professor, enquanto a interface Swing abre normalmente em ambientes locais interativos.
