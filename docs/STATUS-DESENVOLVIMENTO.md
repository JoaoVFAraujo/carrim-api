# Status do Carrim — 08/10/2026

| Parte | Estado |
| --- | --- |
| Frontend 0.0.2 | Compras locais preservadas; serviços de identidade protegida e consultas HTTP preparados |
| Domínio backend | Valores exatos, compras e snapshots implementados |
| PostgreSQL/Flyway | V1 catálogo; V2 compras/histórico; V3 instalações; V4 recibos idempotentes |
| Identidade anônima | Prova da instalação, Bearer com expiração, renovação e revogação implementados |
| APIs de negócio | Catálogo, mercados, compras, itens, preços e finalização idempotente implementados |
| Armazenamento seguro mobile e cliente HTTP | Implementados em serviços; 78 testes, execução nativa pendente |
| Sync e conflitos | Pendente |
| Android em aparelho | Pendente |

Backend 0.0.8: 119 testes, Spotless/verify e JAR, com PostgreSQL 18.2 isolado e HTTP real em porta temporária. O limite de itens é validado no agregado após bloquear a sessão; o limite do bootstrap cobre o mesmo caminho normalizado usado na autorização. Banco do usuário não foi migrado pelo assistente; recarregar Maven e reiniciar com variáveis locais aplica migrations pendentes até V4. Nenhum banco remoto ou deploy.

Frontend 0.0.2: 78 testes, lint, formatação, SQLite real, build e cap sync passaram. Tokens/prova usam Keystore/Keychain no nativo e somente memória no navegador, sem fallback em texto aberto. Bootstrap/renovação são serializados; consultas repetem apenas uma vez após 401. Destinos externos, redirecionamentos e cookies são recusados; Android bloqueia HTTP e backup/transferência do app. Audit completo não encontrou vulnerabilidades conhecidas após ajustar o CLI Capacitor para 8.4.3.

O desenvolvimento web usa proxy localhost:4202 → 127.0.0.1:8082 para `/api/v1/**`, sem abrir CORS. API de produção/nativa permanece desabilitada até configurar HTTPS. Não há conexão automática nem envio de compras; o próximo passo é vincular os dados a um proprietário estável e sincronizar a fila com idempotência/conflitos. A identidade web não persiste ao recarregar, portanto não serve ainda para sincronização de compras persistidas. Detalhes em [integração do mobile](https://github.com/JoaoVFAraujo/carrim-mobile/blob/main/docs/INTEGRACAO-SEGURANCA.md).

Fluxo: branch feature/fix a partir da main, PR, comentário @codex review, correções quando necessárias, merge commit e exclusão da branch. README registra execução/limitações, CHANGELOG registra versões, API-LOCAL registra contratos e documento-base permanece referência revisável.
