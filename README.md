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

Consulta autenticada de sugestões por código: `GET /api/v1/products/lookup/{code}`. Fonte externa Open Food Facts com cache e limites; nenhuma gravação na consulta. Em prod, fonte externa desabilitada por padrão até homologação/licenças. Contrato e configuração em [API](docs/API.md) e [Segurança](docs/SEGURANCA.md).

Fotos persistentes e moderação: ficha/Carrinho usam imagens locais, fila de mídia separada e candidaturas públicas somente após aprovação. Painel no mobile em `/admin/fotos`; hash da chave configurado no ambiente `CARRIM_PHOTO_ADMIN_TOKEN_SHA256`, sem credenciais padrão ou no APK. Contratos e limites em API/Segurança.

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

Linux/macOS: `./mvnw spring-boot:run`. Flyway aplica V1–V7 no schema carrim; Hibernate apenas valida. A aplicação não cria banco/usuário. Não modificar migrations aplicadas nem usar clean/reset para resolver divergência.

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


## Render + Supabase (homologação gratuita)

A imagem Docker agora compila a aplicação a partir do Git com JDK 25; não depende de um JAR do PC. Publicar somente commits integrados por PR e aprovados no CI. Render: serviço Docker Free, uma instância, região Virginia, perfis `prod,render`, `PORT` fornecida pela plataforma e HTTP health check `/healthz`. Desativar deploy automático inicialmente. O contorno de HTTPS do perfil render presume exclusivamente o ingresso TLS gerenciado da plataforma; nunca ativar esse perfil em servidor HTTP diretamente exposto. Cabeçalhos de IP encaminhado continuam ignorados.

Configurar no ambiente do serviço, sem Git/chat/logs: `SPRING_DATASOURCE_URL` (JDBC do Session Pooler IPv4, banco postgres, `sslmode=verify-full` e `sslrootcert=/app/supabase-ca.crt`), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; credenciais separadas de Flyway em `CARRIM_MIGRATION_USERNAME` e `CARRIM_MIGRATION_PASSWORD`. Perfil Render limita o pool a três conexões. Chave de administração: somente o hash SHA-256 em `CARRIM_PHOTO_ADMIN_TOKEN_SHA256`, chave original guardada pelo operador. Habilitar consulta externa explicitamente com `CARRIM_CATALOG_EXTERNAL_ENABLED=true` após conferir orçamento/licença.

Usar schema privado `carrim`, fora dos schemas expostos na Data API. Flyway V1–V7 continua sendo a única fonte das migrations; não executar as mesmas migrations por outra ferramenta. Separar usuário de migração (dono apenas do schema) e usuário da aplicação (DML), sem privilégios de administrador Supabase. Não copiar banco pessoal, tokens ou provas de instalações locais automaticamente. Validar TLS, auth, isolamento entre usuários, sync/idempotência e fotos antes de configurar o APK.

Fotos continuam em BYTEA no PostgreSQL, incluindo aprovação e acesso privado das candidaturas. Supabase Storage é uma melhoria separada: sua franquia de arquivos não aumenta a capacidade atual das fotos no banco. Fazer backup/restauração incluindo mídia antes de uso real.

No APK o frontend está embarcado; a API recebe CORS somente de `https://localhost`, sem cookies, para rotas nativas Bearer. Bootstraps web e administração ficam fora dessa permissão. Actuator permanece privado em loopback; `/healthz` expõe apenas UP/DOWN e retorna 503 quando o banco não responde. Nunca colocar senha do banco ou chave administrativa no APK.

Render Free pode dormir e a franquia de horas é compartilhada com outros serviços Free do workspace. Supabase Free pode pausar por baixa atividade. Validar latência/consumo real; não tratar esses planos como disponibilidade garantida de produção.

O limitador de bootstrap usa o IP do socket, portanto clientes podem compartilhar o limite de 60/min atrás de um proxy Render. Isso limita disponibilidade em carga; não confiar em X-Forwarded-For arbitrário para contornar. Validar limite e proteção no ingresso antes de ampliar o público.
