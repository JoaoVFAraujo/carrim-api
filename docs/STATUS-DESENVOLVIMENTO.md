# Status do Carrim

## Entregas atuais em PR

| Parte | Estado |
| --- | --- |
| Compras offline | SQLite, unidade/peso/promoção, orçamento, caixa e histórico implementados |
| Backend | APIs protegidas, sync push idempotente, journal transacional e pull por proprietário |
| Identidade do mobile | Keystore/Keychain e vínculo do banco ao proprietário; navegador somente em memória |
| Fila nativa | Processador, pedido congelado, retries persistidos e recuperação de reservas |
| Recebimento e conflitos | Bootstrap paginado, importação ordenada, preservação de edições locais e pausas explícitas |
| Integração local | Validada com API/PostgreSQL e SQLite reais, plugins nativos substituídos no teste |
| Android | APK debug compilado; aparelho/câmera/Keystore/SQLite nativo/atualização física pendentes |
| Produção | Perfil com TLS verificado no banco, saúde privada, logs JSON, imagem sem root e CI preparados |
| Publicação | Sem merge, deploy, domínio HTTPS ou release V1.0.0 |

Os PRs são dependentes e devem ser integrados na ordem: backend sync push → journal/pull → produção; mobile processador → pull/conflitos → teste integrado/Android → configuração HTTPS/CI. Estes resultados descrevem as branches das entregas, não a main publicada.

A identidade web não persiste ao recarregar, então o navegador não sincroniza compras. Nativo exige configurar HTTPS antes do build. Conflitos preservam dados e interrompem dependentes, sem sobrescrita silenciosa; resolução assistida permanece pendente.

Verificações: Maven verify com PostgreSQL isolado, frontend tests/lint/formatação/SQLite/build, teste integrado opt-in e assembleDebug. Produção foi validada localmente com certificado de teste, banco TLS, readiness UP/DOWN e porta principal sem acesso ao health. Não é validação de infraestrutura pública.

Próximas dependências externas: aparelho Android e compra real no supermercado; endereço HTTPS, banco/CA/segredos reais, backup/restauração e assinatura/distribuição de release. Consulte API-LOCAL, PRODUCAO e os roteiros de integração/Android do mobile.
