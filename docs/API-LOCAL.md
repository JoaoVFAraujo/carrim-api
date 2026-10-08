# API local do Carrim

Base: `http://localhost:8082/api/v1`. HTTP somente no desenvolvimento local; tokens/provas exigem HTTPS quando houver hospedagem. Contrato implementado prevalece sobre exemplos históricos do documento-base.

## Identidade — backend 0.0.7

POST `/auth/anonymous`, JSON:

```json
{
  "installationId": "UUID criado no dispositivo",
  "installationSecret": "32 bytes de CSPRNG em base64url canônico sem padding",
  "devicePlatform": "ANDROID",
  "appVersion": "0.0.1"
}
```

Plataformas: ANDROID, IOS, WEB. Prova tem 43 caracteres; UUID/prova devem ser preservados em armazenamento seguro do dispositivo. UUID sozinho não autentica. Não incluir Bearer antigo no bootstrap. A resposta contém `userId`, `installationId`, `accountType`, `accessToken` e `expiresAt` UTC. Token opaco tem 256 bits e validade de 30 dias.

Repetir com a mesma instalação/prova mantém usuário, gera novo token e revoga o anterior. Prova divergente: 403; entrada inválida: 400. Banco guarda hashes, não credenciais em claro. Perder instalação/prova significa perder o mecanismo de recuperação anônimo; vinculação de conta fica para etapa futura.

GET `/auth/me` exige `Authorization: Bearer <accessToken>` e retorna identidade técnica. Token desconhecido, expirado ou inválido: 401. Cookies, HTTP Basic e sessão do navegador não autenticam. Não registrar tokens/provas em logs ou commitá-los; frontend ainda não implementa armazenamento seguro nem chamadas à API.

O cliente deve serializar chamadas de bootstrap, pois cada renovação revoga o token anterior. Desde 0.0.8, há limite local de 60 chamadas por endereço de conexão/janela de 60s (429 com Retry-After); cabeçalhos de endereço encaminhado não são confiados. Cookies continuam sem autenticar.

Variações de URL aceitas como o mesmo endpoint após normalização compartilham o limite de chamadas. O filtro usa o mesmo matcher da regra de autorização; o firewall padrão continua rejeitando URLs que considera inseguras.

## Negócio — backend 0.0.8

Todas as rotas abaixo exigem Bearer. Propriedade vem do token; userId enviado no JSON não concede acesso. UUID inexistente/pertencente a outro usuário retorna 404. Versão antiga retorna 409 VERSION_CONFLICT. Campos inválidos retornam 400 INVALID_REQUEST, sem ecoar valores rejeitados.

| Método | Caminho | Comportamento |
| --- | --- | --- |
| POST / GET | `/products` | Criar (201) / listar |
| GET / PUT | `/products/{id}` | Consultar / editar com version |
| GET | `/products/by-barcode/{code}?supermarketId=UUID` | Produto e último preço REGULAR na mesma medida/mercado; mercado opcional |
| POST / GET | `/supermarkets` | Criar (201) / listar |
| GET / PUT | `/supermarkets/{id}` | Consultar / editar com version |
| POST / GET | `/shopping-sessions` | Iniciar (201) / listar resumos; filtro status opcional |
| GET | `/shopping-sessions/active` | Compra ativa (200) ou nenhuma (204) |
| GET / PATCH | `/shopping-sessions/{id}` | Detalhes com itens / alterar ou remover orçamento |
| POST | `/shopping-sessions/{id}/items` | Adicionar item (201), com versão da compra |
| PUT / DELETE | `/shopping-sessions/{id}/items/{itemId}` | Editar / remover (`?version=N`), retornando compra atualizada (200) |
| POST | `/shopping-sessions/{id}/complete` | Finalizar com Idempotency-Key UUID |
| POST | `/shopping-sessions/{id}/cancel` | Cancelar, sem gerar preços |
| GET | `/shopping-sessions/{id}/prices` | Snapshots de preços, somente leitura |

Listagens aceitam limit (padrão 20, 1–100) e offset (0–1000000). Produtos/mercados são ordenados por nome/ID; compras por início desc/ID. Status: ACTIVE, COMPLETED, CANCELED. Cadastro mestre ainda não tem exclusão/tombstone. Não há sync em lote nesta entrega.

Produto: `{id,name,barcode?,measurementType}`. Mercado: `{id,name}`. PUT usa campos de cadastro e version; ID vem da URL. Barcode aceita 8/12/13 dígitos como texto, com zeros iniciais. Último preço não retorna equivalente de bundle como preço unitário e nunca confirma preço de hoje.

Início:

```json
{"id":"UUID da compra","supermarketId":"UUID do mercado","budgetCents":20000,"startedAt":"2026-10-08T10:00:00Z"}
```

PATCH: `{version,budgetCents}`; null remove orçamento. Item POST/PUT usa wrapper e versão do agregado:

```json
{
  "version":0,
  "item":{
    "id":"UUID do item",
    "productId":null,
    "productNameSnapshot":"Café",
    "measurementType":"UNIT",
    "pricingType":"REGULAR",
    "quantityUnits":2,
    "unitPriceCents":799
  }
}
```

WEIGHT/REGULAR usa weightGrams e pricePerKgCents, sem quantityUnits/preço unitário/grupo. UNIT/BUNDLE usa quantityUnits, bundleQuantity e bundlePriceCents, sem preço regular/peso; exige grupos completos. Peso e bundle não podem ser combinados. IDs no PUT devem coincidir com a URL. Item pode ser manual com productId nulo; se informado, produto precisa pertencer ao usuário. Subtotais e total são calculados no servidor; até 1000 linhas por compra. Preço de referência máximo: 100000000 centavos; quantidade máxima 9999; peso máximo 9999999 gramas.

Finalização, com header `Idempotency-Key: UUID`:

```json
{"version":3,"completedAt":"2026-10-08T10:30:00Z","checkoutTotalCents":4200}
```

Checkout pode ser omitido/nulo ou zero; deve ser inteiro não negativo até 9007199254740991. Resposta tem calculatedTotalCents, checkoutTotalCents, checkoutDifferenceCents, version e itens. Mesmo usuário/chave/pedido retorna o mesmo resultado lógico, inclusive após renovação do Bearer. Reutilizar chave com outro pedido retorna 409 IDEMPOTENCY_CONFLICT. Recibo, compra e histórico são gravados juntos. A idempotência persistente aplica-se à finalização; outros comandos usam UUID/versão e podem retornar conflito em replay.

Datas são ISO-8601 UTC; início/finalização/cancelamento são truncados para microssegundos. Cancelamento: `{version,canceledAt}`. Compras encerradas recusam alterações. O limite de 1000 itens é verificado no agregado dentro da mutação transacional bloqueada, inclusive sob concorrência. Histórico conserva referência original e normalizedPriceCents; approximate indica divisão de bundle não exata. Listas de preços não alteram itens.

Integração do mobile/armazenamento seguro, sync, vinculação de contas, proteção distribuída e deploy continuam pendentes. Para aplicar V3/V4 localmente: recarregar Maven e reiniciar com credenciais já configuradas; nenhum segredo adicional de assinatura é necessário.

Referência de implementação: [Spring Security — tokens opacos](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html).
