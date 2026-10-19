# Carrim API

Backend do Carrim, aplicativo de supermercado offline-first. Mobile: [carrim-mobile](https://github.com/JoaoVFAraujo/carrim-mobile).

Manifest **0.0.8**, com APIs protegidas e sync push/pull integrados para navegador e app nativo. Não representa release V1.0.0 ou produção pública.

## Documentação essencial

| Assunto                         | Fonte principal                                                                                        |
| ------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Produto, regras e arquitetura   | [Projeto](docs/PROJETO.md)                                                                             |
| Credenciais, dados e produção   | [Segurança](docs/SEGURANCA.md)                                                                         |
| REST e sincronização            | [API](docs/API.md)                                                                                     |
| Onde paramos, métricas e ideias | [Roadmap](docs/ROADMAP.md)                                                                             |
| Direção visual e quatro imagens | [Layout no mobile](https://github.com/JoaoVFAraujo/carrim-mobile/blob/main/docs/LAYOUT.md)             |
| Teste físico                    | [Validação Android](https://github.com/JoaoVFAraujo/carrim-mobile/blob/main/docs/VALIDACAO-ANDROID.md) |
| Histórico de versões            | [Changelog](CHANGELOG.md)                                                                              |

Ao retomar, ler este README, AGENTS.md e roadmap. Atualizar o documento do assunto alterado; não criar registros por sessão nem copiar status entre arquivos. Histórico fica no Git.

## Executar localmente

JDK 25 completo, PostgreSQL 18 e Maven Wrapper incluído. API na porta 8082, frontend na 4202. O banco local `carrim` deve existir. Configure no ambiente ou na execução do IntelliJ:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/carrim
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<definir somente no ambiente>
```

`postgres` é o usuário local, não uma recomendação para produção. O terminal não herda variáveis do IntelliJ; .env não é carregado automaticamente. Para limitar o servidor a esta máquina, definir `SERVER_ADDRESS=127.0.0.1`.

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS: `./mvnw spring-boot:run`. Flyway aplica V1–V6 no schema carrim; Hibernate apenas valida. A aplicação não cria banco/usuário. Não modificar migrations aplicadas nem usar clean/reset para resolver divergência.

## Validar e empacotar

Com Docker, os testes usam PostgreSQL descartável:

```powershell
.\mvnw.cmd verify
```

Neste Windows, sem Docker, usar os binários instalados de PostgreSQL 18 e JDK 25:

```powershell
.\scripts\verify-local-postgres.ps1
```

O script cria cluster exclusivo em `target/postgres-tests`, banco/usuário carrim_test, porta temporária e somente loopback; encerra a instância no fim. Trust é exclusivo dessa instância. Não usa 5432 nem credenciais do banco pessoal. Parâmetros `-PostgresBin` e `-JavaHome` ajustam instalações diferentes.

Spotless e ArchUnit fazem parte de verify. Correção de formatação: `mvnw spotless:apply`. Não trocar ferramentas globais apenas para retomar.

```powershell
.\mvnw.cmd package
java -jar target/carrim-api-0.0.8.jar
```

Para validar o mobile junto, usar `.\scripts\verify-local-postgres.ps1 -MobileSync` (ajustar `-MobileRoot` se necessário). O mesmo cluster isolado recebe uma API temporária em loopback; o script executa testes HTTP web/nativo e encerra somente esses processos. Sem -MobileSync, permanece a validação exclusiva do backend. Nunca usar dados pessoais ou produção.

Web: servir `/api/v1` pela mesma origem do frontend. Os POSTs `/auth/web/prepare` e `/auth/web/token` mantêm a prova da instalação em cookie HttpOnly e devolvem Bearer para memória. Rotas de negócio continuam exigindo Bearer e ignorando cookies. No perfil prod, bootstrap web exige HTTPS; o proxy confiável deve preservar a origem pública e informar o esquema corretamente. Não confiar em cabeçalhos encaminhados de clientes externos.

CI em [.github/workflows/verify.yml](.github/workflows/verify.yml) executa verify com Java 25 e banco descartável. Não publica infraestrutura. Perfil prod e imagem Docker exigem os controles de [Segurança](docs/SEGURANCA.md).

## Governança

Preservar alterações locais. Branch feature/fix da main, PR com comentário exato `@codex review`, correções, merge commit e exclusão da branch concluída, conforme autorização do usuário e AGENTS.md. Um documento não autoriza commit, push, PR, merge ou deploy por si só.
