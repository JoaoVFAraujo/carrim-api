# Status do Carrim — 08/10/2026

| Parte | Estado |
| --- | --- |
| Frontend 0.0.1 | Compras, scanner integrado, peso/promoção, finalização, histórico e catálogo local implementados |
| Domínio backend | Valores exatos, compras e snapshots implementados |
| PostgreSQL/Flyway | V1 catálogo; V2 compras/histórico; V3 instalações; V4 recibos idempotentes |
| Identidade anônima | Prova da instalação, Bearer com expiração, renovação e revogação implementados |
| APIs de negócio | Catálogo, mercados, compras, itens, preços e finalização idempotente implementados |
| Armazenamento seguro mobile e cliente HTTP | Pendente |
| Sync e conflitos | Pendente |
| Android em aparelho | Pendente |

Backend 0.0.8: 119 testes, Spotless/verify e JAR, com PostgreSQL 18.2 isolado e HTTP real em porta temporária. O limite de itens é validado no agregado após bloquear a sessão; o limite do bootstrap cobre o mesmo caminho normalizado usado na autorização. Banco do usuário não foi migrado pelo assistente; recarregar Maven e reiniciar com variáveis locais aplica migrations pendentes até V4. Frontend não mudou nesta etapa. Nenhum banco remoto ou deploy.

Fluxo: branch feature/fix a partir da main, PR, comentário @codex review, correções quando necessárias, merge commit e exclusão da branch. README registra execução/limitações, CHANGELOG registra versões, API-LOCAL registra contratos e documento-base permanece referência revisável.
