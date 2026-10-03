# Carrim API

API do Carrim para acompanhar compras de supermercado. Este repositório e `JoaoVFAraujo/carrim-mobile` formam o mesmo produto.

## Estado: 0.0.1 — Foundation

Aplicação Spring Boot que inicializa sem banco ou credenciais, com Maven Wrapper e segurança fechada por padrão. Ainda não existem endpoints de negócio, autenticação por token, persistência ou sincronização.

## Stack e pré-requisitos

- JDK 25 LTS, selecionado por `JAVA_HOME`.
- Spring Boot 4.1.1, Spring MVC, Spring Security e Bean Validation.
- Maven Wrapper; não é necessário instalar Maven globalmente.
- JUnit e suporte de testes Spring MVC/Security.

## Executar e validar

```bash
./mvnw verify
./mvnw spring-boot:run
```

No Windows:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

A porta padrão é 8080. Qualquer rota é negada. GET sem autenticação retorna 401; uma identidade autenticada de teste também é negada, com 403. Requisições de mutação sem CSRF podem retornar 403 antes da autorização.

```bash
./mvnw package
java -jar target/carrim-api-0.0.1.jar
```

Os testes verificam inicialização sem banco, bloqueio de acesso anônimo, rotas desconhecidas e bloqueio de mutação/autenticação simulada.

## Arquitetura

Namespace: `br.com.carrim`. Monólito modular com **uma única Hexagonal**, organizada por camadas:

- `domain`: Java puro, sem Spring, JPA ou HTTP.
- `application`: casos de uso e portas; não depende de adapters.
- `adapter/in`: controllers e DTOs explícitos.
- `adapter/out`: persistência e integrações concretas.
- `config`, `security`, `shared`: apenas responsabilidades transversais reais.

A fundação contém a aplicação e `security/SecurityConfig`. As demais áreas serão criadas quando houver implementação; não existem entidades vazias ou casos de uso fictícios. ArchUnit entrará quando houver dependências de domínio/aplicação que possam ser verificadas de forma útil.

JPA, Flyway, PostgreSQL e Testcontainers entrarão na etapa de persistência. Não há datasource, migrations ou conexão com banco nesta versão. O domínio permanecerá independente dessas dependências.

## Segurança e configuração

- `anyRequest().denyAll()`; nenhuma rota pública.
- Sem login por formulário, HTTP Basic, usuário padrão ou senha gerada.
- Sem sessão HTTP; CSRF preservado até a implementação consciente de bearer tokens.
- Erros não expõem stack traces, mensagens internas ou binding errors.
- Nenhum segredo hardcoded; arquivos `.env` reais são ignorados.
- Não há CORS amplo ou Actuator público.
- Não é necessário definir variáveis de banco nesta fundação.

O runtime Cloud pode exigir proxy e truststore na execução do Maven. Essas configurações pertencem ao ambiente e não devem ser adicionadas ao projeto. Verificação TLS deve permanecer habilitada.

Formatação Java: quatro espaços, LF e newline final, definidos em `.editorconfig`; o código atual segue essa convenção.

## Referência e próximas etapas

[Documento-base v12](docs/carrim-documento-base-v12.md): produto, domínio, arquitetura, segurança, UX e backlog. É uma referência revisável, não um contrato imutável.

A experiência offline do mobile será validada antes de avançar para persistência, APIs de domínio e sincronização. CI, commit, push, PR e deploy não fazem parte desta fundação local.

## Validação local — 30/09/2026

Fundação transferida para `C:\workspace\backend\mercado`, na branch `main`. Java instalado: 21.0.10; Maven global: 3.9.12. Esta API exige JDK 25.

O Maven Wrapper recebeu um ajuste para não indexar uma propriedade `Target` nula em diretórios comuns no Windows PowerShell. O wrapper alcança o download do Maven, mas a conexão remota falha no ambiente de execução. A verificação offline também não consegue resolver o parent Spring Boot 4.1.1 ausente no cache. `verify`, os seis testes e a inicialização do JAR permanecem pendentes no Windows; nenhum resultado do ambiente Linux foi tratado como validação local.

Nenhuma ferramenta global foi instalada ou substituída. Para manter os caches dentro da pasta do projeto durante a validação nesta sessão:

```powershell
$env:MAVEN_USER_HOME = "$PWD\target\maven-user-home"
.\mvnw.cmd "-Dmaven.repo.local=$PWD\target\maven-repository" verify
```

## Formatação Java com Spotless

Spotless usa Palantir Java Format com indentação de quatro espaços, LF e newline final. A verificação de fontes Java de produção e testes faz parte de `verify` e não altera arquivos automaticamente.

No IntelliJ, execute os goals pelo painel Maven usando o JDK 25:

```text
spotless:apply
spotless:check
verify
```

A execução pelo assistente ficou bloqueada por acesso negado ao arquivo `conf/security/java.security` do JDK local. A aplicação da formatação e a validação com Spotless ainda precisam ser executadas no IntelliJ.

## Validação atualizada — 03/10/2026

`mvnw.cmd verify` passou no Windows usando o JDK 25.0.4.1 já instalado, selecionado somente para o processo de validação. Os seis testes passaram, o JAR foi empacotado e o Spotless confirmou a formatação. Não houve alteração de configuração global nem conexão com banco. Esta verificação resolve as pendências de `verify` registradas acima; execução manual do JAR continua sem validação específica.
