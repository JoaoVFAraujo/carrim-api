# Carrim — produto e arquitetura

Referência revisável consolidada em 16/10/2026 a partir do documento-base v12 do PC, direção visual e código integrado. O [roadmap](ROADMAP.md) separa intenção de implementação; o [contrato da API](API.md) prevalece sobre exemplos antigos.

## Propósito

Acompanhar a compra no supermercado e saber o total antes do caixa, mesmo sem internet. O usuário escolhe mercado, informa preço/quantidade, acompanha orçamento opcional, finaliza e consulta o histórico.

| Horizonte                    | Pergunta               |
| ---------------------------- | ---------------------- |
| V1 — compra atual            | Quanto estou gastando? |
| V2 — comparação e histórico  | Estou pagando caro?    |
| V3 — planejamento e economia | Onde devo comprar?     |

Esses horizontes não são versões técnicas dos manifests. V1.0.0 ainda não foi liberada. O núcleo não exige conta nem conexão. Scanner é atalho com alternativa manual; o código de barras identifica produto, não preço. Câmera negada, orçamento ultrapassado ou servidor indisponível não devem inutilizar a compra.

## Regras duráveis

| Tema                | Regra                                                                  |
| ------------------- | ---------------------------------------------------------------------- |
| Identidade          | UUID estável; conhecer ID não prova propriedade                        |
| Compra ativa        | Uma por banco local e por proprietário no servidor                     |
| Dinheiro            | Centavos inteiros de ponta a ponta; apresentação pt-BR                 |
| Peso                | Gramas inteiras; entrada em kg com até três casas                      |
| Medida              | Pacote fechado “Arroz 5 kg” é UNIT; produto vendido por peso é WEIGHT  |
| Promoção            | BUNDLE com grupos completos; avulsas ficam em linha separada           |
| Total               | Soma de subtotais derivados, não total arbitrário enviado pelo cliente |
| Caixa               | Opcional; nulo difere de zero; diferença = caixa − app                 |
| Encerramento        | COMPLETED/CANCELED são terminais; histórico somente leitura            |
| Preços              | Observação definitiva nasce na finalização, não a cada edição          |
| Referência anterior | Produto/mercado/medida e data; nunca assumir como preço de hoje        |
| Cadastro mestre     | Não reescrever nome/preço de itens de compras passadas                 |

Exemplos: 2 × R$ 7,99 = R$ 15,98; 824 g a R$ 6,99/kg = R$ 5,76, com arredondamento HALF_UP no subtotal; seis unidades em promoção 3 por R$ 10,00 = R$ 20,00. Equivalência por unidade promocional é aproximada e não entra no cálculo exato do grupo. WEIGHT + BUNDLE fica fora do contrato.

Agrupar repetidos somente com identidade e configuração compatíveis; outro preço fica separado. Ajuste de quantidade não renomeia o catálogo. Observações normalizadas conservam base UNIT/KG, configuração original e indicação de aproximação.

Limite é opcional e não bloqueia adição. Editar/remover orçamento e cancelar com confirmação estão disponíveis na API e no mobile; SQLite e fila são alterados atomicamente, sem gerar preços no cancelamento. Diferença do caixa não ajusta itens automaticamente.

## Arquitetura

Dois repositórios, um produto: carrim-mobile (appId br.com.carrim) e carrim-api (namespace br.com.carrim). Sem microsserviços ou camadas adicionais sem benefício real.

| Mobile                     | Responsabilidade                                               |
| -------------------------- | -------------------------------------------------------------- |
| Angular standalone + Ionic | Navegação e componentes; HTML separado do TypeScript           |
| Signals e Store            | Estado, validações e ordenação das ações; RxJS onde necessário |
| ShoppingRepository         | SQL e transações SQLite de compras/itens/preços com a fila     |
| Tailwind tw:               | Utilitários após componentes/APIs Ionic e Angular              |
| SQLite e migrations        | Fonte local; constraints e transações                          |
| Capacitor + MLKit          | Recursos nativos e scanner                                     |
| Serviços de sync           | Identidade, fila, transporte, pull e atenção                   |

Navegador usa jeep-sqlite/sql.js em IndexedDB por origem (host/porta); trocar origem não transfere dados. Aparelho usa SQLite nativo. UI e acessibilidade ficam no [layout](https://github.com/JoaoVFAraujo/carrim-mobile/blob/main/docs/LAYOUT.md).

Backend: monólito modular com **uma única Hexagonal**, não uma por entidade.

| Camada                 | Regra                                        |
| ---------------------- | -------------------------------------------- |
| domain                 | Java puro e regras de negócio                |
| application            | Casos de uso/portas; não depende de adapters |
| adapter/in             | HTTP e DTOs explícitos                       |
| adapter/out            | Persistência e integrações                   |
| config/security/shared | Somente responsabilidades transversais reais |

Catálogo usa JPA/Hibernate; compras usam JDBC com transação por comando. ArchUnit protege dependências. PostgreSQL guarda propriedade, versões, recibos e journal. Flyway é fonte do schema do servidor; SQLite tem migrations próprias. Não aplicar o schema conceitual antigo sobre tabelas existentes.

## Offline e sincronização

1. Mutação local e entrada da fila são atômicas; sincronizar não é condição para comprar.
2. Confirmar identidade persistente e vínculo SQLite/proprietário antes do envio. Divergência ou perda de identidade bloqueia sync e preserva dados.
3. Fila respeita ordem/dependências; congelar operationId, pedido e baseVersion antes do primeiro envio.
4. Servidor aplica operação e recibo juntos; replay não duplica efeito. Versão divergente gera conflito.
5. Após push, receber journal paginado, estagiar snapshots e aplicar por dependências sem sobrescrever edições locais pendentes.
6. Conflitos pausam dependentes e mostram atenção. A revisão assistida permite comparar e reaplicar explicitamente alterações suportadas em dados ativos, arquivando pedido anterior e estados comparados. Não há last-write-wins nem auto-rebase automático; casos terminais e importações complexas permanecem protegidos.

Navegador e nativo sincronizam mercados, produtos, compras, itens e preços. Web usa API na mesma origem, prova em cookie HttpOnly e token em memória; o nativo usa Keystore/Keychain e endpoint HTTPS configurado. Escritas despertam o envio imediatamente. SyncSchedulerService usa um timer e consultas ociosas em 15/30/60 segundos; atividade recebida reinicia o intervalo. Foco, retorno ao app e retorno da conexão despertam trabalho, respeitando next_attempt_at_ms e leases persistidos. Sem rede, não mantém polling. FAILED e importações pausadas aguardam revisão, sem desbloqueio automático. A pausa de importação prevalece sobre retries pendentes; confirmar uma revisão suportada desperta o agendador somente após a transação aprovada ser salva. Documento oculto ou app nativo inativo pausa novos envios e novas páginas; uma requisição já em andamento pode concluir e salvar a confirmação. Reservas ainda não enviadas são liberadas sem alterar operationId/pedido congelado, com isolamento por lease; a retomada não precisa esperar recuperação por timeout. Retomar o primeiro plano volta a processar as pendências. Offline ou API indisponível mantém pendências persistidas. Web Locks limita o banco web a uma aba por origem, evitando cópias concorrentes do IndexedDB. Coalescência continua uma otimização futura.

Os schemas não são espelhos físicos: histórico de preços é derivado da finalização, e filas, recibos, cursores e vínculos têm funções próprias em cada ambiente. Todas as informações de negócio locais têm representação no servidor; segredos e controles internos não são enviados como tabelas de usuário. Consultas de tela usam o cache local atualizado pelo pull.

## Manutenção

Testar dinheiro, migrações preservando dados, rollback, imutabilidade, propriedade, replay e concorrência. Registrar ambiente/limites: CI não equivale a teste físico. Consultar código/testes em vez de duplicar SQL e árvores completas em Markdown.

Documento-base e retomadas foram incorporados aos guias essenciais. Textos completos anteriores permanecem no Git (backend 08f0f52, mobile 915a113); pasta original do PC preservada. O exemplo antigo do PC de autenticação somente por UUID foi substituído pelo contrato com prova da instalação, sem regredir o código.
