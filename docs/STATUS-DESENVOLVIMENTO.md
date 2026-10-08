# Status do Carrim — 07/10/2026

| Parte | Estado |
| --- | --- |
| Frontend 0.0.1 | Compras, scanner integrado, peso/promoção, finalização, histórico e catálogo local implementados |
| Domínio backend | Valores exatos, compras e snapshots implementados |
| PostgreSQL/Flyway | V1 catálogo; V2 compras/histórico; V3 instalações anônimas |
| Identidade anônima | Prova da instalação, Bearer com expiração, renovação e revogação implementados |
| APIs de negócio | Próxima entrega |
| Armazenamento seguro mobile e cliente HTTP | Pendente |
| Sync e conflitos | Pendente |
| Android em aparelho | Pendente |

Backend 0.0.7: 99 testes, Spotless/verify e JAR, usando PostgreSQL 18.2 isolado. Banco do usuário não foi migrado pelo assistente; recarregar Maven e reiniciar com variáveis locais aplica migrations pendentes. Frontend não mudou nesta etapa. Nenhum banco remoto ou deploy.

Fluxo: branch feature/fix a partir da main, PR, comentário @codex review, correções quando necessárias, merge commit e exclusão da branch. README registra execução/limitações, CHANGELOG registra versões, API-LOCAL registra contratos e documento-base permanece referência revisável.
