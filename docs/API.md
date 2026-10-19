# Contrato da API do Carrim

Base local: `http://localhost:8082/api/v1`. HTTP somente no desenvolvimento local; endpoint nativo/público exige HTTPS. Este documento descreve o contrato implementado; regras de produto em [Projeto](PROJETO.md), controles em [Segurança](SEGURANCA.md) e validações/pendências no [roadmap](ROADMAP.md).

## Identidade

POST `/auth/anonymous`, JSON:

```json
{
  "installationId": "UUID criado no dispositivo",
  "installationSecret": "32 bytes de CSPRNG em base64url canônico sem padding",
  "devicePlatform": "ANDROID",
  "appVersion": "0.0.2"
}
```

Plataformas: ANDROID, IOS, WEB. Prova tem 43 caracteres; UUID/prova devem ser preservados em armazenamento seguro do dispositivo. UUID sozinho não autentica. Não incluir Bearer antigo no bootstrap. A resposta contém `userId`, `installationId`, `accountType`, `accessToken` e `expiresAt` UTC. Token opaco tem 256 bits e validade de 30 dias.

Repetir com a mesma instalação/prova mantém usuário, gera novo token e revoga o anterior. Prova divergente: 403; entrada inválida: 400. Banco guarda hashes, não credenciais em claro. Perder instalação/prova significa perder o mecanismo de recuperação anônimo; vinculação de conta fica para etapa futura.

GET `/auth/me` exige `Authorization: Bearer <accessToken>` e retorna identidade técnica. Token desconhecido, expirado ou inválido: 401. Cookies, HTTP Basic e sessão do navegador não autenticam. Não registrar tokens/provas em logs ou commitá-los. Nativo usa identidade protegida e processador de fila/pull com HTTPS configurado. Navegador usa os mesmos contratos de negócio e sincronização com o bootstrap protegido abaixo.

O cliente deve serializar chamadas de bootstrap, pois cada renovação revoga o token anterior. Desde 0.0.8, há limite local de 60 chamadas por endereço de conexão/janela de 60s (429 com Retry-After); cabeçalhos de endereço encaminhado não são confiados. Cookies continuam sem autenticar.

Variações de URL aceitas como o mesmo endpoint após normalização compartilham o limite de chamadas. O filtro usa o mesmo matcher da regra de autorização; o firewall padrão continua rejeitando URLs que considera inseguras.

## Identidade no navegador

Web exige API na mesma origem do frontend. POST `/auth/web/prepare`, com `X-Carrim-Web: 1` e JSON `{installationId: UUID-ou-null}`, valida ou prepara a prova HttpOnly e retorna somente installationId. O cliente recebe/persiste o cookie antes de POST `/auth/web/token`, com o mesmo cabeçalho e `{installationId: UUID}`. Token retorna o contrato IssuedIdentity acima, sem a prova; Bearer fica exclusivamente em memória. Renovação preserva proprietário e revoga o token anterior.

Cookie HTTPS: `__Host-carrim_web_identity`, Secure, HttpOnly, SameSite=Strict, Path=/, sem Domain, validade de 365 dias. HTTP só é permitido fora de prod em host e conexão loopback, usando cookie local separado e Path=/api/v1/auth/web. Cookies não autenticam rotas de negócio. Cookie ausente num banco vinculado, identidade divergente ou cookie malformado pausam sync; não apagar o vínculo para forçar recuperação.

Bootstrap web recusa Fetch Metadata de outra origem, Origin incompatível, cabeçalho customizado ausente/duplicado e HTTP não local; prod exige HTTPS mesmo em loopback. Não habilitar CORS para esses endpoints. Os três bootstraps compartilham o limite por IP. Respostas usam no-store; rejeições 401/403/409 não ecoam prova ou token. Em produção, o proxy preserva Host/origem pública e resolve HTTPS por encaminhamento confiável; cabeçalhos externos precisam ser removidos pelo gateway.

## Negócio

Todas as rotas abaixo exigem Bearer. Propriedade vem do token; userId enviado no JSON não concede acesso. UUID inexistente/pertencente a outro usuário retorna 404. Versão antiga retorna 409 VERSION_CONFLICT. Campos inválidos retornam 400 INVALID_REQUEST, sem ecoar valores rejeitados.

| Método       | Caminho                                          | Comportamento                                                            |
| ------------ | ------------------------------------------------ | ------------------------------------------------------------------------ |
| POST / GET   | `/products`                                      | Criar (201) / listar                                                     |
| GET / PUT    | `/products/{id}`                                 | Consultar / editar com version                                           |
| GET          | `/products/by-barcode/{code}?supermarketId=UUID` | Produto e último preço REGULAR na mesma medida/mercado; mercado opcional |
| POST / GET   | `/supermarkets`                                  | Criar (201) / listar                                                     |
| GET / PUT    | `/supermarkets/{id}`                             | Consultar / editar com version                                           |
| POST / GET   | `/shopping-sessions`                             | Iniciar (201) / listar resumos; filtro status opcional                   |
| GET          | `/shopping-sessions/active`                      | Compra ativa (200) ou nenhuma (204)                                      |
| GET / PATCH  | `/shopping-sessions/{id}`                        | Detalhes com itens / alterar ou remover orçamento                        |
| POST         | `/shopping-sessions/{id}/items`                  | Adicionar item (201), com versão da compra                               |
| PUT / DELETE | `/shopping-sessions/{id}/items/{itemId}`         | Editar / remover (`?version=N`), retornando compra atualizada (200)      |
| POST         | `/shopping-sessions/{id}/complete`               | Finalizar com Idempotency-Key UUID                                       |
| POST         | `/shopping-sessions/{id}/cancel`                 | Cancelar, sem gerar preços                                               |
| GET          | `/shopping-sessions/{id}/prices`                 | Snapshots de preços, somente leitura                                     |

Listagens REST aceitam limit (padrão 20, 1–100) e offset (0–1000000). Produtos/mercados são ordenados por nome/ID; compras por início desc/ID. Status: ACTIVE, COMPLETED, CANCELED. Cadastro mestre ainda não tem exclusão/tombstone. O sync tem contrato separado abaixo.

Produto: `{id,name,barcode?,measurementType}`. Mercado: `{id,name}`. PUT usa campos de cadastro e version; ID vem da URL. Barcode aceita 8/12/13 dígitos como texto, com zeros iniciais. Último preço não retorna equivalente de bundle como preço unitário e nunca confirma preço de hoje.

Início:

```json
{
  "id": "UUID da compra",
  "supermarketId": "UUID do mercado",
  "budgetCents": 20000,
  "startedAt": "2026-10-08T10:00:00Z"
}
```

PATCH: `{version,budgetCents}`; null remove orçamento. Item POST/PUT usa wrapper e versão do agregado:

```json
{
  "version": 0,
  "item": {
    "id": "UUID do item",
    "productId": null,
    "productNameSnapshot": "Café",
    "measurementType": "UNIT",
    "pricingType": "REGULAR",
    "quantityUnits": 2,
    "unitPriceCents": 799
  }
}
```

WEIGHT/REGULAR usa weightGrams e pricePerKgCents, sem quantityUnits/preço unitário/grupo. UNIT/BUNDLE usa quantityUnits, bundleQuantity e bundlePriceCents, sem preço regular/peso; exige grupos completos. Peso e bundle não podem ser combinados. IDs no PUT devem coincidir com a URL. Item pode ser manual com productId nulo; se informado, produto precisa pertencer ao usuário. Subtotais e total são calculados no servidor; até 1000 linhas por compra. Preço de referência máximo: 100000000 centavos; quantidade máxima 9999; peso máximo 9999999 gramas.

Finalização, com header `Idempotency-Key: UUID`:

```json
{
  "version": 3,
  "completedAt": "2026-10-08T10:30:00Z",
  "checkoutTotalCents": 4200
}
```

Checkout pode ser omitido/nulo ou zero; deve ser inteiro não negativo até 9007199254740991. Resposta tem calculatedTotalCents, checkoutTotalCents, checkoutDifferenceCents, version e itens. Mesmo usuário/chave/pedido retorna o mesmo resultado lógico, inclusive após renovação do Bearer. Reutilizar chave com outro pedido retorna 409 IDEMPOTENCY_CONFLICT. Recibo, compra e histórico são gravados juntos. A idempotência persistente aplica-se à finalização; outros comandos usam UUID/versão e podem retornar conflito em replay.

Datas são ISO-8601 UTC; início/finalização/cancelamento são truncados para microssegundos. Cancelamento: `{version,canceledAt}`. Compras encerradas recusam alterações. O limite de 1000 itens é verificado no agregado dentro da mutação transacional bloqueada, inclusive sob concorrência. Histórico conserva referência original e normalizedPriceCents; approximate indica divisão de bundle não exata. Listas de preços não alteram itens.

## Sync push

POST `/api/v1/sync/push` exige Bearer. Recebe `operations` (1–50), cada uma com `operationId`, `aggregateType`, `aggregateId`, `operation`, `baseVersion` e `payload`. Versão é obrigatória para alterar catálogo/compras e para qualquer escrita de item; CREATE de mercado/produto/compra usa versão nula. Payload de item contém `sessionId` e `item` no contrato de ItemInput; COMPLETE usa `completedAt` e `checkoutTotalCents`.

Resposta por operação: APPLIED, ALREADY_APPLIED, CONFLICT ou REJECTED. Após a primeira falha, entradas posteriores ficam NOT_PROCESSED. Recibo e alteração são atômicos e isolados por proprietário; reutilizar operationId com outro conteúdo retorna IDEMPOTENCY_CONFLICT. A migration V5 cria os recibos. Nenhuma escrita deriva proprietário do JSON.

O corpo da resposta é um array de `{operationId,status,code,state}`. Sucesso/replay inclui state `{aggregateType,aggregateId,snapshot}`; conflito pode incluir estado atual do proprietário. A atomicidade é **por operação**, não pelo lote completo: uma falha posterior não desfaz operações anteriores aplicadas.

| aggregateType    | operation                           | Payload e versão                                                        |
| ---------------- | ----------------------------------- | ----------------------------------------------------------------------- |
| PRODUCT          | CREATE / UPDATE                     | Campos de produto; CREATE baseVersion nula, UPDATE versão atual         |
| SUPERMARKET      | CREATE / UPDATE                     | Campos de mercado; CREATE baseVersion nula, UPDATE versão atual         |
| SHOPPING_SESSION | CREATE / UPDATE / COMPLETE / CANCEL | Campos da operação REST; CREATE versão nula, demais versão atual        |
| SHOPPING_ITEM    | CREATE / UPDATE / DELETE            | sessionId e item quando aplicável; baseVersion é da compra, não do item |

O mobile envia uma operação por requisição embora a API aceite até 50. Congela o pedido na fila antes do primeiro envio e mantém operationId/payload/baseVersion no retry, inclusive após resposta perdida ou renovação de token. Não tratar toda escrita REST como idempotente: o recibo de sync usa seu próprio contrato.

## Sync pull e bootstrap

GET `/api/v1/sync/pull?cursor=...&limit=50` devolve `changes`, cursor opaco vinculado ao proprietário e `hasMore`. Limite de 1–100. Sem cursor, inicia a reconstrução paginada. Snapshots são atuais e consistentes por página; eventos repetidos podem devolver o mesmo estado mais recente. V6 cria journal/cabeça transacional por proprietário e inclui dados existentes. Triggers registram também escritas REST; alterações e eventos fazem commit ou rollback juntos. Cursor de outro proprietário ou além da cabeça atual é recusado.

Cada change tem `{aggregateType,aggregateId,snapshot}`. O cliente não interpreta cursor como autorização: só Bearer autoriza. Armazenar páginas/cursor de modo transacional, ordenar catálogo/mercados antes das compras e não substituir edição local pendente. Compras encerradas importam itens/preços sem ecoar novas operações para a fila.

Conflitos de versão/estado, identidade/código colidente ou snapshot inválido pausam o fluxo correspondente preservando dados. O mobile oferece reaplicação assistida para alterações suportadas em dados ativos, após consulta autenticada, comparação, confirmação e arquivo do pedido anterior. Auto-rebase automático, reconciliação terminal e exclusão/tombstones de catálogo não estão entregues. Não limpar filas, trocar proprietário/operationId ou desbloquear FAILED automaticamente.

## Erros e limites

Erros de negócio usam `{code,message}` sanitizados; não assumir esse envelope em rejeições de autenticação/filtro/infraestrutura. 401 requer autenticação; 403 é acesso/prova negados; 404 não diferencia UUID inexistente de alheio; 409 representa versão/estado/idempotência; 400 entrada inválida; 413 corpo excessivo; 429 limite; 503 persistência indisponível. Resultados individuais do push são avaliados pelo status de cada operação, mesmo quando o HTTP é bem-sucedido.

JSON monetário/peso/quantidade exige inteiros; não há coerção de decimal para inteiro. Corpo POST/PUT/PATCH até 2 MiB, payload individual de sync até 32 KiB. Demais limites de negócio descritos acima. Política e limites externos de produção em [Segurança](SEGURANCA.md).

Referências de implementação: controllers/DTOs em `src/main/java/br/com/carrim/adapter/in/web`, testes `BusinessApiTests`, `NativeHttpApiTests`, `SyncApiTests` e [Spring Security — tokens opacos](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html).
