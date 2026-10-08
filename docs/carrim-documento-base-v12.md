# Carrim — Documento Base do Projeto

> Documento inicial de referência para decisões de produto, arquitetura e roadmap.

## 0. Como este documento deve ser usado

Este documento é um **norte para o desenvolvimento**, não um contrato imutável.

As decisões aqui registradas representam o melhor caminho definido até o momento e devem orientar arquitetura, implementação, UX e organização do projeto. Porém, **nada está escrito em pedra**.

Durante o desenvolvimento, uma decisão pode ser revista quando:

- surgir uma solução tecnicamente melhor;
- uma biblioteca ou recurso nativo simplificar a implementação;
- testes reais mostrarem que o fluxo planejado não funciona tão bem quanto esperado;
- houver ganho claro de manutenção, desempenho, segurança ou experiência do usuário;
- uma decisão anterior criar complexidade desnecessária;
- versões futuras das tecnologias utilizadas oferecerem alternativas melhores.

Quando isso acontecer, devemos:

1. avaliar o novo caminho;
2. comparar benefícios e impactos;
3. tomar a decisão conscientemente;
4. atualizar este documento para refletir o novo estado do projeto.

O objetivo deste arquivo é **manter direção e contexto**, e não impedir melhorias.

## 1. Visão do produto

Aplicativo mobile para acompanhar compras em supermercados em tempo real.

O usuário poderá escanear o código de barras dos produtos, informar o preço encontrado, adicionar itens ao carrinho e acompanhar o total da compra antes de chegar ao caixa.

A evolução do produto será baseada em três pilares:

1. **Controle da compra atual**
2. **Histórico e comparação de preços**
3. **Planejamento e economia**

A proposta visual do aplicativo será minimalista, sofisticada e próxima da experiência nativa do sistema operacional, aproveitando recursos nativos do Android e iOS sempre que fizer sentido.

---

## 2. Objetivo principal

Responder, progressivamente, a três perguntas:

### V1
> Quanto estou gastando?

### V2
> Estou pagando caro?

### V3
> Onde devo comprar?

---

# 3. Stack tecnológica

## Mobile / Frontend

| Tecnologia | Versão/base definida |
|---|---|
| Ionic Framework | 9.x |
| Angular | 22.2.x |
| Capacitor | 8.5.x |
| TypeScript | 6.0.x |
| Node.js | 24 LTS |
| Estado | Angular Signals |
| Componentes | Ionic Components |
| Ícones | Ionicons |
| Banco local | SQLite |
| Scanner | ML Kit Barcode Scanner |
| Testes | Vitest |

### Princípios

- Standalone Components.
- Signals como principal solução de estado.
- Uso de RxJS apenas onde fizer sentido.
- Aproveitar comportamentos nativos de Android e iOS.
- Offline-first desde a primeira versão.
- Feature-first no frontend.
- Templates Angular em arquivos `.html` separados dos componentes `.ts`.
- Não utilizar `template: \`...\`` ou grandes blocos de HTML inline dentro do component.
- Priorizar componentes e APIs do próprio Ionic/Angular antes de criar soluções customizadas.
- Utilizar Tailwind CSS como camada utilitária para layout, espaçamento, tipografia e pequenos ajustes visuais.
- CSS/SCSS manual somente quando Ionic e Tailwind não resolverem de forma adequada.
- Evitar sobrescrever internals de componentes Ionic sem necessidade.

---

## Backend

| Tecnologia | Versão/base definida |
|---|---|
| Java | 25 LTS |
| Spring Boot | 4.1.x |
| Spring Security | gerenciado pelo Spring Boot |
| Spring Data JPA / Hibernate | gerenciado pelo Spring Boot |
| PostgreSQL | 18.x |
| Flyway | gerenciado pelo Spring Boot |
| Testes | JUnit + Mockito + Testcontainers |

### Princípios

- Monólito modular
- Uma única Arquitetura Hexagonal, com contextos separados dentro de domain/application/adapters
- REST API
- Migrações obrigatórias com Flyway
- Sem microsserviços neste momento
- Módulos preparados para futura separação caso exista necessidade real

---

# 4. Arquitetura geral

```text
                         MOBILE
┌────────────────────────────────────────────────────┐
│ Ionic + Angular + Capacitor                        │
│                                                    │
│ core/                                              │
│ features/                                          │
│ shared/                                            │
│ theme/                                             │
│                                                    │
│ Angular Signals                                    │
│ SQLite local                                       │
│ integrações nativas via Capacitor                  │
└────────────────────────┬───────────────────────────┘
                         │
                      HTTPS
                         │
                         ▼
┌────────────────────────────────────────────────────┐
│                     BACKEND                        │
│                                                    │
│ Java + Spring Boot                                 │
│ Monólito modular                                   │
│ UMA arquitetura Hexagonal                          │
│                                                    │
│              ┌─────────────────┐                   │
│              │     Domain      │                   │
│              │ shopping        │                   │
│              │ catalog         │                   │
│              │ supermarket     │                   │
│              │ pricing         │                   │
│              │ identity        │                   │
│              └────────▲────────┘                   │
│                       │                            │
│              ┌────────┴────────┐                   │
│              │   Application   │                   │
│              │ use cases/ports │                   │
│              └───▲─────────▲───┘                   │
│                  │         │                       │
│        adapter/in│         │adapter/out            │
│                  │         │                       │
│               REST       Persistence               │
│               Sync       Security                  │
└────────────────────────┬───────────────────────────┘
                         │
                         ▼
                    PostgreSQL
```

A arquitetura backend possui **um único hexágono**. `shopping`, `catalog`, `pricing` e os demais contextos são subdivisões internas desse mesmo sistema, e não arquiteturas hexagonais independentes.

No mobile não será reproduzida a arquitetura Hexagonal do backend. O frontend seguirá uma organização **feature-first pragmática**, adequada a Angular/Ionic.

---

# 5. Arquitetura do frontend

O frontend será simples e orientado a features.

Estrutura base:

```text
src/app/

├── core/
│   ├── auth/
│   ├── database/
│   ├── http/
│   ├── security/
│   ├── sync/
│   └── platform/
│       ├── barcode/
│       ├── haptics/
│       ├── network/
│       └── secure-storage/
│
├── features/
│   ├── home/
│   ├── shopping/
│   ├── scanner/
│   ├── catalog/
│   ├── supermarkets/
│   └── history/
│
├── shared/
│   ├── components/
│   ├── directives/
│   ├── pipes/
│   └── utils/
│
├── theme/
│
├── app.config.ts
├── app.html
├── app.routes.ts
└── app.ts
```

Não haverá uma regra obrigatória de criar `domain/application/data/presentation` dentro de toda feature.

Cada feature terá apenas as divisões que realmente precisar. Exemplo:

```text
features/shopping/

├── pages/
├── components/
├── stores/
├── services/
└── models/
```

Uma feature simples como `home` pode ter apenas:

```text
features/home/
└── pages/
    ├── home.page.ts
    ├── home.page.html
    └── home.page.spec.ts
```

Se uma feature crescer e justificar uma separação adicional, ela poderá ganhar pastas como `data/`, `mappers/` ou `repositories/`. Isso será uma decisão local, não uma obrigação arquitetural.

Princípios:

- Angular/Ionic continuam sendo a base do frontend;
- Signals serão usados para estado;
- integrações nativas ficam concentradas em `core/platform`;
- persistência local fica apoiada em `core/database` e serviços/repositórios das features quando necessário;
- `shared` não deve virar uma pasta genérica para código sem dono;
- manter HTML separado do `.ts`;
- não tentar replicar no frontend a Hexagonal do backend;
- preferir simplicidade enquanto a complexidade do domínio não exigir novas camadas.

---

# 6. Estado da aplicação

A solução padrão será Angular Signals.

Principais recursos:

- `signal()`
- `computed()`
- `effect()`
- `inject()`

NgRx não será utilizado inicialmente.

RxJS continuará disponível para fluxos naturalmente assíncronos, especialmente integrações HTTP e APIs que retornem Observables.

---

# 7. Experiência visual e componentes

## 7.1 Hierarquia oficial de UI e estilos

A prioridade do projeto será:

```text
1. Ionic / Angular nativos
        ↓
2. Tailwind CSS
        ↓
3. CSS/SCSS manual
```

Essa ordem deve orientar a implementação.

### 1. Ionic / Angular primeiro

Sempre verificar primeiro se o Ionic já oferece o componente, interação ou padrão necessário.

Exemplos:

- `IonButton`
- `IonInput`
- `IonModal`
- `IonActionSheet`
- `IonToast`
- `IonAlert`
- `IonItem`
- `IonItemSliding`
- `IonTabs`
- `IonSearchbar`
- `IonRefresher`
- `IonProgressBar`
- APIs de animação e gestos do Ionic
- recursos do Angular para binding, Signals, control flow e composição de componentes

Não recriar em HTML/CSS puro algo que o Ionic já resolve de forma adequada.

### 2. Tailwind CSS em seguida

Tailwind CSS será utilizado como camada utilitária, principalmente para:

- layout;
- flex/grid;
- gaps;
- padding e margin;
- largura/altura;
- tipografia;
- alinhamento;
- responsividade;
- pequenos estados visuais;
- composição de telas e componentes próprios.

Tailwind não substitui o Ionic. Ele complementa a composição visual ao redor dos componentes Ionic.

O Tailwind CSS possui integração oficial com Angular e gera CSS estaticamente, sem runtime adicional.

### 3. CSS/SCSS manual por último

CSS manual deve ser usado somente quando:

- Ionic não fornecer a customização necessária;
- Tailwind não representar bem a regra;
- for necessário trabalhar com CSS Variables ou `::part` disponibilizados pelo Ionic;
- existir uma animação ou comportamento visual específico que realmente exija CSS customizado.

A intenção não é proibir CSS, mas evitar folhas de estilo grandes, difíceis de manter e cheias de regras duplicadas.

Como os componentes Ionic utilizam Web Components e Shadow DOM em várias partes, customizações internas devem preferir as APIs oficiais de tema, CSS Variables e parts expostos pelo próprio Ionic, em vez de seletores frágeis.

## 7.2 Estrutura dos componentes Angular

Os arquivos devem permanecer separados:

```text
product-card/
├── product-card.component.ts
├── product-card.component.html
└── product-card.component.spec.ts
```

Quando CSS manual não for necessário, não é obrigatório criar um arquivo `.css`/`.scss` vazio.

Exemplo esperado:

```typescript
@Component({
  selector: 'app-product-card',
  templateUrl: './product-card.component.html'
})
export class ProductCardComponent {}
```

Evitar:

```typescript
@Component({
  selector: 'app-product-card',
  template: `
    <ion-card>
      ...
    </ion-card>
  `
})
```

A regra vale principalmente para componentes e páginas reais do aplicativo. Pequenos componentes técnicos excepcionais só devem fugir disso se houver um motivo claro.

## 7.3 Diretriz visual

A interface seguirá o conceito dos mockups aprovados:

- minimalista;
- sofisticada;
- clara;
- bastante espaço em branco;
- cards suaves;
- hierarquia visual simples;
- animações discretas;
- feedback visual imediato.

## Prioridade para componentes Ionic

Exemplos:

- IonContent
- IonHeader
- IonToolbar
- IonTabs
- IonTabBar
- IonModal
- IonActionSheet
- IonToast
- IonAlert
- IonInput
- IonSearchbar
- IonRefresher
- IonSkeletonText
- IonItemSliding
- IonSegment
- IonBadge
- IonProgressBar

Não utilizar PrimeNG no aplicativo mobile.

Para este projeto, a combinação preferida será:

```text
Ionic Components
+
Angular
+
Tailwind CSS 4
```

com CSS manual apenas em necessidades específicas.

---

# 8. Experiência nativa

O aplicativo deverá respeitar a plataforma em que estiver sendo executado.

## iOS

- comportamento visual iOS;
- swipe back;
- safe areas;
- modais e transições apropriadas;
- padrões de interação do sistema.

## Android

- comportamento Material;
- integração com botão/gesto voltar;
- componentes e animações adequados ao Android.

Não forçar globalmente o modo iOS em dispositivos Android.

---

# 9. Animações e feedback

As animações devem ser elegantes e funcionais, sem excesso.

Tecnologias preferidas:

- Ionic Animations
- Web Animations API
- CSS transitions
- Haptic feedback via Capacitor

Exemplos planejados:

- confirmação suave após leitura do código de barras;
- bottom sheet surgindo após produto encontrado;
- animação ao adicionar produto ao carrinho;
- atualização animada do valor total;
- badge do carrinho aumentando;
- feedback háptico ao confirmar ações;
- feedback visual ao atingir limites de orçamento.

---

# 10. Offline-first

O aplicativo deverá continuar funcionando durante uma compra mesmo sem conexão com internet.

O dispositivo será a fonte operacional imediata durante a compra.

## Banco local

SQLite.

Estruturas iniciais previstas:

```text
products
supermarkets
shopping_sessions
shopping_items
price_history
sync_queue
```

Fluxo online:

```text
Aplicativo
   ↓
SQLite
   ↓
API
   ↓
PostgreSQL
```

Fluxo offline:

```text
Aplicativo
   ↓
SQLite
```

Ao recuperar conexão:

```text
SQLite
   ↓
Sync Engine
   ↓
Spring Boot API
   ↓
PostgreSQL
```

---

# 11. Sincronização

Alterações feitas pelo usuário deverão aparecer imediatamente no aplicativo.

Uma operação local poderá ter estados como:

```text
PENDING
SYNCED
FAILED
```

A fila de sincronização poderá conter ações como:

```text
ADD_PRODUCT
UPDATE_PRICE
ADD_SHOPPING_ITEM
UPDATE_SHOPPING_ITEM
REMOVE_SHOPPING_ITEM
FINISH_SHOPPING
```

A estratégia será semelhante a uma Outbox local.

Não utilizar CRDT inicialmente.

---

# 12. Arquitetura do backend

O backend utilizará:

```text
Monólito Modular
+
UMA Arquitetura Hexagonal
```

A estrutura principal será organizada pelas partes do hexágono, e os contextos de negócio ficarão separados **dentro delas**.

```text
br.com.carrim/

├── domain/
│   ├── shopping/
│   ├── catalog/
│   ├── supermarket/
│   ├── pricing/
│   └── identity/
│
├── application/
│   ├── shopping/
│   ├── catalog/
│   ├── supermarket/
│   ├── pricing/
│   ├── identity/
│   └── synchronization/
│
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   └── sync/
│   │
│   └── out/
│       ├── persistence/
│       └── security/
│
├── config/
├── security/
└── shared/
```

Isso representa **um único hexágono**, e não um hexágono por módulo.

## Domínio

O coração do sistema:

```text
domain/
├── shopping/
│   ├── ShoppingSession
│   ├── ShoppingItem
│   └── ShoppingStatus
├── catalog/
│   └── Product
├── supermarket/
│   └── Supermarket
└── pricing/
    ├── PriceObservation
    └── Money
```

O domínio não conhece:

```text
Spring MVC
JPA
PostgreSQL
HTTP
JSON
```

## Application

Contém casos de uso e portas.

Exemplo:

```text
application/shopping/
├── port/
│   ├── in/
│   └── out/
└── service/
```

Entrada:

```text
CreateShoppingSessionUseCase
AddShoppingItemUseCase
CompleteShoppingSessionUseCase
```

Saída:

```text
LoadShoppingSessionPort
SaveShoppingSessionPort
LoadProductPort
```

## Adapter IN

Entradas para o sistema.

```text
adapter/in/
├── web/
│   ├── shopping/
│   ├── catalog/
│   └── supermarket/
└── sync/
```

REST:

```text
HTTP
 ↓
Controller
 ↓
Application Port IN
 ↓
Use Case
 ↓
Domain
```

## Adapter OUT

Saídas do sistema.

```text
adapter/out/
├── persistence/
│   ├── shopping/
│   ├── catalog/
│   └── supermarket/
└── security/
```

Persistência:

```text
Application Port OUT
        ↑
Persistence Adapter
        ↓
JPA / PostgreSQL
```

Entidades JPA e repositories Spring ficam no adapter de persistência, não no domínio.

## Regra de dependências

A direção desejada é:

```text
Adapters
   ↓
Application
   ↓
Domain
```

O domínio nunca depende dos adapters.

---

# 13. Modularidade e testes arquiteturais

O backend continua sendo um **monólito modular**, embora a estrutura física principal seja Hexagonal por camadas.

Os contextos:

```text
shopping
catalog
supermarket
pricing
identity
synchronization
```

devem manter limites claros dentro de `domain`, `application` e `adapter`.

Um contexto não deve acessar diretamente detalhes internos de outro quando houver uma interface pública mais adequada.

Para verificar essas regras, a preferência será:

```text
ArchUnit
```

e testes arquiteturais próprios.

**Spring Modulith permanece opcional**, não obrigatório. Ele poderá ser adotado caso agregue valor sem obrigar o projeto a abandonar a estrutura Hexagonal escolhida.

A aplicação continuará sendo entregue como uma única unidade:

```text
app-api.jar
```

Microsserviços só serão considerados se houver necessidade real futura.

---

# 14. API

A comunicação inicial será REST/HTTPS.

Endpoints conceituais:

```text
/api/v1/products
/api/v1/products/{barcode}

/api/v1/supermarkets

/api/v1/shoppings
/api/v1/shoppings/{id}
/api/v1/shoppings/{id}/items

/api/v1/prices/{barcode}
/api/v1/prices/{barcode}/history

/api/v1/sync
```

---

# 15. Produto x preço

Produto e preço serão entidades/conceitos separados.

Exemplo:

```text
Produto:
Café Santa Clara 500 g
EAN: 789...
```

Pode existir em diferentes estabelecimentos:

```text
São Luiz       R$ 18,99
Assaí          R$ 17,29
Atacadão       R$ 16,49
```

O preço será registrado com contexto temporal:

```text
ProductPrice

product
supermarket
price
recordedAt
```

Preços anteriores não deverão ser simplesmente sobrescritos.

Isso permitirá formar histórico desde a V1.

---

# 16. Histórico de preços

Mesmo antes de a interface de histórico existir, a V1 já deverá registrar dados necessários para versões futuras.

Exemplo:

```text
Café Santa Clara 500 g
São Luiz

29/09/2026  R$ 18,99
06/10/2026  R$ 17,49
14/10/2026  R$ 18,29
21/10/2026  R$ 16,99
```

Esse histórico será utilizado pela V2 e V3.

---

# 17. Autenticação

A autenticação não será requisito para o primeiro fluxo principal da V1.

Arquitetura deverá ficar preparada para:

- modo visitante;
- Google;
- Apple;
- OAuth2 / OpenID Connect;
- Spring Security;
- sincronização associada ao usuário.

Fluxo futuro possível:

```text
Continuar como visitante
Continuar com Google
Continuar com Apple
```

Um usuário visitante poderá posteriormente vincular seus dados a uma conta.

---

# 18. Roadmap

## V1 — Minha compra agora

### Objetivo

> Quanto estou gastando?

### Funcionalidades

- criar uma nova compra;
- escolher ou cadastrar supermercado;
- definir limite de gastos opcional;
- escanear código de barras EAN/UPC;
- identificar produto existente;
- cadastrar rapidamente produto desconhecido;
- informar preço encontrado no mercado;
- informar quantidade;
- adicionar produto ao carrinho;
- remover produto;
- alterar quantidade;
- alterar preço;
- total atualizado em tempo real;
- valor restante do orçamento;
- percentual utilizado do limite;
- finalizar compra;
- histórico básico de compras anteriores;
- registrar preço do produto naquele supermercado;
- funcionamento offline durante a compra;
- sincronização posterior;
- haptic feedback;
- toasts;
- gestures;
- bottom sheets;
- animações de transição;
- tratamento inteligente de produto já presente no carrinho.

### Produto escaneado novamente

Se um produto já estiver no carrinho, uma nova leitura deverá permitir adicionar outra unidade rapidamente.

Exemplo:

```text
Coca-Cola 2L adicionada novamente
Quantidade: 2
```

A interação deverá ser rápida, com animação e feedback háptico.

### Navegação principal inicial

```text
Início
Escanear
Carrinho
Histórico
```

---

# 19. V2 — Histórico e comparação

### Objetivo

> Estou pagando caro?

### Funcionalidades

- histórico completo de preços;
- comparação entre supermercados;
- menor preço registrado;
- último preço;
- preço médio;
- indicação de aumento ou redução;
- gráfico de histórico;
- comparação de compras anteriores;
- gastos semanais;
- gastos mensais;
- gastos por supermercado;
- produtos comprados com maior frequência;
- pesquisa de produtos;
- favoritos;
- lista de compras;
- conta do usuário;
- login Google;
- login Apple;
- sincronização entre dispositivos;
- backup em servidor.

### Informação temporal obrigatória

Todo preço comparado deverá mostrar quando foi registrado.

Exemplo:

```text
Atacadão
R$ 16,49
registrado há 3 dias
```

Isso reduz o risco de apresentar preços antigos como se ainda estivessem vigentes.

---

# 20. V3 — Planejamento e economia

### Objetivo

> Onde devo comprar?

### Funcionalidades

- lista de compras inteligente;
- estimativa de preço antes de sair de casa;
- comparação da lista inteira entre mercados;
- estimativa de economia;
- sugestão dos estabelecimentos com preços registrados mais vantajosos;
- divisão de itens entre estabelecimentos;
- orçamento mensal;
- categorias de gastos;
- análise mensal;
- alertas de preço;
- comparação com média histórica pessoal.

Exemplo:

```text
Lista do mês

São Luiz       R$ 473
Assaí          R$ 431
Atacadão       R$ 418
```

Possível evolução:

```text
Comprando tudo no Atacadão:
R$ 418

Dividindo entre mercados:
R$ 397

Diferença:
R$ 21
```

---

# 21. Funcionalidades futuras — pós V3

Funcionalidades interessantes que não serão prioridade nas três primeiras versões:

## OCR de etiqueta

Apontar a câmera para a etiqueta da prateleira e reconhecer automaticamente o preço.

## NFC-e / nota fiscal

Importação de produtos e valores após a compra.

## Crowdsourcing de preços

Preços compartilhados por diferentes usuários.

## Ofertas públicas

Integrações com promoções disponíveis publicamente.

## Integrações com supermercados

APIs ou serviços oficiais quando disponíveis.

## Inteligência avançada

Sugestões e análises baseadas no histórico de consumo.

Essas funcionalidades devem ser avaliadas apenas depois que o fluxo principal estiver consolidado.

---

# 22. Princípios de produto

1. O aplicativo deve funcionar bem dentro do supermercado.
2. A compra não pode depender de conexão constante.
3. Adicionar um produto deve ser extremamente rápido.
4. O usuário deve sempre visualizar o total atual.
5. Limite/orçamento deve ser opcional, porém disponível desde a V1.
6. Preços precisam manter contexto de data e estabelecimento.
7. Histórico não deve ser perdido por sobrescrita.
8. Funcionalidades futuras devem aproveitar dados coletados nas versões anteriores.
9. Não adicionar complexidade arquitetural sem benefício concreto.
10. A experiência mobile deve parecer natural no Android e iOS.

---

# 23. Próxima etapa

Detalhar completamente a **V1** antes da implementação.

Devem ser definidos:

- telas;
- fluxo completo de compra;
- estados de cada tela;
- regras de negócio;
- entidades iniciais;
- produtos sem código de barras;
- produtos vendidos por peso;
- produtos vendidos por unidade;
- promoções;
- produtos repetidos;
- alteração de preço;
- exclusão;
- cancelamento de compra;
- compra offline;
- conflitos de sincronização;
- supermercados;
- histórico mínimo;
- estados vazios;
- erros;
- permissões de câmera;
- comportamento do scanner.

Após esse refinamento, definir:

1. modelo de domínio;
2. modelo de banco local;
3. modelo PostgreSQL;
4. contratos da API;
5. estrutura dos repositórios;
6. backlog técnico da V1;
7. ordem de implementação.

---

# 24. Refinamento funcional da V1

A V1 deve ser pequena o suficiente para ser concluída, mas completa o bastante para ser utilizada em uma compra real de supermercado.

## 24.1 Fluxo principal

```text
Abrir o app
    ↓
Início
    ↓
Nova compra
    ↓
Escolher supermercado
    ↓
Definir limite (opcional)
    ↓
Iniciar compra
    ↓
Escanear produto
ou
Adicionar manualmente
    ↓
Produto encontrado
    ↓
Informar preço
    ↓
Quantidade
    ↓
Adicionar ao carrinho
    ↓
Continuar escaneando
    ↓
Finalizar compra
    ↓
Histórico
```

## 24.2 Uma compra ativa por vez

Na V1 existirá apenas uma compra ativa por usuário/dispositivo.

Se houver uma compra em andamento:

```text
Compra em andamento

Supermercado São Luiz
R$ 237,40 de R$ 500,00

[ Continuar compra ]
```

Essa decisão simplifica a experiência e a sincronização inicial.

# 25. Home da V1

Sem compra ativa:

```text
Bom dia, João

Pronto para fazer suas compras?

[ + Nova compra ]

Últimas compras
```

Com compra ativa:

```text
Compra em andamento

São Luiz

Limite
R$ 500,00

Total
R$ 237,40

Restante
R$ 262,60

████████░░░░░░░ 47%

[ Continuar compra ]
```

Navegação principal:

```text
Início      Escanear      Carrinho      Histórico
```

# 26. Nova compra

Ao iniciar uma nova compra:

```text
Nova compra

Supermercado
[ São Luiz                 ▼ ]

Limite de gastos
[ R$ 500,00                  ]

☑ Quero acompanhar meu limite

[ Começar compra ]
```

Na V1 o supermercado terá cadastro simples:

```text
Supermarket
- id
- name
- createdAt
- updatedAt
```

Endereço, GPS, CNPJ, rede e filial ficam para versões futuras.

# 27. Limite de gastos

O limite é opcional, alterável durante a compra e nunca bloqueia novas adições.

```text
0–79%   estado normal
80%     feedback visual suave
90%     alerta mais perceptível
100%    limite atingido
>100%   limite ultrapassado
```

# 28. Scanner

A leitura de código de barras será o fluxo principal.

```text
←       Escanear             ⚡

Aponte para o código de barras

       [ área de leitura ]

     Adicionar manualmente
```

Ao detectar um produto:

```text
✓ Produto encontrado
```

A ação deve utilizar feedback háptico leve e bottom sheet.

# 29. Produto conhecido

Exemplo:

```text
Coca-Cola 2L

7894900011517

Último preço neste mercado
R$ 9,99

Preço hoje
[ R$ 10,49 ]

Quantidade
[-]      1      [+]

[ Adicionar ao carrinho ]
```

O preço anterior pode ser exibido como referência, mas nunca assumido automaticamente como preço atual.

# 30. Produto desconhecido

Cadastro rápido:

```text
Produto não encontrado

Código
7891234567890

Nome
[ __________________ ]

Marca
[ __________________ ] opcional

Descrição / tamanho
[ __________________ ] opcional

Preço
[ R$ ______ ]

[ Adicionar ]
```

Nome e preço são suficientes para concluir o fluxo.

# 31. Produto repetido

Se o produto já estiver no carrinho e a precificação for compatível:

```text
✓ Coca-Cola adicionada
Quantidade: 2
```

A nova leitura incrementa a quantidade sem reabrir o formulário completo.

# 32. Produtos sem código de barras

A V1 precisa aceitar produtos manuais.

```text
Novo item

Nome
[ Banana Prata ]

Tipo
(•) Unidade
( ) Peso
```

Por unidade:

```text
Preço
R$ 4,50

Quantidade
2

Subtotal
R$ 9,00
```

Por peso:

```text
Banana Prata

Preço por kg
R$ 6,99

Peso
0,824 kg

Subtotal
R$ 5,76
```

# 33. Tipos de medida

```text
MeasurementType
- UNIT
- WEIGHT
```

`Arroz 5 kg` continua sendo `UNIT`, pois é vendido como pacote fechado.

Banana, tomate, carnes e similares podem ser `WEIGHT`.

# 34. Promoções simples

Tipos iniciais:

```text
PricingType
- REGULAR
- BUNDLE
```

Exemplo:

```text
3 por R$ 10,00
```

Representação:

```text
bundleQuantity = 3
bundlePriceCents = 1000
```

Preço equivalente:

```text
R$ 3,33/un.
```

Promoções complexas ficam fora da V1:

- leve 3, pague 2;
- desconto na segunda unidade;
- desconto com cartão;
- cashback;
- cupom;
- desconto progressivo.

# 35. Carrinho

Exemplo:

```text
Carrinho

Limite
R$ 500,00

Total
R$ 412,35

Restante
R$ 87,65

Arroz Tio João
1 × R$ 28,90
                   R$ 28,90

Coca-Cola 2L
2 × R$ 8,99
                   R$ 17,98
```

Interações:

```text
tap → editar
swipe ← → excluir
[-] quantidade [+]
```

# 36. Alteração de preço durante a compra

O usuário pode corrigir valores livremente enquanto a compra estiver ativa.

```text
10,49
 ↓
9,99
```

Essas correções não geram múltiplos registros históricos.

# 37. Finalização

Exemplo:

```text
Finalizar compra

São Luiz

18 itens

Total calculado
R$ 412,35

Limite
R$ 500,00

Restante
R$ 87,65
```

Campo opcional:

```text
Total do caixa
[ R$ ______ ]
```

Se informado:

```text
Calculado pelo app
R$ 412,35

Total do caixa
R$ 414,10

Diferença
R$ 1,75
```

A diferença não altera automaticamente os itens.

# 38. Histórico básico

A V1 mostra compras finalizadas e seus itens, sem gráficos ou análises avançadas.

# 39. Cancelamento

Estados:

```text
ShoppingStatus
- ACTIVE
- COMPLETED
- CANCELED
```

Compras canceladas não geram histórico de preços definitivo.

# 40. Comportamento offline

Sem conexão:

```text
Sem conexão
Sua compra continua funcionando normalmente.
```

Continuam funcionando:

```text
scanner
produtos
preço
carrinho
limite
histórico local
finalização
```

Quando a conexão voltar, a sincronização ocorre discretamente.

# 41. Permissão de câmera

Se a câmera for negada:

```text
Precisamos da câmera para ler códigos de barras.

[ Permitir câmera ]
ou
[ Adicionar produto manualmente ]
```

A recusa da câmera não inutiliza o app.

# 42. Fora da V1

Ficam fora inicialmente:

- autenticação obrigatória;
- comparação entre supermercados;
- gráficos;
- OCR de etiquetas;
- leitura de nota fiscal;
- compartilhamento familiar;
- ofertas online;
- lista inteligente;
- IA;
- GPS do mercado;
- crowdsourcing;
- categorias automáticas;
- promoções complexas.

# 43. Telas principais da V1

| Tela | Responsabilidade |
|---|---|
| Início | Compra ativa e histórico recente |
| Nova compra | Mercado e limite |
| Scanner | Leitura de código de barras |
| Produto | Preço e quantidade |
| Item manual | Produto sem barcode / peso |
| Carrinho | Controle da compra |
| Finalizar compra | Revisão e total |
| Histórico | Compras finalizadas |

Decisões consideradas fechadas:

1. uma compra ativa por vez;
2. limite opcional e alterável;
3. produtos por peso já na V1;
4. histórico de preços consolidado somente ao finalizar a compra.

# 44. Modelo de domínio da V1

Conceitos principais:

```text
Product
   │
   ▼
ShoppingItem ──────── Shopping
   │                    │
   │                    ▼
   │                Supermarket
   │
   └──────────────► PriceObservation
```

# 45. Shopping

`Shopping` será o Aggregate Root principal.

```text
Shopping
- id
- supermarketId
- budget?
- status
- startedAt
- completedAt?
- checkoutTotal?
- items
```

Comportamentos:

```text
addItem()
removeItem()
changeItemQuantity()
changeBudget()
complete()
cancel()
```

Compras `COMPLETED` ou `CANCELED` não devem ser alteradas livremente.

# 46. Representação de dinheiro

Regra principal:

> Não utilizar `float` ou `double` para valores monetários.

Na interface o usuário continua vendo normalmente:

```text
R$ 10,49
R$ 3,33
R$ 412,35
```

Internamente, a representação preferida será em centavos inteiros:

```text
R$ 1,00   → 100
R$ 10,49  → 1049
R$ 99,99  → 9999
```

Motivo: ponto flutuante binário pode produzir pequenas imprecisões, inadequadas para dinheiro.

## Frontend

```text
priceCents = 1049
```

A apresentação é formatada em `pt-BR`.

## Backend

Preferência por um Value Object `Money`.

No Java, `BigDecimal` poderá ser usado internamente quando necessário, sem utilizar `double`.

## Persistência

Preferência atual:

```text
SQLite      → centavos inteiros
API         → centavos inteiros ou contrato equivalente exato
PostgreSQL  → centavos inteiros
```

A intenção é manter consistência de ponta a ponta.

# 47. Representação de peso

Também não será usado ponto flutuante para armazenar peso.

```text
0,824 kg
```

será persistido como:

```text
824 gramas
```

Exemplo:

```text
pricePerKgCents = 699
weightGrams = 824
```

representa:

```text
R$ 6,99/kg
824 g
```

# 48. Product

```text
Product
- id
- barcode?
- name
- brand?
- description?
- measurementType
```

O barcode é opcional.

Exemplos válidos:

```text
Coca-Cola 2L
barcode: 7894900011517
```

```text
Banana Prata
barcode: null
```

# 49. ShoppingItem

```text
ShoppingItem
- id
- shoppingId
- productId
- productNameSnapshot
- measurementType
- pricingType
- quantity?
- weightGrams?
- unitPriceCents?
- bundleQuantity?
- bundlePriceCents?
- subtotalCents
```

`productNameSnapshot` preserva o texto histórico mesmo se o cadastro mestre for renomeado futuramente.

# 50. Subtotais

O subtotal não será digitado manualmente.

Regular:

```text
quantity = 3
unitPriceCents = 899

subtotalCents = 2697
```

Por peso:

```text
weightGrams = 824
pricePerKgCents = 699

subtotalCents ≈ 576
```

As regras de arredondamento serão explicitadas no domínio.

# 51. Total da compra

O total calculado será derivado:

```text
calculatedTotal = SUM(item.subtotal)
```

`checkoutTotal` será um valor opcional informado pelo usuário para comparação com o caixa.

# 52. PriceObservation

Representa um preço efetivamente observado em uma compra finalizada.

```text
PriceObservation
- id
- productId
- supermarketId
- shoppingId
- observedAt
- comparisonBasis
- normalizedPriceCents
- originalPricingType
- unitPriceCents?
- bundleQuantity?
- bundlePriceCents?
```

Exemplo normal:

```text
Coca-Cola 2L
São Luiz
R$ 9,99

normalizedPriceCents = 999
comparisonBasis = UNIT
```

Exemplo por peso:

```text
Patinho
R$ 39,90/kg

normalizedPriceCents = 3990
comparisonBasis = KG
```

Exemplo bundle:

```text
3 por R$ 10,00

originalPricingType = BUNDLE
bundleQuantity = 3
bundlePriceCents = 1000
normalizedPriceCents = 333
comparisonBasis = UNIT
```

# 53. Consolidação do histórico

`PriceObservation` só nasce quando a compra é finalizada.

Durante `ACTIVE`, correções de preço não geram histórico adicional.

```text
Shopping COMPLETED
        ↓
PriceObservation
PriceObservation
PriceObservation
...
```

# 54. Produto repetido no domínio

Se o produto já existir na compra e a configuração de preço for compatível:

```text
quantity++
```

Se houver configurações incompatíveis, como preço regular e bundle, linhas distintas são permitidas.

# 55. IDs e offline-first

Os IDs serão gerados no dispositivo.

Padrão:

```text
UUID
```

O mesmo ID será enviado ao servidor.

Evitar:

```text
localId → serverId
```

Isso simplifica a sincronização.

# 56. Sync como infraestrutura

`sync_queue` não pertence ao domínio de negócio.

Estrutura conceitual:

```text
SyncOperation
- id
- aggregateType
- aggregateId
- operation
- status
- attempts
- createdAt
```

Estados:

```text
PENDING
SYNCING
SYNCED
FAILED
```

# 57. Modelo conceitual consolidado

```text
Supermarket
    │
    ▼
Shopping
    │
    ├──────────► ShoppingItem ──────────► Product
    │
    └──────────► PriceObservation
```

Relacionamentos principais:

```text
Supermarket 1 ── N Shopping
Shopping    1 ── N ShoppingItem
Product     1 ── N ShoppingItem
Shopping    1 ── N PriceObservation
Product     1 ── N PriceObservation
Supermarket 1 ─ N PriceObservation
```

# 58. Próximos passos definidos

Ordem de trabalho:

```text
Modelo de banco
      ↓
Contratos da API
      ↓
Estratégia de sincronização
      ↓
Detalhamento final das telas/estados
      ↓
Estrutura dos repositórios
      ↓
Backlog técnico da V1
      ↓
Criação dos projetos
      ↓
Implementação local da V1
      ↓
Backend
      ↓
Sincronização
      ↓
Testes
      ↓
Primeira versão utilizável
```

# 59. Próxima etapa imediata

Modelar:

1. SQLite local;
2. PostgreSQL;
3. tabelas;
4. PKs e FKs;
5. índices;
6. constraints;
7. dados exclusivamente locais;
8. migrations iniciais.

Depois:

- contratos REST;
- conflitos de sincronização;
- backlog técnico;
- implementação.

# 60. Estratégia de implementação

Primeiro:

```text
Ionic
  ↓
SQLite
  ↓
Fluxo completo funcionando offline
```

Depois:

```text
Spring Boot API
+
PostgreSQL
+
sincronização
```

A prioridade é validar primeiro a experiência principal da compra no supermercado antes de adicionar complexidade de rede.

---

# 61. Modelo de banco da V1

O modelo físico seguirá o domínio, mas separará claramente dados de negócio e metadados de sincronização.

## 61.1 Decisões gerais

- UUID v4 gerado no dispositivo para entidades sincronizáveis.
- Código de barras armazenado como `TEXT`/`VARCHAR`, nunca como número.
- Valores monetários persistidos em centavos inteiros.
- Peso persistido em gramas inteiras.
- Datas locais em UTC como epoch milliseconds no SQLite.
- Datas no servidor como `TIMESTAMPTZ`.
- SQLite será a fonte operacional imediata durante uma compra.
- PostgreSQL será a fonte persistente do backend.
- `sync_queue` e `sync_state` são infraestrutura e não fazem parte do domínio.

# 62. SQLite — tabelas de domínio

## 62.1 products

```text
products
- id TEXT PK
- barcode TEXT NULL UNIQUE
- name TEXT NOT NULL
- brand TEXT NULL
- description TEXT NULL
- measurement_type TEXT NOT NULL
- created_at_ms INTEGER NOT NULL
- updated_at_ms INTEGER NOT NULL
```

Regras:

```text
measurement_type ∈ { UNIT, WEIGHT }
```

`barcode` é texto porque códigos de barras podem possuir zeros à esquerda e não são valores matemáticos.

## 62.2 supermarkets

```text
supermarkets
- id TEXT PK
- name TEXT NOT NULL
- created_at_ms INTEGER NOT NULL
- updated_at_ms INTEGER NOT NULL
```

O nome não será `UNIQUE` na V1, pois futuramente podem existir filiais com o mesmo nome.

## 62.3 shoppings

```text
shoppings
- id TEXT PK
- supermarket_id TEXT NOT NULL FK
- budget_cents INTEGER NULL
- status TEXT NOT NULL
- started_at_ms INTEGER NOT NULL
- finished_at_ms INTEGER NULL
- checkout_total_cents INTEGER NULL
- created_at_ms INTEGER NOT NULL
- updated_at_ms INTEGER NOT NULL
```

Estados:

```text
ACTIVE
COMPLETED
CANCELED
```

Regras:

- `budget_cents`, quando informado, deve ser positivo;
- `checkout_total_cents`, quando informado, não pode ser negativo;
- compra `ACTIVE` não possui `finished_at_ms`;
- compra `COMPLETED` ou `CANCELED` possui `finished_at_ms`;
- apenas uma compra `ACTIVE` pode existir localmente.

A restrição de uma compra ativa será reforçada por índice parcial no SQLite.

## 62.4 shopping_items

A persistência separará preço unitário de preço por kg para evitar ambiguidade.

```text
shopping_items
- id TEXT PK
- shopping_id TEXT NOT NULL FK
- product_id TEXT NOT NULL FK
- product_name_snapshot TEXT NOT NULL
- measurement_type TEXT NOT NULL
- pricing_type TEXT NOT NULL

- quantity_units INTEGER NULL
- weight_grams INTEGER NULL

- unit_price_cents INTEGER NULL
- price_per_kg_cents INTEGER NULL

- bundle_quantity INTEGER NULL
- bundle_price_cents INTEGER NULL

- subtotal_cents INTEGER NOT NULL

- created_at_ms INTEGER NOT NULL
- updated_at_ms INTEGER NOT NULL
```

### UNIT + REGULAR

Obrigatório:

```text
quantity_units > 0
unit_price_cents > 0
```

Não utilizado:

```text
weight_grams
price_per_kg_cents
bundle_quantity
bundle_price_cents
```

### WEIGHT + REGULAR

Obrigatório:

```text
weight_grams > 0
price_per_kg_cents > 0
```

Não utilizado:

```text
quantity_units
unit_price_cents
bundle_quantity
bundle_price_cents
```

### UNIT + BUNDLE

Obrigatório:

```text
quantity_units > 0
bundle_quantity > 1
bundle_price_cents > 0
```

Na V1, `quantity_units` deve ser múltiplo de `bundle_quantity`.

Exemplo:

```text
3 por R$ 10
quantity_units = 6
bundle_quantity = 3
bundle_price_cents = 1000
subtotal_cents = 2000
```

Se houver uma quarta unidade fora da promoção, ela entra como outra linha `REGULAR`.

### WEIGHT + BUNDLE

Não suportado na V1.

## 62.5 price_observations

```text
price_observations
- id TEXT PK
- shopping_item_id TEXT NOT NULL UNIQUE FK
- product_id TEXT NOT NULL FK
- supermarket_id TEXT NOT NULL FK
- shopping_id TEXT NOT NULL FK

- observed_at_ms INTEGER NOT NULL
- comparison_basis TEXT NOT NULL
- normalized_price_cents INTEGER NOT NULL
- original_pricing_type TEXT NOT NULL

- unit_price_cents INTEGER NULL
- price_per_kg_cents INTEGER NULL
- bundle_quantity INTEGER NULL
- bundle_price_cents INTEGER NULL
```

`shopping_item_id` será `UNIQUE`.

Isso garante que uma mesma linha de compra não gere duas observações históricas se a finalização for processada novamente.

Bases:

```text
UNIT
KG
```

# 63. SQLite — infraestrutura de sincronização

## 63.1 sync_queue

```text
sync_queue
- id TEXT PK
- aggregate_type TEXT NOT NULL
- aggregate_id TEXT NOT NULL
- operation TEXT NOT NULL
- payload_json TEXT NOT NULL
- status TEXT NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- next_attempt_at_ms INTEGER NULL
- last_error TEXT NULL
- created_at_ms INTEGER NOT NULL
- updated_at_ms INTEGER NOT NULL
```

Estados:

```text
PENDING
SYNCING
SYNCED
FAILED
```

O payload é armazenado na fila para que uma operação continue sincronizável mesmo se a entidade local tiver sido removida.

## 63.2 sync_state

```text
sync_state
- aggregate_type TEXT NOT NULL
- aggregate_id TEXT NOT NULL
- server_version INTEGER NULL
- last_synced_at_ms INTEGER NULL

PK (aggregate_type, aggregate_id)
```

Essa tabela mantém metadados técnicos fora das tabelas de domínio.

## 63.3 app_metadata

```text
app_metadata
- key TEXT PK
- value TEXT NOT NULL
```

Uso previsto:

```text
installation_id
last_successful_sync_at
database_schema_version
```

# 64. Índices SQLite

Índices iniciais:

```text
products(barcode) UNIQUE
shopping_items(shopping_id)
shopping_items(product_id)
shoppings(status)
shoppings(supermarket_id, started_at_ms)
price_observations(product_id, supermarket_id, observed_at_ms)
price_observations(shopping_id)
sync_queue(status, next_attempt_at_ms)
```

Índice parcial:

```text
UNIQUE(status)
WHERE status = 'ACTIVE'
```

Esse índice garante uma única compra ativa no banco local.

# 65. Relacionamentos e política de exclusão

Política inicial:

```text
Supermarket ← Shopping
Product     ← ShoppingItem
Shopping    ← ShoppingItem
ShoppingItem ← PriceObservation
```

Regras:

- compras finalizadas não são removidas fisicamente;
- observações de preço são históricas e imutáveis;
- produto referenciado por histórico não deve ser apagado fisicamente;
- supermercado utilizado em compras históricas não deve ser apagado fisicamente;
- item pode ser removido fisicamente enquanto a compra estiver `ACTIVE`;
- a remoção local deve gerar a operação correspondente na `sync_queue` dentro da mesma transação.

# 66. PostgreSQL — diferenças do modelo local

O modelo lógico será equivalente, usando tipos nativos do PostgreSQL.

Mapeamentos:

```text
SQLite TEXT UUID      → PostgreSQL UUID
SQLite INTEGER cents  → PostgreSQL BIGINT
SQLite INTEGER grams  → PostgreSQL INTEGER
SQLite *_at_ms        → PostgreSQL TIMESTAMPTZ
SQLite TEXT enum      → VARCHAR + CHECK inicialmente
```

Não será necessário criar tipos ENUM nativos do PostgreSQL na V1. `VARCHAR + CHECK` facilita migrations futuras.

# 67. Identidade anônima para sincronização

A V1 não obrigará o usuário a fazer login.

Entretanto, o backend precisa saber a quem pertencem os dados.

Estratégia proposta:

```text
Primeira sincronização
        ↓
cria usuário anônimo técnico
        ↓
user_id UUID
        ↓
token do dispositivo
```

Isso não representa uma tela de cadastro.

Futuramente:

```text
ANONYMOUS
   ↓
vincular Google / Apple
   ↓
ACCOUNT
```

mantendo o mesmo conjunto de dados.

Tabela mínima no servidor:

```text
users
- id UUID PK
- account_type VARCHAR NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- upgraded_at TIMESTAMPTZ NULL
```

Inicialmente:

```text
account_type = ANONYMOUS
```

Na V2 poderão ser adicionadas identidades Google/Apple.

# 68. Escopo de propriedade no PostgreSQL

Tabelas mestre pertencentes ao usuário terão `user_id`.

```text
products.user_id
supermarkets.user_id
shoppings.user_id
```

Assim:

```text
UNIQUE(user_id, barcode)
```

substitui um `barcode` globalmente único.

Isso permite que dois usuários tenham catálogos próprios.

A regra de uma compra ativa no servidor será:

```text
UNIQUE(user_id)
WHERE status = 'ACTIVE'
```

# 69. Versionamento otimista

Entidades mutáveis no servidor terão:

```text
version BIGINT NOT NULL DEFAULT 0
```

Aplicável inicialmente a:

```text
products
supermarkets
shoppings
shopping_items
```

No Spring/JPA esse campo poderá ser utilizado com `@Version`.

A versão do servidor não entra no domínio; o cliente a mantém em `sync_state.server_version`.

Isso será usado posteriormente na estratégia de conflitos.

# 70. Finalização transacional

Finalizar uma compra local deve ocorrer dentro de uma única transação SQLite:

```text
1. validar compra ACTIVE
2. recalcular subtotais
3. recalcular total
4. mudar Shopping → COMPLETED
5. definir finished_at
6. salvar checkout_total, se informado
7. gerar PriceObservation para cada ShoppingItem
8. inserir operações necessárias na sync_queue
9. COMMIT
```

Se qualquer etapa falhar:

```text
ROLLBACK
```

Nenhuma compra pode ficar parcialmente finalizada.

No servidor será aplicada a mesma ideia com transação PostgreSQL.

# 71. Idempotência do histórico

Duas proteções serão utilizadas:

```text
PriceObservation.shopping_item_id UNIQUE
```

e operações de sincronização com IDs únicos.

Assim, repetir uma requisição de finalização não deve duplicar histórico de preços.

# 72. Migrations

## SQLite

Estrutura sugerida:

```text
database/migrations/
├── 001_initial_schema.sql
├── 002_add_....sql
└── ...
```

As migrations serão aplicadas durante a inicialização do banco local.

## PostgreSQL / Flyway

```text
src/main/resources/db/migration/

V1__initial_schema.sql
V2__....sql
```

Nunca depender de `ddl-auto=update` em produção.

# 73. Decisões fechadas após o modelo de banco

1. dinheiro em centavos inteiros;
2. peso em gramas inteiras;
3. barcode como texto;
4. UUID gerado no dispositivo;
5. `unit_price_cents` e `price_per_kg_cents` separados;
6. `PriceObservation` referencia o `ShoppingItem` de origem;
7. finalização é transacional;
8. histórico possui proteção contra duplicidade;
9. metadados de sync ficam fora do domínio;
10. backend utiliza identidade anônima técnica sem exigir login na V1;
11. uma compra ativa por usuário;
12. `version` do servidor será preparado para controle otimista de concorrência.

# 74. Próxima etapa

Com o modelo de banco definido, o próximo passo é desenhar os contratos REST da V1:

```text
produtos
supermercados
compras
itens
finalização
histórico básico
bootstrap/sync
```

Também serão definidos:

- formato padrão de erro;
- idempotency key;
- payloads em centavos/gramas;
- versionamento da API;
- contratos de criação e atualização;
- endpoints específicos de sincronização.

---

# 75. API REST da V1

A API será versionada em:

```text
/api/v1
```

O aplicativo seguirá estratégia **local-first**:

```text
UI
 ↓
SQLite
 ↓
sync_queue
 ↓
API
 ↓
PostgreSQL
```

A interface não dependerá de resposta da API para concluir ações básicas durante a compra.

Os endpoints REST e o adaptador de sincronização chamarão os mesmos casos de uso no backend.

# 76. Ajuste de nomenclatura

Na API, o recurso de compra será chamado:

```text
shopping-sessions
```

em vez de `shoppings`, pois representa melhor uma compra que pode estar `ACTIVE`, `COMPLETED` ou `CANCELED`.

Antes da implementação será avaliado renomear também a entidade Java `Shopping` para `ShoppingSession`.

# 77. Convenções gerais

## JSON

```text
Content-Type: application/json
```

## IDs

UUIDs gerados no cliente para entidades que podem nascer offline.

## Dinheiro

Centavos inteiros:

```json
{"unitPriceCents":1049}
```

representa `R$ 10,49`.

## Peso

Gramas inteiras:

```json
{"weightGrams":842}
```

representa `0,842 kg`.

## Datas

ISO-8601 UTC na API:

```json
{"occurredAt":"2026-09-30T01:30:00Z"}
```

SQLite continua usando epoch milliseconds e PostgreSQL `TIMESTAMPTZ`.

## Versionamento otimista

Recursos mutáveis retornam `version`.

Se a versão enviada pelo cliente estiver desatualizada:

```text
409 CONFLICT
VERSION_CONFLICT
```

## Idempotência

Operações mutáveis importantes utilizam:

```text
Idempotency-Key: <UUID>
```

No sync, `operationId` exerce a mesma função.

# 78. Padrão de erro

```json
{
  "timestamp": "2026-09-30T01:32:00Z",
  "status": 409,
  "code": "SHOPPING_SESSION_ALREADY_COMPLETED",
  "message": "A compra já foi finalizada.",
  "path": "/api/v1/shopping-sessions/...",
  "traceId": "8bb3e8c2c547",
  "details": []
}
```

Códigos principais:

```text
200 OK
201 CREATED
204 NO CONTENT
400 BAD REQUEST
401 UNAUTHORIZED
404 NOT FOUND
409 CONFLICT
422 UNPROCESSABLE ENTITY
500 INTERNAL SERVER ERROR
```

O backend nunca retorna stack trace para o cliente.

# 79. Identidade anônima

## POST /api/v1/auth/anonymous

Request:

```json
{
  "installationId": "f412d819-665a-4aa7-b74d-898bd58943ca",
  "installationSecret": "<32 bytes aleatórios em base64url sem padding>",
  "devicePlatform": "ANDROID",
  "appVersion": "1.0.0"
}
```

Response:

```json
{
  "userId": "c1d415e7-1960-48ad-b2da-290701feea29",
  "installationId": "f412d819-665a-4aa7-b74d-898bd58943ca",
  "accountType": "ANONYMOUS",
  "accessToken": "<token>",
  "expiresAt": "2026-10-30T01:30:00Z"
}
```

A implementação preserva o mesmo usuário quando `installationId` e a prova `installationSecret` conferem, e renova o token opaco. A prova deve ser gerada com CSPRNG e guardada de forma segura no dispositivo; conhecer somente o UUID não autentica nem recupera a conta. A resposta perdida pode ser recuperada por nova chamada com a mesma prova, sem tela de cadastro. Contrato implementado e status atual estão em `API-LOCAL.md` e `STATUS-DESENVOLVIMENTO.md` nesta pasta; idempotência da identidade não significa repetir o mesmo token após renovação.

# 80. Produtos

## Buscar por código de barras

```text
GET /api/v1/products/by-barcode/{barcode}?supermarketId={uuid}
```

Response:

```json
{
  "id": "0a388ec3-e03c-43a5-a33b-88cd3d685b0b",
  "barcode": "7894900011517",
  "name": "Coca-Cola 2L",
  "brand": "Coca-Cola",
  "description": "Original 2 litros",
  "measurementType": "UNIT",
  "version": 2,
  "lastPrice": {
    "supermarketId": "b9fa4e18-3dcf-4795-8539-7ad32fe40fac",
    "priceCents": 999,
    "comparisonBasis": "UNIT",
    "observedAt": "2026-09-23T18:42:00Z"
  }
}
```

Se não existir:

```text
404 PRODUCT_NOT_FOUND
```

## Pesquisar produtos

```text
GET /api/v1/products?query=banana&limit=20&cursor=...
```

Usado para reutilizar produtos manuais.

## Criar produto

```text
POST /api/v1/products
```

```json
{
  "id": "db446681-50fc-43af-9127-e7ca9aeb443a",
  "barcode": "7891234567890",
  "name": "Produto Exemplo",
  "brand": "Marca",
  "description": "500 g",
  "measurementType": "UNIT",
  "occurredAt": "2026-09-30T01:40:00Z"
}
```

Possível conflito:

```text
409 BARCODE_ALREADY_EXISTS
```

por usuário.

## Editar produto

```text
PATCH /api/v1/products/{productId}
```

```json
{
  "version": 2,
  "name": "Coca-Cola Original 2L",
  "brand": "Coca-Cola",
  "description": "Garrafa 2 litros",
  "measurementType": "UNIT"
}
```

# 81. Supermercados

## Listar/pesquisar

```text
GET /api/v1/supermarkets?query=sao&limit=20&cursor=...
```

## Criar

```text
POST /api/v1/supermarkets
```

```json
{
  "id": "b9fa4e18-3dcf-4795-8539-7ad32fe40fac",
  "name": "Supermercado São Luiz",
  "occurredAt": "2026-09-30T01:45:00Z"
}
```

## Editar

```text
PATCH /api/v1/supermarkets/{supermarketId}
```

```json
{
  "version": 1,
  "name": "São Luiz"
}
```

# 82. Shopping Sessions

## Criar compra

```text
POST /api/v1/shopping-sessions
```

```json
{
  "id": "d4acfa13-2e5a-4c31-aa8b-3bb9da23a116",
  "supermarketId": "b9fa4e18-3dcf-4795-8539-7ad32fe40fac",
  "budgetCents": 50000,
  "startedAt": "2026-09-30T01:50:00Z"
}
```

Response inclui:

```json
{
  "id": "d4acfa13-2e5a-4c31-aa8b-3bb9da23a116",
  "budgetCents": 50000,
  "status": "ACTIVE",
  "calculatedTotalCents": 0,
  "version": 0
}
```

Conflito:

```text
409 ACTIVE_SHOPPING_SESSION_ALREADY_EXISTS
```

## Recuperar compra ativa

```text
GET /api/v1/shopping-sessions/active
```

```text
200 → compra ativa
204 → nenhuma compra ativa
```

## Buscar por ID

```text
GET /api/v1/shopping-sessions/{shoppingSessionId}
```

## Alterar limite

```text
PATCH /api/v1/shopping-sessions/{shoppingSessionId}
```

```json
{
  "version": 3,
  "budgetCents": 60000
}
```

`budgetCents: null` remove o limite.

## Cancelar

```text
POST /api/v1/shopping-sessions/{shoppingSessionId}/cancel
```

```json
{
  "version": 3,
  "canceledAt": "2026-09-30T02:20:00Z"
}
```

Não gera histórico de preços.

# 83. Itens da compra

## UNIT + REGULAR

```text
POST /api/v1/shopping-sessions/{shoppingSessionId}/items
```

```json
{
  "id": "82b75b96-1665-4bea-bfec-aab226996ed6",
  "productId": "0a388ec3-e03c-43a5-a33b-88cd3d685b0b",
  "productNameSnapshot": "Coca-Cola 2L",
  "measurementType": "UNIT",
  "pricingType": "REGULAR",
  "quantityUnits": 2,
  "unitPriceCents": 1049,
  "occurredAt": "2026-09-30T02:00:00Z"
}
```

O backend recalcula e retorna:

```json
{
  "subtotalCents": 2098,
  "version": 0
}
```

## WEIGHT + REGULAR

```json
{
  "id": "...",
  "productId": "...",
  "productNameSnapshot": "Banana Prata",
  "measurementType": "WEIGHT",
  "pricingType": "REGULAR",
  "weightGrams": 824,
  "pricePerKgCents": 699,
  "occurredAt": "2026-09-30T02:05:00Z"
}
```

Resposta calculada:

```json
{
  "weightGrams": 824,
  "pricePerKgCents": 699,
  "subtotalCents": 576,
  "version": 0
}
```

## UNIT + BUNDLE

```json
{
  "id": "...",
  "productId": "...",
  "productNameSnapshot": "Refrigerante X",
  "measurementType": "UNIT",
  "pricingType": "BUNDLE",
  "quantityUnits": 6,
  "bundleQuantity": 3,
  "bundlePriceCents": 1000,
  "occurredAt": "2026-09-30T02:10:00Z"
}
```

Response:

```json
{
  "quantityUnits": 6,
  "bundleQuantity": 3,
  "bundlePriceCents": 1000,
  "subtotalCents": 2000,
  "version": 0
}
```

Na V1, `quantityUnits` deve ser múltiplo de `bundleQuantity`.

## Atualizar item

```text
PUT /api/v1/shopping-sessions/{shoppingSessionId}/items/{itemId}
```

A atualização envia o estado completo da linha para simplificar sync offline.

## Remover item

```text
DELETE /api/v1/shopping-sessions/{shoppingSessionId}/items/{itemId}?version=2
```

Response:

```text
204 NO CONTENT
```

Só permitido em compra `ACTIVE`.

# 84. Finalizar compra

```text
POST /api/v1/shopping-sessions/{shoppingSessionId}/complete
```

Exige `Idempotency-Key`.

Request:

```json
{
  "version": 8,
  "completedAt": "2026-09-30T02:30:00Z",
  "checkoutTotalCents": 41410
}
```

O backend executa em uma única transação:

```text
1. validar ACTIVE
2. validar version
3. recalcular itens
4. calcular total
5. mudar para COMPLETED
6. gravar completedAt
7. gravar checkoutTotalCents
8. gerar PriceObservation
9. COMMIT
```

Response:

```json
{
  "id": "d4acfa13-2e5a-4c31-aa8b-3bb9da23a116",
  "status": "COMPLETED",
  "itemCount": 18,
  "calculatedTotalCents": 41235,
  "checkoutTotalCents": 41410,
  "checkoutDifferenceCents": 175,
  "priceObservationsCreated": 18,
  "version": 9
}
```

Repetir com a mesma chave retorna o mesmo resultado lógico.

# 85. Histórico básico

```text
GET /api/v1/shopping-sessions?status=COMPLETED&limit=20&cursor=...
```

Response resumida:

```json
{
  "items": [
    {
      "id": "...",
      "supermarket": {
        "id": "...",
        "name": "São Luiz"
      },
      "itemCount": 18,
      "calculatedTotalCents": 41235,
      "checkoutTotalCents": 41410,
      "completedAt": "2026-09-30T02:30:00Z"
    }
  ],
  "nextCursor": null
}
```

Detalhe:

```text
GET /api/v1/shopping-sessions/{id}
```

# 86. Último preço conhecido

Para produto escaneado:

```text
GET /api/v1/products/by-barcode/{barcode}?supermarketId={id}
```

Para produto manual:

```text
GET /api/v1/products/{productId}/last-price?supermarketId={id}
```

Esse preço é apenas referência; o usuário confirma o preço atual.

# 87. Estratégia de sincronização

Os endpoints REST e o sync em lote reutilizam os mesmos casos de uso:

```text
REST Controller ─┐
                 ├── Application Use Cases
Sync Controller ─┘
```

# 88. Push

```text
POST /api/v1/sync/push
```

```json
{
  "installationId": "f412d819-665a-4aa7-b74d-898bd58943ca",
  "operations": [
    {
      "operationId": "e56ae1d7-f26b-464b-9814-24a629145819",
      "aggregateType": "SHOPPING_SESSION",
      "aggregateId": "d4acfa13-2e5a-4c31-aa8b-3bb9da23a116",
      "operation": "CREATE",
      "baseVersion": null,
      "occurredAt": "2026-09-30T01:50:00Z",
      "payload": {
        "supermarketId": "...",
        "budgetCents": 50000,
        "startedAt": "2026-09-30T01:50:00Z"
      }
    }
  ]
}
```

Response por operação:

```text
APPLIED
ALREADY_APPLIED
CONFLICT
REJECTED
```

Exemplo:

```json
{
  "results": [
    {
      "operationId": "...",
      "status": "APPLIED",
      "aggregateId": "...",
      "serverVersion": 0
    }
  ]
}
```

# 89. Conflito de sync

```json
{
  "operationId": "...",
  "status": "CONFLICT",
  "aggregateId": "...",
  "code": "VERSION_CONFLICT",
  "baseVersion": 2,
  "serverVersion": 4,
  "serverState": {
    "budgetCents": 55000,
    "status": "ACTIVE"
  }
}
```

Na V1 conflitos devem ser raros, mas o protocolo já fica preparado para múltiplos dispositivos.

# 90. Pull

```text
GET /api/v1/sync/pull?cursor={cursor}&limit=200
```

Response:

```json
{
  "changes": [
    {
      "changeId": 1048,
      "aggregateType": "PRODUCT",
      "aggregateId": "...",
      "operation": "UPSERT",
      "serverVersion": 3,
      "changedAt": "2026-09-30T02:10:00Z",
      "payload": {
        "barcode": "7894900011517",
        "name": "Coca-Cola 2L",
        "measurementType": "UNIT"
      }
    }
  ],
  "nextCursor": "1048",
  "hasMore": false
}
```

O cursor deve ser tratado como opaco pelo app.

# 91. Bootstrap

```text
GET /api/v1/sync/bootstrap
```

Response conceitual:

```json
{
  "products": [],
  "supermarkets": [],
  "activeShoppingSession": null,
  "recentShoppingSessions": [],
  "syncCursor": "1048"
}
```

# 92. Regras de conflito

Recursos mutáveis usam controle otimista:

```text
Product
Supermarket
ShoppingSession ACTIVE
ShoppingItem
```

Se `baseVersion != server.version`:

```text
409 / CONFLICT
```

O servidor não aplica `last-write-wins` silencioso para dados de negócio.

Após finalização:

```text
ShoppingSession COMPLETED
PriceObservation
```

são imutáveis para o fluxo normal.

# 93. Regras de validação

Produto:

```text
name obrigatório
barcode opcional e textual
measurementType = UNIT | WEIGHT
```

Compra:

```text
supermarketId obrigatório
budgetCents = null ou > 0
apenas uma ACTIVE
```

UNIT + REGULAR:

```text
quantityUnits > 0
unitPriceCents > 0
```

WEIGHT + REGULAR:

```text
weightGrams > 0
pricePerKgCents > 0
```

UNIT + BUNDLE:

```text
quantityUnits > 0
bundleQuantity > 1
bundlePriceCents > 0
quantityUnits % bundleQuantity == 0
```

Compra finalizada não aceita alterações normais.

# 94. Códigos de domínio iniciais

```text
PRODUCT_NOT_FOUND
BARCODE_ALREADY_EXISTS
SUPERMARKET_NOT_FOUND
ACTIVE_SHOPPING_SESSION_ALREADY_EXISTS
SHOPPING_SESSION_NOT_FOUND
SHOPPING_SESSION_NOT_ACTIVE
SHOPPING_SESSION_ALREADY_COMPLETED
SHOPPING_SESSION_ALREADY_CANCELED
SHOPPING_ITEM_NOT_FOUND
INVALID_ITEM_CONFIGURATION
INVALID_QUANTITY
INVALID_WEIGHT
INVALID_PRICE
INVALID_BUNDLE_CONFIGURATION
VERSION_CONFLICT
IDEMPOTENCY_CONFLICT
SYNC_OPERATION_REJECTED
```

# 95. Paginação

Listas usam cursor:

```text
GET /products?limit=20&cursor=abc
```

Response:

```json
{
  "items": [],
  "nextCursor": "def"
}
```

# 96. Segurança básica

Todas as rotas, exceto identidade anônima, exigem:

```text
Authorization: Bearer <token>
```

Toda consulta é filtrada pelo `userId` obtido do token.

O backend nunca confia em `userId` enviado pelo cliente.

Conhecer um UUID não permite acessar recurso de outro usuário.

# 97. Fora da API V1

```text
OAuth Google
OAuth Apple
compartilhamento familiar
preços públicos de outros usuários
crowdsourcing
OCR
NFC-e
ofertas
notificações de preço
gráficos
recomendação de supermercado
IA
```

# 98. Resumo dos endpoints

```text
POST   /api/v1/auth/anonymous

GET    /api/v1/products
GET    /api/v1/products/by-barcode/{barcode}
GET    /api/v1/products/{id}/last-price
POST   /api/v1/products
PATCH  /api/v1/products/{id}

GET    /api/v1/supermarkets
POST   /api/v1/supermarkets
PATCH  /api/v1/supermarkets/{id}

POST   /api/v1/shopping-sessions
GET    /api/v1/shopping-sessions
GET    /api/v1/shopping-sessions/active
GET    /api/v1/shopping-sessions/{id}
PATCH  /api/v1/shopping-sessions/{id}
POST   /api/v1/shopping-sessions/{id}/cancel
POST   /api/v1/shopping-sessions/{id}/complete

POST   /api/v1/shopping-sessions/{id}/items
PUT    /api/v1/shopping-sessions/{id}/items/{itemId}
DELETE /api/v1/shopping-sessions/{id}/items/{itemId}

POST   /api/v1/sync/push
GET    /api/v1/sync/pull
GET    /api/v1/sync/bootstrap
```

# 99. Próxima etapa

Com domínio, banco e API fechados, o próximo passo será detalhar a sincronização operacional:

```text
1. quando uma alteração entra na sync_queue
2. ordem de processamento
3. dependências entre operações
4. retry e backoff
5. comportamento offline/online
6. conflito de versão
7. falha permanente
8. exclusões
9. finalização offline
10. recuperação após app fechado ou travado
```

Depois:

```text
telas/estados finais
↓
estrutura dos repositórios
↓
backlog técnico
↓
implementação
```

---

# 100. Diretrizes de implementação revisáveis

As definições deste documento devem ser respeitadas como **padrão inicial do projeto**, mas podem evoluir.

Antes de fugir de uma decisão registrada, deve existir um motivo concreto, por exemplo:

```text
melhor experiência nativa
menor complexidade
melhor manutenção
melhor desempenho
melhor segurança
limitação descoberta durante implementação
nova capacidade relevante da stack
```

Não devemos manter uma abordagem apenas porque ela foi escrita anteriormente.

Ao mudar uma decisão importante, atualizar este documento junto com o projeto.

## 100.1 Prioridade oficial de frontend

```text
Componentes/APIs Ionic e Angular
              ↓
          Tailwind CSS
              ↓
        CSS/SCSS manual
```

Regras:

- preferir o comportamento nativo fornecido pelo Ionic;
- utilizar Tailwind para composição e utilities;
- evitar recriar componentes Ionic com `div`s e CSS;
- evitar CSS manual quando uma utility ou API oficial resolver;
- usar CSS Variables e `::part` oficiais do Ionic quando forem a maneira correta de customizar um Web Component;
- templates Angular permanecem em arquivos `.html`;
- lógica permanece nos arquivos `.ts`;
- não colocar templates de páginas/componentes diretamente dentro do decorator `@Component`.

## 100.2 Tailwind no aplicativo mobile

Tailwind CSS foi aprovado para o projeto como ferramenta complementar.

O fato de o aplicativo ser mobile não é um problema por si só: Ionic Angular continua sendo uma aplicação Angular baseada em tecnologias web, e o Tailwind possui integração oficial com Angular. Ionic fornece os componentes, gestos e experiência cross-platform; Tailwind entra principalmente como ferramenta de composição visual.

A regra continua sendo:

> **Tailwind não deve substituir os componentes Ionic.**

---

# 101. Sincronização offline — estratégia operacional

A sincronização seguirá um padrão de **Outbox persistente local**.

Regra central:

```text
alteração de negócio
+
operação de sincronização
=
mesma transação SQLite
```

Exemplo:

```text
Adicionar Coca-Cola ao carrinho

BEGIN TRANSACTION

1. INSERT/UPDATE shopping_items
2. INSERT/UPDATE sync_queue

COMMIT
```

Se qualquer etapa falhar:

```text
ROLLBACK
```

Assim nunca existirá uma alteração local importante sem uma operação correspondente para sincronizar.

---

# 102. Princípio local-first

A interface nunca espera o backend para concluir ações principais.

```text
Usuário
  ↓
SQLite
  ↓
UI atualiza imediatamente
  ↓
sync_queue
  ↓
API quando houver conexão
```

O servidor é a persistência remota e a fonte compartilhável futura, mas durante a compra o SQLite é a fonte operacional imediata.

---

# 103. Quando a sincronização é disparada

O app tentará sincronizar quando:

```text
app inicia
app volta para foreground
conexão é recuperada
usuário conclui uma ação importante e existe internet
compra é finalizada com internet disponível
usuário solicita atualização manualmente, se essa opção existir
```

Não depender da execução em background do sistema operacional para garantir consistência.

iOS e Android podem restringir execução em background. Portanto, background sync poderá ser uma otimização futura, nunca a única forma de sincronização.

---

# 104. Sequência local

Cada instalação possuirá um contador monotônico local.

Exemplo:

```text
installation_sequence
1
2
3
4
...
```

Cada entrada da `sync_queue` recebe:

```text
sequence_number
```

Isso preserva a ordem causal produzida naquele dispositivo.

A tabela `sync_queue` passa a considerar:

```text
- id
- sequence_number
- aggregate_type
- aggregate_id
- operation
- payload_json
- status
- attempt_count
- next_attempt_at_ms
- processing_started_at_ms
- last_error
- created_at_ms
- updated_at_ms
```

---

# 105. Dependências entre operações

UUIDs são gerados localmente, mas o servidor ainda precisa respeitar chaves estrangeiras.

Portanto, a sincronização aplica operações por dependência lógica.

Prioridade inicial:

```text
10  Product / Supermarket CREATE
20  ShoppingSession CREATE
30  ShoppingItem CREATE / UPDATE / DELETE
40  Product / Supermarket UPDATE
50  ShoppingSession UPDATE
90  ShoppingSession COMPLETE / CANCEL
```

Exemplo offline:

```text
criar produto
      ↓
criar compra
      ↓
adicionar item
      ↓
finalizar
```

No servidor deve chegar:

```text
Product
   ↓
ShoppingSession
   ↓
ShoppingItem
   ↓
Complete ShoppingSession
```

Mesmo que existam várias operações na fila.

---

# 106. Coalescência da fila

Antes de enviar, operações ainda não sincronizadas poderão ser compactadas quando for seguro.

## CREATE + UPDATE

```text
CREATE produto nome A
UPDATE produto nome B
```

pode virar:

```text
CREATE produto nome B
```

## UPDATE + UPDATE

```text
UPDATE quantidade 1 → 2
UPDATE quantidade 2 → 3
```

pode virar:

```text
UPDATE quantidade → 3
```

## CREATE + DELETE antes de qualquer sync

Se um item foi criado e removido antes de existir no servidor:

```text
CREATE item
DELETE item
```

as duas operações podem ser descartadas.

Isso só é permitido quando nenhuma operação posterior depende daquele registro.

Operações terminais como:

```text
COMPLETE
CANCEL
```

não devem ser descartadas por coalescência comum.

---

# 107. Processamento em lotes

O cliente utilizará:

```text
POST /api/v1/sync/push
```

com pequenos lotes.

Valor inicial sugerido:

```text
até 50 operações por lote
```

Esse valor é revisável.

O lote não significa que todas as operações sejam aplicadas cegamente em uma única transação. Cada operação possui seu resultado idempotente.

---

# 108. Idempotência de sincronização

Toda operação possui:

```text
operationId UUID
```

No servidor existirá um registro técnico semelhante a:

```text
processed_sync_operations
- operation_id UUID
- user_id UUID
- aggregate_type
- aggregate_id
- result_status
- result_version
- processed_at
```

Se a mesma operação chegar novamente:

```text
operationId já conhecido
```

o servidor não executa a mutação outra vez.

Retorna:

```text
ALREADY_APPLIED
```

Isso protege contra:

```text
timeout
internet oscilando
app fechado após enviar
resposta perdida
retry automático
```

---

# 109. Retry e backoff

Erros transitórios entram novamente na fila.

Exemplos:

```text
sem internet
timeout
HTTP 429
HTTP 502
HTTP 503
HTTP 504
```

Backoff inicial sugerido:

```text
2 s
5 s
15 s
1 min
5 min
15 min
30 min
60 min
```

Depois disso, continuar com intervalo máximo controlado.

O contador fica persistido:

```text
attempt_count
```

Portanto fechar o app não zera o retry.

O valor exato desses intervalos poderá ser calibrado durante testes.

---

# 110. Erros permanentes

Erros como:

```text
payload inválido
regra de domínio violada
recurso inexistente quando deveria existir
operação impossível naquele estado
```

não devem ficar em retry infinito.

A operação passa para:

```text
FAILED
```

com:

```text
last_error
```

Falhas técnicas recuperáveis permanecem `PENDING`.

Falhas permanentes devem ser registradas para diagnóstico e, quando impactarem o usuário, apresentadas de maneira compreensível.

---

# 111. Recuperação de operação presa

Uma operação pode ficar como:

```text
SYNCING
```

e o app fechar antes da resposta.

Por isso será salvo:

```text
processing_started_at_ms
```

Ao iniciar o app, entradas `SYNCING` antigas além de uma janela de segurança voltam para:

```text
PENDING
```

Como `operationId` é idempotente, reenviá-las é seguro.

---

# 112. Fluxo push → pull

Um ciclo normal de sincronização será:

```text
1. detectar conexão
2. recuperar operações pendentes
3. ordenar/coalescer
4. PUSH
5. atualizar server_version
6. marcar operações aplicadas
7. PULL alterações remotas
8. aplicar alterações no SQLite
9. atualizar sync_cursor
```

O `pull` vem depois do `push` para reduzir conflitos com mudanças locais ainda pendentes.

---

# 113. Pull e mudanças locais pendentes

Uma mudança recebida do servidor não pode sobrescrever silenciosamente uma alteração local ainda não enviada.

Se existir:

```text
remote change para aggregate X
+
sync_queue pendente para aggregate X
```

o cliente:

```text
1. preserva a intenção local
2. atualiza o estado/versionamento remoto conhecido
3. tenta reconciliar/rebase
4. só depois aplica o estado final
```

Nunca usar `last-write-wins` silencioso como regra geral de negócio.

---

# 114. Estratégia de conflito da V1

Na V1, a identidade anônima estará normalmente vinculada a um único dispositivo.

Por isso conflitos reais deverão ser raros.

## Conflito seguro para rebase automático

Exemplos:

```text
alterar orçamento
alterar quantidade de item
alterar preço de item
editar nome de produto
```

Se o conflito vier da mesma instalação e o estado continuar compatível:

```text
1. buscar versão atual
2. reaplicar a intenção local
3. enviar uma única vez novamente
```

## Estado terminal

Para:

```text
COMPLETED
CANCELED
```

as regras são rígidas.

Se servidor já estiver `COMPLETED` com a mesma operação lógica:

```text
considerar sincronizado
```

Se local diz `COMPLETE` e servidor está `CANCELED`:

```text
CONFLICT
```

Não alterar silenciosamente.

---

# 115. Finalização offline

Finalizar offline continua sendo totalmente suportado.

Transação local:

```text
BEGIN

1. validar ACTIVE
2. calcular subtotais
3. calcular total
4. status = COMPLETED
5. completed_at
6. checkout_total opcional
7. gerar PriceObservation local
8. adicionar operação COMPLETE à sync_queue

COMMIT
```

A operação `COMPLETE` só pode ser enviada depois das operações de produto, supermercado, sessão e itens das quais depende.

Payload de sync poderá incluir:

```text
clientCalculatedTotalCents
itemCount
```

O servidor recalcula o total.

Se o estado remoto não produzir o mesmo resultado:

```text
SYNC_STATE_MISMATCH
```

A finalização não é aplicada até o estado ser reconciliado.

---

# 116. Cenário completo offline

Exemplo:

```text
sem internet
↓
cria supermercado
↓
cria produto A
↓
cria compra
↓
adiciona A
↓
adiciona produto B
↓
altera preço de A
↓
remove B
↓
adiciona produto C
↓
finaliza
↓
fecha o app
```

A fila pode ser compactada para algo equivalente a:

```text
1 CREATE supermarket
2 CREATE product A com estado final
3 CREATE product C
4 CREATE shopping session
5 CREATE item A com preço final
6 CREATE item C
7 COMPLETE shopping session
```

O item B nunca precisa existir no servidor caso tenha sido criado e removido antes de qualquer sincronização e não exista dependência histórica.

---

# 117. Exclusões

## Item de compra ACTIVE

Se nunca foi sincronizado:

```text
CREATE + DELETE
→ remover operações
```

Se já existe no servidor:

```text
DELETE
→ enviar normalmente
```

## Produtos e supermercados

Na V1 não haverá exclusão física de produtos ou supermercados que já tenham histórico associado.

Poderá ser adicionada futuramente uma estratégia:

```text
archived = true
```

em vez de delete.

---

# 118. Sync cursor

O servidor manterá um changelog técnico por usuário.

Exemplo conceitual:

```text
sync_changes
- sequence BIGSERIAL
- user_id UUID
- aggregate_type
- aggregate_id
- operation
- server_version
- payload
- changed_at
```

O cliente guarda apenas um cursor opaco:

```text
sync_cursor
```

Nunca deve depender do significado interno desse cursor.

---

# 119. Bootstrap

Quando não for possível confiar no estado local, o app poderá reconstruí-lo usando:

```text
GET /api/v1/sync/bootstrap
```

Casos:

```text
nova instalação
banco local recriado
migração excepcional
recuperação após inconsistência grave
```

Bootstrap não será o fluxo normal de abertura do aplicativo.

---

# 120. Falha do app no meio da compra

Toda informação essencial permanece no SQLite.

Portanto:

```text
app fecha
celular reinicia
processo é morto
```

não pode apagar:

```text
compra ativa
carrinho
limite
produtos locais
fila de sync
estado de retry
```

Ao reabrir:

```text
1. abrir/migrar SQLite
2. recuperar compra ativa
3. recuperar fila
4. corrigir SYNCING abandonados
5. verificar conectividade
6. sincronizar quando possível
```

---

# 121. Estados de sincronização visíveis ao usuário

A UI não deve mostrar detalhes técnicos da fila.

Estados de alto nível:

```text
Sincronizado
Sincronizando
Offline
Atenção necessária
```

Exemplos:

```text
Offline
Sua compra está salva neste aparelho.
```

```text
Sincronizando...
```

```text
Tudo sincronizado
```

Erros recuperáveis não devem gerar pop-ups repetitivos.

---

# 122. Regras consideradas fechadas para sync

1. alterações de negócio e Outbox são salvas na mesma transação SQLite;
2. a UI é local-first;
3. toda operação possui UUID idempotente;
4. a fila é persistente;
5. ordem causal é preservada por sequência e prioridade;
6. operações podem ser coalescidas quando seguro;
7. retry utiliza backoff persistido;
8. operação presa pode voltar para `PENDING`;
9. push ocorre antes de pull;
10. pull não sobrescreve mudanças locais pendentes;
11. não utilizar `last-write-wins` silencioso como regra geral;
12. finalização offline é suportada;
13. `COMPLETE` depende de todas as mutações anteriores da compra;
14. servidor recalcula o total na finalização;
15. bootstrap é mecanismo de recuperação, não fluxo normal;
16. não depender de background execution para garantir sync.

Essas regras continuam sujeitas ao princípio geral do projeto: são o melhor norte atual, mas podem ser revistas se a implementação revelar uma solução melhor.

---

# 123. Próximo passo

Após a sincronização, faltam antes da implementação:

```text
estados finais das telas
↓
estrutura física dos repositórios
↓
backlog técnico V1
↓
criação dos projetos
↓
implementação
```

A próxima etapa será detalhar cada tela da V1 com:

```text
estado vazio
estado normal
loading
offline
erro
permissões
ações
transições
feedback visual
feedback háptico
```

---

# 124. UX detalhada da V1

A V1 terá uma navegação principal simples e previsível.

```text
Início
Escanear
Carrinho
Histórico
```

A navegação será implementada preferencialmente com componentes nativos do Ionic:

```text
IonTabs
IonTabBar
IonTabButton
IonRouterOutlet
```

As abas continuam visíveis mesmo sem compra ativa.

Se o usuário abrir `Escanear` ou `Carrinho` sem uma compra em andamento, verá um estado vazio com ação para iniciar uma nova compra.

Não esconder ou trocar abas dinamicamente.

---

# 125. Estados globais de interface

Todo o aplicativo deverá reconhecer quatro estados gerais:

```text
ONLINE + SYNCED
ONLINE + SYNCING
OFFLINE
ATTENTION_REQUIRED
```

## ONLINE + SYNCED

Nenhum destaque permanente é necessário.

## ONLINE + SYNCING

Indicador discreto:

```text
Sincronizando...
```

Sem bloquear a interface.

## OFFLINE

Exibir uma indicação discreta:

```text
Offline
Sua compra está salva neste aparelho.
```

A compra continua totalmente utilizável.

## ATTENTION_REQUIRED

Somente para falhas permanentes ou conflitos que exijam intervenção.

Exemplo:

```text
Não foi possível sincronizar uma alteração.
[ Ver detalhes ]
```

Não usar pop-ups repetitivos para erros transitórios.

---

# 126. Tela Início

## 126.1 Sem compra ativa

Conteúdo:

```text
Bom dia, João

Pronto para fazer suas compras?

[ + Nova compra ]

Últimas compras
```

Caso existam compras recentes:

```text
São Luiz
29/09/2026
18 itens
R$ 412,35
```

Componentes preferidos:

```text
IonHeader
IonToolbar
IonContent
IonCard
IonButton
IonList
IonItem
IonSkeletonText
```

## 126.2 Sem histórico

Estado vazio:

```text
Nenhuma compra por aqui ainda.

Quando você finalizar sua primeira compra,
ela aparecerá aqui.

[ Começar primeira compra ]
```

## 126.3 Com compra ativa

A compra ativa ganha prioridade visual.

```text
Compra em andamento

São Luiz

Limite       R$ 500,00
Total        R$ 237,40
Restante     R$ 262,60

████████░░░░░░░ 47%

[ Continuar compra ]
```

Abaixo:

```text
Últimas compras
```

## 126.4 Loading inicial

Usar `IonSkeletonText` e skeleton cards.

Não utilizar spinner ocupando a tela inteira para leitura local.

Como o estado principal vem do SQLite, a tela deve aparecer rapidamente.

## 126.5 Erro local grave

Exemplo:

```text
Não conseguimos abrir seus dados locais.

[ Tentar novamente ]
```

Esse estado é excepcional e deve ser diferente de falha de rede.

---

# 127. Fluxo Nova Compra

A criação de compra será preferencialmente apresentada em `IonModal` no formato sheet/bottom sheet.

## 127.1 Conteúdo

```text
Nova compra

Supermercado
[ Selecionar ]

Limite de gastos
[ R$ ______ ]

[ Começar compra ]
```

O limite é opcional.

## 127.2 Seleção de supermercado

Mostrar primeiro:

```text
Recentes

São Luiz
Assaí
Atacadão

[ + Adicionar supermercado ]
```

Busca disponível quando houver quantidade suficiente de registros.

## 127.3 Novo supermercado

Subfluxo curto:

```text
Nome
[ Supermercado São Luiz ]

[ Salvar ]
```

Após salvar, selecionar automaticamente o supermercado recém-criado.

## 127.4 Validações

Obrigatório:

```text
supermercado
```

Opcional:

```text
limite
```

Se limite informado:

```text
> 0
```

## 127.5 Compra já ativa

Se por alguma razão o fluxo for aberto com compra ativa:

```text
Você já possui uma compra em andamento.

[ Continuar compra ]
[ Cancelar compra atual ]
```

Não permitir criar uma segunda compra ativa.

---

# 128. Tela Scanner

O scanner será uma das telas centrais do produto.

Preferir integração com ML Kit via Capacitor e componentes Ionic para controles e sheets.

## 128.1 Sem compra ativa

```text
Nenhuma compra em andamento

Comece uma compra para escanear produtos.

[ Nova compra ]
```

## 128.2 Permissão ainda não concedida

```text
Use a câmera para ler códigos de barras rapidamente.

[ Permitir câmera ]

ou

[ Adicionar manualmente ]
```

Não solicitar permissão antes do usuário entrar no fluxo que precisa da câmera.

## 128.3 Permissão negada

```text
A câmera está desativada para este app.

Você ainda pode adicionar produtos manualmente.

[ Abrir configurações ]
[ Adicionar manualmente ]
```

Se o sistema permitir nova solicitação:

```text
[ Tentar permitir novamente ]
```

## 128.4 Scanner ativo

```text
←        Escanear             ⚡

Aponte para o código de barras

        [ área de leitura ]

[ Adicionar manualmente ]
```

Controles:

```text
flash
fechar/voltar
adicionar manualmente
```

Galeria não é requisito da V1.

## 128.5 Código detectado

Ao detectar:

```text
1. pausar leitura
2. feedback háptico leve
3. animação curta no retículo
4. resolver produto localmente
5. abrir bottom sheet
```

A leitura não deve continuar disparando enquanto o bottom sheet estiver aberto.

## 128.6 Proteção contra leituras duplicadas acidentais

O scanner entra em estado `LOCKED` após detectar um código.

Só volta a escanear após:

```text
produto adicionado
ou
sheet fechado
```

Ao retomar, deve existir uma pequena janela de proteção contra a mesma imagem ainda estar diante da câmera.

O valor exato será calibrado durante testes reais.

Objetivo:

```text
não transformar 1 leitura em 5 unidades
```

## 128.7 Produto repetido intencionalmente

Depois que o scanner estiver novamente pronto, uma nova leitura real do mesmo produto pode incrementar a quantidade.

Feedback:

```text
✓ Coca-Cola adicionada novamente
Quantidade: 2
```

Haptic leve.

---

# 129. Bottom sheet — Produto conhecido

Preferir `IonModal` com breakpoints.

Conteúdo:

```text
Coca-Cola 2L

7894900011517

Último preço neste mercado
R$ 9,99
há 7 dias

Preço hoje
[ R$ 10,49 ]

Quantidade
[-] 1 [+]

[ Adicionar ao carrinho ]
```

## 129.1 Regras

- preço atual obrigatório;
- quantidade padrão = 1;
- último preço é apenas referência;
- botão principal fica disponível quando o preço atual for válido.

## 129.2 Ao adicionar

```text
1. salvar SQLite
2. salvar sync_queue na mesma transação
3. fechar sheet
4. atualizar badge do carrinho
5. haptic de confirmação
6. scanner volta ao estado pronto
```

Toast curto opcional:

```text
Coca-Cola adicionada
```

Evitar confirmação modal adicional.

---

# 130. Bottom sheet — Produto desconhecido

Conteúdo mínimo:

```text
Produto não encontrado

Código
7891234567890

Nome *
[ __________________ ]

Marca
[ __________________ ]

Descrição / tamanho
[ __________________ ]

Tipo
[ Unidade ▼ ]

Preço *
[ R$ ______ ]

[ Cadastrar e adicionar ]
```

`Nome` e `Preço` são obrigatórios.

Os demais campos podem ser preenchidos futuramente.

Não transformar o primeiro uso de um produto em um formulário longo.

---

# 131. Adicionar item manualmente

Esse fluxo será acessível tanto pelo scanner quanto pelo carrinho.

## 131.1 Primeiro passo

```text
Novo item

Nome
[ Banana Prata ]

Como é vendido?

[ Unidade ]
[ Peso ]
```

Preferir `IonSegment` ou controle equivalente do Ionic.

## 131.2 Unidade

```text
Preço por unidade
[ R$ 4,50 ]

Quantidade
[-] 2 [+]

Subtotal
R$ 9,00
```

## 131.3 Peso

```text
Preço por kg
[ R$ 6,99 ]

Peso
[ 0,824 kg ]

Subtotal
R$ 5,76
```

O usuário digita usando formato amigável.

A camada de apresentação converte:

```text
R$ 6,99 → 699 centavos
0,824 kg → 824 gramas
```

antes de chegar ao domínio.

---

# 132. Promoção BUNDLE na interface

A opção de promoção não deve poluir o fluxo normal.

No formulário do item:

```text
Preço
[ R$ 3,99 ]

[ Este produto está em promoção ]
```

Ao ativar:

```text
Promoção

Leve
[ 3 ] unidades

Por
[ R$ 10,00 ]

Quantidade no carrinho
[ 6 ]
```

Resumo:

```text
3 por R$ 10,00
R$ 3,33 por unidade
Total: R$ 20,00
```

Na V1, a quantidade precisa formar bundles completos.

Caso o usuário queira:

```text
3 promocionais + 1 unidade normal
```

serão duas linhas no carrinho.

---

# 133. Tela Carrinho

## 133.1 Sem compra ativa

```text
Seu carrinho está vazio

Comece uma compra para adicionar produtos.

[ Nova compra ]
```

## 133.2 Compra ativa sem itens

```text
Carrinho vazio

Escaneie seu primeiro produto
ou adicione manualmente.

[ Escanear produto ]
[ Adicionar manualmente ]
```

## 133.3 Carrinho com itens

Topo:

```text
São Luiz

Limite       R$ 500,00
Total        R$ 412,35
Restante      R$ 87,65

████████████████░░ 82%
```

Lista:

```text
Arroz Tio João
1 × R$ 28,90
R$ 28,90

Coca-Cola 2L
2 × R$ 8,99
R$ 17,98
```

Rodapé sticky:

```text
Total
R$ 412,35

[ Finalizar compra ]
```

## 133.4 Interações

Tap no item:

```text
editar
```

`IonItemSliding`:

```text
deslizar ←
Excluir
```

Quantidade:

```text
[-] 2 [+]
```

A alteração é persistida imediatamente.

## 133.5 Exclusão

Após remover:

```text
Coca-Cola removida
[ Desfazer ]
```

Preferir `IonToast` com ação `Desfazer`.

O `DELETE` só é consolidado após a operação local correspondente.

## 133.6 Limite

Estados visuais devem usar mais que apenas cor.

Exemplo:

```text
82% do limite
R$ 87,65 restantes
```

Ao ultrapassar:

```text
Limite ultrapassado em R$ 12,40
```

Nunca bloquear novas adições.

---

# 134. Edição de item

Abrir em sheet/modal.

Campos exibidos dependem da configuração:

```text
UNIT + REGULAR
WEIGHT + REGULAR
UNIT + BUNDLE
```

A interface não exibe campos incompatíveis.

Exemplo UNIT:

```text
Coca-Cola 2L

Preço
R$ 9,99

Quantidade
3

Subtotal
R$ 29,97

[ Salvar ]
```

Exemplo WEIGHT:

```text
Banana Prata

Preço/kg
R$ 6,99

Peso
0,824 kg

Subtotal
R$ 5,76

[ Salvar ]
```

---

# 135. Tela Finalizar Compra

A finalização é uma ação terminal.

## 135.1 Resumo

```text
Finalizar compra

São Luiz
18 itens

Total calculado
R$ 412,35

Limite
R$ 500,00

Restante
R$ 87,65
```

## 135.2 Total do caixa

Campo opcional:

```text
Total do caixa
[ R$ ______ ]
```

Se informado:

```text
Calculado pelo app
R$ 412,35

Total do caixa
R$ 414,10

Diferença
+ R$ 1,75
```

Diferença zero:

```text
✓ Valores conferem
```

Diferença diferente de zero é informativa.

Não bloquear finalização.

## 135.3 Confirmação

Antes da ação terminal:

```text
Finalizar compra?

Depois de finalizada, ela ficará no histórico
e não poderá ser editada normalmente.

[ Voltar ]
[ Finalizar ]
```

Preferir `IonAlert` ou confirmação nativa equivalente.

## 135.4 Finalização offline

Se offline:

```text
Você está offline.

A compra será finalizada neste aparelho
e sincronizada quando a conexão voltar.

[ Finalizar mesmo assim ]
```

Não impedir.

## 135.5 Após finalizar

Feedback:

```text
✓ Compra finalizada

R$ 412,35
18 itens
```

Haptic de sucesso.

Ação:

```text
[ Ver compra ]
[ Voltar ao início ]
```

---

# 136. Tela Histórico

## 136.1 Estado vazio

```text
Nenhuma compra finalizada ainda.

[ Começar uma compra ]
```

## 136.2 Lista

Ordenação:

```text
mais recente primeiro
```

Item:

```text
29 SET

São Luiz
18 itens

R$ 412,35
```

Paginação/carregamento progressivo quando necessário.

## 136.3 Offline

Histórico local continua disponível.

Se existirem compras antigas apenas no servidor e não presentes localmente:

```text
Mostrar o que existe localmente
+
indicador offline discreto
```

Nunca substituir a lista inteira por erro de rede.

---

# 137. Detalhe da compra histórica

Tela somente leitura.

```text
São Luiz
29/09/2026

18 itens

Arroz Tio João
1 × R$ 28,90
R$ 28,90

Coca-Cola 2L
2 × R$ 8,99
R$ 17,98

...

Total calculado
R$ 412,35

Total do caixa
R$ 414,10

Diferença
R$ 1,75
```

Se a compra tiver limite:

```text
Limite
R$ 500,00
```

Nenhum botão de edição na V1.

---

# 138. Loading

Regra geral:

```text
dados locais → skeleton curto
rede → não bloquear dados locais já disponíveis
```

Preferir:

```text
IonSkeletonText
skeleton cards
```

Evitar `IonLoading` cobrindo a tela para operações comuns.

`IonLoading` pode ser usado apenas em ações críticas muito curtas em que duplicar o comando seria perigoso.

---

# 139. Erros

## Erro de rede

Não bloquear tela com erro se o dado local estiver disponível.

Exemplo:

```text
Offline
Mostrando dados salvos neste aparelho.
```

## Erro de validação

Mensagem próxima ao campo.

```text
Informe um preço válido.
```

## Erro inesperado

```text
Não foi possível concluir esta ação.

[ Tentar novamente ]
```

## Erro de sincronização

Não confundir com erro da operação local.

Se o item foi salvo localmente:

```text
item continua no carrinho
```

mesmo se o servidor estiver indisponível.

---

# 140. Feedback háptico

Usar haptics com moderação.

## Leve

```text
código reconhecido
quantidade +1
toggle importante
```

## Médio

```text
produto adicionado
item removido
```

## Sucesso

```text
compra finalizada
```

Não vibrar em toda interação.

Respeitar configurações e disponibilidade do dispositivo.

---

# 141. Animações

Prioridade:

```text
Ionic native transitions
↓
Ionic Animations / Web Animations
↓
CSS somente quando necessário
```

Animações previstas:

```text
sheet sobe ao detectar produto
retículo reage ao scan
badge do carrinho atualiza
progress bar do limite transiciona
total muda suavemente
toast aparece após ação
```

Duração curta e funcional.

Não criar animações que atrasem o próximo scan.

Respeitar:

```text
prefers-reduced-motion
```

quando aplicável.

---

# 142. Acessibilidade e legibilidade

Regras mínimas:

- áreas de toque adequadas;
- labels acessíveis para ícones;
- não depender somente de cor;
- valores monetários legíveis;
- suporte a tamanho de fonte do sistema quando possível;
- contraste adequado;
- foco e teclado corretos nos inputs;
- teclado numérico apropriado para preço, quantidade e peso.

Exemplo:

```text
82% do limite
```

não deve ser comunicado somente por uma barra verde/amarela/vermelha.

---

# 143. Estrutura de rotas sugerida

```text
/tabs/home
/tabs/scan
/tabs/cart
/tabs/history

/history/:shoppingSessionId
/shopping/:shoppingSessionId/complete
```

Fluxos apresentados como modal/sheet não precisam necessariamente virar rotas:

```text
nova compra
produto encontrado
produto manual
editar item
novo supermercado
```

A decisão exata poderá ser revista durante implementação se deep linking ou navegação exigir rotas próprias.

---

# 144. Componentes reutilizáveis previstos

Criar apenas quando houver reutilização clara.

Possíveis componentes:

```text
shopping-summary-card
budget-progress
shopping-history-item
cart-item
money-input
quantity-stepper
weight-input
sync-status-indicator
empty-state
```

Não criar um Design System próprio antes de existir necessidade real.

Preferir composição de componentes Ionic.

---

# 145. Estrutura visual e arquivos Angular

Exemplo:

```text
features/
└── shopping/
    └── presentation/
        ├── pages/
        │   ├── cart/
        │   │   ├── cart.page.ts
        │   │   ├── cart.page.html
        │   │   └── cart.page.spec.ts
        │   └── complete/
        │       ├── complete.page.ts
        │       ├── complete.page.html
        │       └── complete.page.spec.ts
        │
        └── components/
            └── cart-item/
                ├── cart-item.component.ts
                ├── cart-item.component.html
                └── cart-item.component.spec.ts
```

CSS/SCSS só deve existir onde Ionic + Tailwind não forem suficientes.

---

# 146. Critério de UX da V1

O fluxo principal deverá permitir:

```text
escanear
↓
confirmar preço
↓
adicionar
↓
voltar a escanear
```

com o mínimo possível de toques.

Objetivo de produto:

> depois que uma compra estiver iniciada, adicionar o próximo produto deve ser a ação mais rápida do aplicativo.

---

# 147. Decisões UX consideradas fechadas

1. quatro abas fixas: Início, Escanear, Carrinho e Histórico;
2. abas permanecem visíveis sem compra ativa;
3. Nova Compra usa bottom sheet/modal;
4. scanner pausa após uma leitura válida;
5. scanner possui proteção contra duplicação acidental;
6. produto conhecido usa sheet rápido;
7. produto desconhecido usa cadastro mínimo;
8. item manual suporta UNIT e WEIGHT;
9. bundle fica oculto até o usuário indicar promoção;
10. carrinho usa swipe para excluir;
11. exclusão oferece `Desfazer`;
12. limite nunca bloqueia compra;
13. finalização possui confirmação por ser terminal;
14. finalização offline é permitida;
15. histórico da V1 é somente leitura;
16. dados locais nunca são escondidos apenas porque a rede falhou;
17. skeleton é preferido a loading bloqueante;
18. haptics e animações são discretos;
19. acessibilidade não depende apenas de cor;
20. Ionic vem primeiro, Tailwind depois e CSS manual por último.

Todas continuam revisáveis conforme o princípio geral do documento.

---

# 148. Próximo passo

Com UX, domínio, banco, API e sincronização definidos, a próxima etapa será:

```text
estrutura física dos repositórios
↓
backlog técnico da V1
↓
ordem de implementação
↓
criação dos projetos
```

Na estrutura física serão definidos:

```text
nomes dos repositórios
packages Java
módulos Spring
pastas Angular/Ionic
convenções de arquivos
lint/format
testes
branches
CI inicial
```

---

# 149. Security Baseline da V1

Segurança é uma preocupação transversal do projeto e deve existir desde o primeiro commit.

Ela não será tratada como uma etapa final de "hardening".

Referenciais principais:

```text
Mobile
→ OWASP MASVS / MASTG

API
→ OWASP API Security Top 10

Backend/Web
→ práticas OWASP e Spring Security
```

Princípio geral:

```text
Mobile não é ambiente confiável.
Internet não é ambiente confiável.
IDs enviados pelo cliente não provam propriedade.
Dados recebidos do cliente nunca são considerados confiáveis.
```

A estratégia será defesa em profundidade.

---

# 150. Modelo de ameaças inicial

A V1 deve considerar, no mínimo:

```text
roubo/replay de token
acesso a dados de outro usuário (BOLA/IDOR)
broken authentication
mass assignment
SQL Injection
XSS / HTML não confiável
abuso de endpoints
força bruta / automação
DDoS / resource exhaustion
payloads excessivamente grandes
replay de sync
duplicação de operações
manipulação do APK/app
segredos expostos no frontend
logs vazando dados
banco PostgreSQL exposto
dependências vulneráveis
configuração insegura de produção
```

Nem todos possuem a mesma probabilidade ou impacto, mas a arquitetura deve evitar criar vulnerabilidades conhecidas.

---

# 151. Princípio de confiança

O backend é a autoridade final para:

```text
autenticação
autorização
propriedade dos dados
regras de domínio
versionamento
validação
cálculos finais
```

O mobile pode calcular valores para UX, mas o servidor recalcula e valida quando sincroniza.

Exemplo:

```text
mobile envia subtotal
```

não significa que o backend confia nele.

O backend recalcula:

```text
quantidade × preço
peso × preço/kg
bundle
total
```

---

# 152. Segurança do mobile — segredos

Nenhum segredo permanente deve existir dentro do aplicativo.

Não colocar no bundle/APK/IPA:

```text
senha de banco
secret JWT
API secret privilegiada
credencial de infraestrutura
chave privada
token administrativo
```

Qualquer valor presente no aplicativo deve ser considerado potencialmente recuperável por alguém com acesso ao binário.

Configurações públicas, como URL da API, não são segredos.

---

# 153. Tokens no dispositivo

Tokens de autenticação não serão armazenados em:

```text
localStorage
sessionStorage
arquivo texto
variável persistente não protegida
```

Preferir armazenamento seguro da plataforma:

```text
Android Keystore
iOS Keychain
```

acessados por uma abstração Capacitor adequada.

Estrutura conceitual:

```text
SecureTokenStorage
├── getAccessToken()
├── getRefreshToken()
├── saveTokens()
└── clear()
```

O restante da aplicação não conhece diretamente o mecanismo nativo.

---

# 154. Sessões e tokens

Estratégia inicial recomendada:

```text
Access Token
→ vida curta

Refresh Token
→ vida maior
→ armazenamento seguro
→ rotação
```

Regras:

- access token nunca fica válido indefinidamente;
- refresh token deve ser revogável;
- refresh token rotaciona após uso quando a estratégia adotada permitir;
- logout remove credenciais locais e invalida/revoga sessão no servidor quando aplicável;
- tokens nunca aparecem em logs;
- `Authorization` nunca é logado.

Na V1 a identidade poderá ser anônima, mas ainda terá uma sessão autenticada tecnicamente.

---

# 155. Transporte seguro

Toda comunicação remota será:

```text
HTTPS
```

Nunca:

```text
HTTP em produção
```

O cliente deve rejeitar certificados TLS inválidos.

Ambientes de desenvolvimento podem possuir configuração separada, sem enfraquecer a configuração de produção.

Certificate pinning não será requisito inicial obrigatório porque aumenta a complexidade de rotação e operação. Poderá ser reavaliado conforme o risco e o modelo de distribuição.

---

# 156. Segurança do SQLite

O SQLite estará dentro do sandbox privado do aplicativo.

Dados principais:

```text
produtos
supermercados
compras
histórico de preços
fila de sync
```

Tokens de autenticação não pertencem ao SQLite comum.

O banco local não deve armazenar dados secretos desnecessários.

Criptografia completa do SQLite não será obrigatória na primeira implementação porque os dados de compras têm sensibilidade relativamente limitada, mas poderá ser adotada se o perfil de dados crescer ou o modelo de ameaça mudar.

Logs e dumps de desenvolvimento não devem expor desnecessariamente o conteúdo do banco.

---

# 157. Permissões do aplicativo

Princípio:

```text
mínimo privilégio
```

A V1 deverá solicitar apenas permissões realmente necessárias.

Exemplo:

```text
CAMERA
```

somente quando o usuário entrar no scanner.

Não solicitar antecipadamente permissões como:

```text
localização
contatos
microfone
arquivos
```

se a funcionalidade não exigir.

---

# 158. Segurança Angular/Ionic

Angular continuará sendo utilizado de forma que preserve suas proteções padrão.

Regras:

- não interpolar HTML externo arbitrário;
- evitar `[innerHTML]` para conteúdo não confiável;
- não utilizar bypass de sanitização sem justificativa explícita;
- evitar `bypassSecurityTrustHtml`/equivalentes como solução rápida;
- não executar JavaScript recebido da API;
- não construir templates dinamicamente com dados remotos;
- validar URLs externas antes de abrir;
- componentes devem renderizar dados como texto sempre que possível.

Se conteúdo HTML rico vier a existir futuramente, deverá possuir política específica de sanitização.

---

# 159. Content Security Policy

Mesmo sendo empacotado com Capacitor, o conteúdo web deverá ter uma CSP coerente sempre que tecnicamente aplicável.

Objetivo:

```text
limitar scripts
limitar conexões
impedir conteúdo inesperado
reduzir impacto de XSS
```

Não usar configurações amplas como:

```text
script-src *
```

ou equivalentes sem necessidade.

A política final será ajustada quando os hosts reais da API forem definidos.

---

# 160. Spring Security

O backend utilizará Spring Security com estratégia:

```text
deny by default
```

Rotas públicas devem ser uma allowlist explícita.

Exemplo conceitual:

```text
POST /api/v1/auth/anonymous
```

pode ser público.

As demais:

```text
/api/v1/**
```

exigem autenticação, exceto endpoints técnicos explicitamente definidos.

Não utilizar uma blacklist do tipo "tudo público exceto algumas rotas".

---

# 161. Autorização por objeto — BOLA/IDOR

Todo acesso a um recurso identificado por UUID deve validar propriedade.

Errado:

```text
GET /shopping-sessions/{id}

repository.findById(id)
```

Certo conceitualmente:

```text
repository.findByIdAndUserId(id, authenticatedUserId)
```

ou uma política equivalente no application layer.

Aplica-se a:

```text
Product
Supermarket
ShoppingSession
ShoppingItem
PriceObservation
sync
```

Um UUID imprevisível ajuda contra enumeração, mas não substitui autorização.

Nenhum endpoint confiará em:

```json
{
  "userId": "..."
}
```

fornecido pelo cliente como prova de propriedade.

O `userId` é derivado da sessão/token autenticado.

---

# 162. Broken Function Level Authorization

Se surgirem no futuro papéis como:

```text
USER
ADMIN
SUPPORT
```

cada função administrativa terá autorização explícita.

Não basta esconder botões no frontend.

Regra:

```text
Frontend esconde
+
Backend proíbe
```

A segurança real está no backend.

---

# 163. DTOs e Mass Assignment

Entidades JPA não serão recebidas diretamente nos controllers.

Usar:

```text
Request DTO
↓
validação
↓
Use Case
↓
Domain
```

Exemplo:

```text
UpdateProductRequest
```

não pode permitir que o cliente envie silenciosamente:

```text
userId
version do servidor arbitrária
createdAt
admin
owner
```

Campos aceitos são definidos explicitamente pelo DTO.

Respostas também usam DTOs, evitando exposição acidental de campos internos.

---

# 164. Validação de entrada

Todo input externo deve ser validado.

Camadas:

```text
Controller DTO
↓
Bean Validation
↓
Application/Domain validation
↓
Database constraints
```

Exemplos:

```text
nome → tamanho máximo
barcode → formato/tamanho permitido
quantityUnits → > 0
weightGrams → > 0 e limite razoável
priceCents → > 0 e teto razoável
bundleQuantity → faixa válida
query → tamanho limitado
limit → valor máximo
cursor → formato esperado
```

Também limitar:

```text
tamanho do request body
quantidade de operações por sync push
tamanho do payload_json
```

---

# 165. SQL Injection

Regra absoluta:

```text
nunca concatenar input do usuário em SQL
```

Preferir:

```text
Spring Data JPA
JPQL parametrizado
prepared statements
queries nativas com parâmetros
```

Evitar:

```java
"SELECT ... WHERE name = '" + userInput + "'"
```

Ordenação, nomes de coluna e outros elementos que não aceitam bind parameter devem utilizar allowlists definidas pelo código.

O mesmo princípio vale para SQLite no mobile.

---

# 166. Banco PostgreSQL

PostgreSQL não deverá ficar exposto diretamente à internet pública.

Topologia desejada:

```text
Internet
   ↓
Edge / Reverse Proxy
   ↓
Backend
   ↓
rede privada
   ↓
PostgreSQL
```

Regras:

- porta do banco restrita;
- credencial da aplicação com privilégio mínimo;
- senha forte e rotacionável;
- TLS na conexão quando a infraestrutura exigir/comportar;
- backups protegidos;
- nenhum acesso administrativo usando a mesma conta da aplicação;
- migrations podem utilizar credencial separada quando a infraestrutura estiver definida.

---

# 167. Privilégios de banco

A aplicação deve possuir somente permissões necessárias.

Exemplo:

```text
SELECT
INSERT
UPDATE
DELETE
```

nas tabelas da aplicação.

Não conceder desnecessariamente:

```text
SUPERUSER
CREATEDB
CREATEROLE
```

Separar, quando viável:

```text
app_runtime_user
migration_user
admin_user
```

---

# 168. Proteção contra DDoS e Resource Exhaustion

DDoS não é resolvido apenas dentro do Spring Boot.

A proteção deve existir em camadas:

```text
Internet
   ↓
CDN / Edge / WAF
   ↓
Rate Limiting
   ↓
Reverse Proxy
   ↓
Spring Boot
   ↓
PostgreSQL
```

O provedor exato poderá ser decidido no deploy.

Cloudflare ou solução equivalente poderá ser utilizada como camada de edge caso faça sentido para a infraestrutura escolhida.

---

# 169. Rate Limiting

Limites serão aplicados por:

```text
IP
identidade/usuário
endpoint
```

dependendo do fluxo.

Endpoints especialmente protegidos:

```text
/auth/anonymous
/sync/push
/sync/pull
/products search
```

Exemplos de políticas futuras:

```text
burst curto
+
limite sustentado
```

Os números exatos serão definidos com testes e telemetria.

Retorno:

```text
429 TOO MANY REQUESTS
```

com comportamento de retry apropriado.

---

# 170. Limites de recursos

Além de rate limit:

```text
request body máximo
timeout HTTP
timeout de query
pool de conexões limitado
page size máximo
sync batch máximo
tamanho máximo de strings
limite de operações simultâneas
```

Exemplo já definido:

```text
sync push ≤ aproximadamente 50 operações por lote
```

O valor é revisável.

Uma requisição não deve conseguir consumir memória/CPU ilimitadamente.

---

# 171. CORS

CORS não é mecanismo de autenticação.

Mesmo com CORS correto, o backend continua validando token e autorização.

Quando houver frontend web/PWA, usar allowlist explícita de origens.

Evitar em produção:

```text
Access-Control-Allow-Origin: *
```

quando houver credenciais ou superfícies sensíveis.

O app Capacitor nativo também não será usado como justificativa para deixar a política aberta desnecessariamente.

---

# 172. CSRF

Se a API usar autenticação por:

```text
Authorization: Bearer <token>
```

e não utilizar cookies de sessão automáticos, o risco clássico de CSRF é significativamente reduzido.

A configuração Spring Security deve refletir conscientemente esse modelo.

Se no futuro autenticação baseada em cookies for adicionada, a estratégia de CSRF deverá ser revisada e habilitada conforme o fluxo.

Não desabilitar CSRF apenas por hábito sem compreender o modelo de autenticação.

---

# 173. Segurança do sync

A sincronização precisa resistir a replay e manipulação.

Proteções já definidas:

```text
operationId UUID
idempotência
baseVersion
serverVersion
ownership
validação do payload
ordem causal
```

O servidor nunca confia em:

```text
aggregateId
baseVersion
payload
```

sem validar autorização e regras.

Reenviar uma operação já aplicada deve retornar:

```text
ALREADY_APPLIED
```

e não executá-la novamente.

---

# 174. Segurança da finalização

A operação:

```text
COMPLETE shopping session
```

é crítica.

O servidor deve:

```text
validar proprietário
validar status ACTIVE
validar version
recarregar itens
recalcular subtotais
recalcular total
gerar PriceObservation
executar tudo em transação
registrar idempotência
```

Nunca confiar em:

```text
total calculado pelo cliente
itemCount informado pelo cliente
PriceObservation enviada pronta
```

Esses valores podem ser usados para comparação/diagnóstico, não como autoridade.

---

# 175. Logs

Nunca registrar:

```text
access token
refresh token
Authorization header
senha
segredo
credenciais de banco
payloads sensíveis completos
```

Logs devem conter identificadores técnicos suficientes para diagnóstico:

```text
traceId
operationId
aggregateId quando seguro
status
tempo de resposta
erro sanitizado
```

Erros retornados ao cliente não devem conter:

```text
stack trace
SQL
path interno do servidor
credenciais
detalhes de infraestrutura
```

---

# 176. Auditoria de eventos críticos

Registrar eventos de segurança relevantes, por exemplo:

```text
falhas repetidas de autenticação
refresh token inválido
BOLA negado
rate limit acionado
conflito anormal de sync
tentativa de payload inválido repetida
```

Auditoria não significa armazenar conteúdo excessivo do usuário.

Princípio:

```text
logar o necessário para detectar e investigar
sem transformar logs em vazamento de dados
```

---

# 177. Tratamento de erros

Mensagens externas devem ser úteis, mas não revelar internals.

Exemplo:

```json
{
  "code": "SHOPPING_SESSION_NOT_FOUND",
  "message": "Compra não encontrada."
}
```

Evitar mensagens como:

```text
relation shoppings does not exist
SQLSTATE ...
NullPointerException em com.app....
```

Detalhes técnicos ficam restritos aos logs internos.

---

# 178. Segredos do backend

Segredos não entram no Git.

Exemplos:

```text
DATABASE_PASSWORD
JWT_PRIVATE_KEY
API_KEYS
```

serão fornecidos por:

```text
environment variables
secret store da plataforma
```

Arquivos `.env` reais ficam fora do repositório.

Pode existir:

```text
.env.example
```

sem valores secretos.

---

# 179. Dependências e Supply Chain

Frontend e backend utilizarão lock/versionamento controlado.

Frontend:

```text
package-lock.json
```

deve ser versionado.

Backend:

```text
pom.xml
```

deve evitar versões dinâmicas.

CI deverá incluir análise de dependências vulneráveis.

Ferramentas exatas serão definidas com a estrutura do repositório, podendo incluir recursos como:

```text
Dependabot
GitHub dependency review
npm audit como sinal auxiliar
OWASP Dependency-Check ou equivalente
```

Nenhuma ferramenta isolada será considerada prova de segurança.

---

# 180. CI/CD e segurança

Pull requests deverão executar, no mínimo:

```text
lint
unit tests
build
dependency checks relevantes
```

Gradualmente adicionar:

```text
SAST
secret scanning
DAST em ambiente de teste
container scanning se houver container
```

Deploy de produção não deve depender de artefatos construídos manualmente em máquina local.

---

# 181. Configuração de produção

Produção deve possuir perfil separado de desenvolvimento.

Em produção:

```text
debug desabilitado
stack traces externos desabilitados
devtools desabilitados
SQL logging sensível desabilitado
HTTPS obrigatório
actuator restrito
CORS restrito
credenciais externas ao código
```

Endpoints administrativos/Actuator não devem ficar publicamente expostos sem proteção.

---

# 182. Backups e recuperação

Segurança também inclui disponibilidade e recuperação.

PostgreSQL deverá possuir:

```text
backup automático
retenção definida
teste periódico de restauração
```

Backup sem teste de restore não é considerado estratégia suficiente.

A política concreta depende do provedor de infraestrutura que for escolhido.

---

# 183. DDoS — expectativa correta

Não existe configuração dentro do código capaz de garantir proteção absoluta contra DDoS volumétrico.

O objetivo é:

```text
absorver no edge
bloquear tráfego abusivo cedo
limitar custo computacional por request
degradar de forma controlada
proteger banco/backend
```

Ataques volumétricos exigem capacidade do provedor de rede/CDN.

A aplicação complementa isso com rate limits, limites de payload, timeouts e eficiência.

---

# 184. Testes de segurança obrigatórios

Antes da V1 ser considerada pronta, testar:

## Autorização

```text
Usuário A não consegue ler recurso de B
Usuário A não consegue editar recurso de B
Usuário A não consegue deletar recurso de B
```

Mesmo conhecendo o UUID.

## SQL Injection

Testar entradas maliciosas em:

```text
busca
nome de produto
nome de mercado
filtros
sort permitido
```

As queries devem continuar parametrizadas.

## Mass Assignment

Enviar propriedades que não pertencem ao DTO e garantir que não produzam alteração privilegiada.

## Tokens

```text
expirado
inválido
revogado
ausente
```

## Sync

```text
replay operationId
baseVersion antiga
payload adulterado
operação de outro usuário
finalização duplicada
```

## Resource Exhaustion

```text
payload muito grande
page size excessivo
sync batch excessivo
muitas requisições
```

---

# 185. Testes mobile — OWASP MASVS

O mobile será revisado nas áreas:

```text
STORAGE
CRYPTO
AUTH
NETWORK
PLATFORM
CODE
RESILIENCE
PRIVACY
```

Não é necessário buscar um nível de proteção bancária para um app de compras, mas os controles relevantes ao risco do aplicativo serão verificados antes da publicação.

---

# 186. O que não será tratado como defesa real

Não confiar em:

```text
minificação como segurança
UUID sozinho como autorização
CORS como autenticação
campo escondido no frontend
botão desabilitado
validação somente Angular
ofuscação como proteção principal
WAF como substituto de código seguro
```

Todas essas medidas podem complementar segurança, mas não substituem validação e autorização do backend.

---

# 187. Ordem oficial de segurança

Segurança passa a entrar antes da criação dos projetos:

```text
Security Baseline
      ↓
Estrutura física dos repositórios
      ↓
Backlog técnico com tarefas de segurança
      ↓
Criação dos projetos
      ↓
Implementação segura desde o início
      ↓
Testes de segurança
      ↓
Deploy
```

O backlog técnico da V1 deve possuir atividades de segurança distribuídas entre as epics, e não apenas uma tarefa "segurança" no fim.

---

# 188. Checklist mínimo antes de produção

```text
[ ] HTTPS obrigatório
[ ] PostgreSQL não exposto publicamente
[ ] segredos fora do Git
[ ] tokens em armazenamento seguro
[ ] access token com expiração
[ ] autorização por objeto testada
[ ] DTOs explícitos
[ ] queries parametrizadas
[ ] validação server-side
[ ] rate limiting
[ ] limite de payload
[ ] timeouts
[ ] sync idempotente
[ ] logs sem credenciais
[ ] CORS restrito
[ ] Actuator restrito
[ ] dependências auditadas
[ ] backups configurados
[ ] restauração de backup testada
[ ] testes BOLA
[ ] testes de injection
[ ] testes de replay de sync
[ ] build de produção sem debug
```

Assim como o restante do documento, esta baseline é um norte revisável. Controles poderão ser fortalecidos ou simplificados de acordo com o risco real, a infraestrutura escolhida e as descobertas durante desenvolvimento.

---

# 189. Estrutura física dos repositórios

O projeto terá inicialmente dois repositórios:

```text
carrim-mobile
carrim-api
```

São nomes de trabalho. O nome final do produto e dos repositórios deverá ser escolhido antes da criação oficial.

---

# 190. Estrutura do repositório mobile

```text
carrim-mobile/
├── android/
├── ios/
├── src/
│   ├── app/
│   │   ├── core/
│   │   ├── features/
│   │   ├── shared/
│   │   └── theme/
│   │
│   ├── assets/
│   └── environments/
│
├── capacitor.config.ts
├── ionic.config.json
├── angular.json
├── package.json
├── package-lock.json
├── tsconfig.json
└── README.md
```

O frontend **não será Hexagonal**. A organização será feature-first e pragmática.

---

# 191. Mobile — core

`core/` concentra infraestrutura global e recursos usados pela aplicação inteira.

```text
core/
├── auth/
├── config/
├── database/
├── errors/
├── http/
├── security/
├── sync/
└── platform/
    ├── barcode/
    ├── camera/
    ├── haptics/
    ├── network/
    ├── secure-storage/
    └── status-bar/
```

Exemplos:

```text
auth/
→ sessão anônima e tokens

database/
→ SQLite, migrations e transações

http/
→ HttpClient/interceptors

security/
→ abstrações de secure storage

sync/
→ fila e coordenação de sincronização

platform/
→ integrações Capacitor/nativas
```

Páginas não devem chamar plugins nativos diretamente quando existir uma abstração útil em `core/platform`.

---

# 192. Mobile — features

Estrutura inicial:

```text
features/
├── home/
├── shopping/
├── scanner/
├── catalog/
├── supermarkets/
└── history/
```

Não existe uma estrutura interna obrigatória para todas as features.

Uma feature maior pode usar:

```text
shopping/
├── pages/
├── components/
├── stores/
├── services/
└── models/
```

Uma feature simples pode usar apenas:

```text
home/
└── pages/
```

Pastas adicionais como:

```text
data/
repositories/
mappers/
```

só serão criadas quando houver necessidade concreta.

---

# 193. Mobile — shared

```text
shared/
├── components/
├── directives/
├── pipes/
├── validators/
└── utils/
```

Exemplos possíveis:

```text
money-input
quantity-stepper
empty-state
sync-status-indicator
```

Regra:

> se algo pertence claramente a uma feature, fica na feature.

`shared` não deve virar depósito de código genérico.

---

# 194. Mobile — theme e UI

```text
theme/
```

concentra configurações globais de tema realmente necessárias.

A ordem de implementação continua:

```text
1. Ionic / Angular
        ↓
2. Tailwind CSS
        ↓
3. CSS/SCSS manual
```

Tailwind será usado para composição visual; Ionic continuará responsável pelos componentes e padrões de interação mobile.

---

# 195. Componentes Angular

Páginas/componentes reais mantêm `.ts` e `.html` separados.

```text
cart/
├── cart.page.ts
├── cart.page.html
└── cart.page.spec.ts
```

CSS/SCSS só é criado quando necessário:

```text
cart.page.scss
```

Exemplo:

```typescript
@Component({
  selector: 'app-cart',
  templateUrl: './cart.page.html'
})
export class CartPage {}
```

Evitar templates grandes inline no `.ts`.

---

# 196. Dependências no mobile

O frontend não terá uma regra rígida de ports/adapters.

A separação será prática:

```text
pages/components
      ↓
stores/services
      ↓
core/database | core/http | core/platform
```

Modelos de apresentação e regras simples podem ficar dentro da feature.

Regras de negócio importantes devem permanecer testáveis e não ficar escondidas em templates.

---

# 197. SQLite no mobile

Infraestrutura base:

```text
core/database/
├── database.service.ts
├── migrations/
└── transaction/
```

As features podem possuir services/repositories próprios quando necessário, por exemplo:

```text
features/shopping/services/
├── shopping.service.ts
└── shopping.repository.ts
```

Não criar `domain`, `application` ou `data` apenas por convenção.

---

# 198. Arquivos de ambiente mobile

```text
src/environments/
├── environment.ts
└── environment.production.ts
```

Permitido:

```text
API base URL
flags públicas
identificador do ambiente
```

Proibido:

```text
senhas
segredos JWT
chaves privadas
credenciais de banco
tokens administrativos
```

Tudo incluído no bundle mobile deve ser tratado como público.

---

# 199. Estrutura do repositório backend

```text
carrim-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── br/com/<org>/<app>/
│   │   └── resources/
│   │       ├── db/migration/
│   │       ├── application.yml
│   │       └── application-local.yml
│   │
│   └── test/
│       └── java/
│
├── pom.xml
├── .gitignore
└── README.md
```

---

# 200. Backend — uma Hexagonal

Estrutura principal:

```text
br.com.carrim/

├── domain/
├── application/
├── adapter/
│   ├── in/
│   └── out/
├── config/
├── security/
└── shared/
```

Essa é **uma única arquitetura Hexagonal**.

Os contextos aparecem dentro dessas áreas.

---

# 201. Backend — domain

```text
domain/
├── shopping/
│   ├── ShoppingSession.java
│   ├── ShoppingItem.java
│   ├── ShoppingStatus.java
│   └── ...
│
├── catalog/
│   ├── Product.java
│   └── ...
│
├── supermarket/
│   └── Supermarket.java
│
├── pricing/
│   ├── PriceObservation.java
│   ├── Money.java
│   └── ...
│
└── identity/
    └── ...
```

O domínio é Java puro sempre que possível.

Não colocar aqui:

```text
@RestController
@Entity
JpaRepository
HttpServletRequest
PostgreSQL
```

---

# 202. Backend — application

```text
application/
├── shopping/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   └── service/
│
├── catalog/
├── supermarket/
├── pricing/
├── identity/
└── synchronization/
```

Exemplos de portas de entrada:

```text
CreateShoppingSessionUseCase
AddShoppingItemUseCase
CompleteShoppingSessionUseCase
```

Exemplos de portas de saída:

```text
LoadShoppingSessionPort
SaveShoppingSessionPort
LoadProductPort
```

Os services de aplicação implementam/orquestram esses casos de uso.

---

# 203. Backend — adapter IN

```text
adapter/in/
├── web/
│   ├── shopping/
│   │   ├── controller/
│   │   ├── request/
│   │   ├── response/
│   │   └── mapper/
│   │
│   ├── catalog/
│   ├── supermarket/
│   └── identity/
│
└── sync/
    ├── controller/
    ├── request/
    └── response/
```

Controllers:

```text
recebem HTTP
validam DTOs
obtêm usuário autenticado
chamam portas de entrada
mapeiam respostas
```

Não contêm regra de negócio.

---

# 204. Backend — adapter OUT

```text
adapter/out/
├── persistence/
│   ├── shopping/
│   │   ├── entity/
│   │   ├── repository/
│   │   ├── mapper/
│   │   └── ShoppingPersistenceAdapter.java
│   │
│   ├── catalog/
│   ├── supermarket/
│   ├── pricing/
│   └── identity/
│
└── security/
    ├── token/
    └── ...
```

Exemplo de fluxo:

```text
SaveShoppingSessionPort
        ↑
ShoppingPersistenceAdapter
        ↓
SpringDataShoppingRepository
        ↓
PostgreSQL
```

Entidades JPA ficam aqui, separadas das entidades de domínio.

---

# 205. Backend — config, security e shared

## config

Configuração técnica do Spring:

```text
beans
Jackson
CORS
OpenAPI futuro
configurações de infraestrutura
```

## security

Configuração transversal de segurança:

```text
Spring Security
filter chain
authentication context
políticas de autorização
```

Adapters concretos de token podem permanecer em `adapter/out/security`.

## shared

Somente conceitos realmente transversais.

Exemplos possíveis:

```text
erros base
clock abstraction
tipos técnicos compartilhados
```

Evitar `Utils`, `Helper` e serviços genéricos sem responsabilidade clara.

---

# 206. Regra de dependências backend

A direção é:

```text
adapter/in ─────┐
                ▼
           application
                ▼
              domain
                ▲
                │
adapter/out ────┘
```

Mais precisamente:

- `domain` não depende de `application` nem de adapters;
- `application` depende do `domain`;
- adapters dependem das portas/modelos necessários de `application`/`domain`;
- infraestrutura nunca vira dependência do domínio.

---

# 207. Modularidade dentro da Hexagonal

Os contextos:

```text
shopping
catalog
supermarket
pricing
identity
synchronization
```

existem dentro das camadas do hexágono.

Exemplo:

```text
domain/shopping
application/shopping
adapter/in/web/shopping
adapter/out/persistence/shopping
```

Essas quatro áreas pertencem ao **mesmo contexto de shopping**, espalhado pelas partes da Hexagonal.

Isso não significa quatro módulos independentes nem quatro hexágonos.

---

# 208. Comunicação entre contextos backend

Evitar dependência direta em detalhes técnicos de outro contexto.

Por exemplo, `shopping` não deve acessar:

```text
adapter/out/persistence/pricing/...
```

diretamente.

Preferir:

```text
interfaces da application
modelos de domínio adequados
eventos quando realmente úteis
```

Chamadas explícitas são preferidas quando forem mais claras que eventos.

---

# 209. Testes arquiteturais

A preferência inicial será **ArchUnit** para proteger regras como:

```text
domain não depende de Spring
domain não depende de adapter
application não depende de adapter
adapter/out não é acessado diretamente pelo domain
```

Spring Modulith é opcional e poderá ser avaliado depois.

---

# 210. Convenções de nomes — backend

Exemplos:

```text
CreateShoppingSessionUseCase
CreateShoppingSessionService

LoadShoppingSessionPort
SaveShoppingSessionPort

ShoppingController
CreateShoppingSessionRequest
ShoppingSessionResponse

ShoppingJpaEntity
SpringDataShoppingRepository
ShoppingPersistenceAdapter
```

Nomes devem comunicar responsabilidade.

Evitar nomes genéricos como:

```text
Utils
Helper
Manager
CommonService
GeneralService
```

---

# 211. Convenções de nomes — mobile

Exemplos:

```text
cart.page.ts
cart.page.html
product-card.component.ts
shopping-session.store.ts
shopping.service.ts
shopping.repository.ts
barcode-scanner.service.ts
```

Arquivos em:

```text
kebab-case
```

Classes em:

```text
PascalCase
```

Não usar nomes como `domain`, `port` ou `adapter` no frontend apenas para imitar a estrutura backend.

---

# 212. Branch strategy

Para um projeto inicialmente pequeno/individual, não será utilizado `develop`.

Fluxo:

```text
main
  ↑
feature branch curta
```

Tipos:

```text
feat/...
fix/...
refactor/...
chore/...
docs/...
test/...
```

Exemplos:

```text
feat/sqlite-foundation
feat/shopping-session
feat/barcode-scanner
fix/bundle-rounding
docs/security-baseline
```

Branches devem ser curtas e focadas.

---

# 213. Commits

Adotar Conventional Commits.

Exemplos:

```text
feat(shopping): add offline shopping session creation
fix(pricing): correct weight subtotal rounding
refactor(sync): extract retry policy
test(security): cover cross-user access denial
docs(architecture): update repository structure
```

Evitar commits genéricos:

```text
update
changes
fix stuff
```

---

# 214. Pull Requests

Mesmo sendo projeto pessoal, mudanças significativas devem preferir PR quando isso trouxer benefício de:

```text
CI
revisão
histórico
comparação de diff
rollback
```

Mudanças pequenas e puramente documentais podem ser tratadas de forma mais leve.

O processo pode ser ajustado conforme o ritmo real de desenvolvimento.

---

# 215. Formatação e lint — mobile

Configurar:

```text
ESLint
Prettier
Angular compiler checks
TypeScript strict
```

Preferir:

```text
strict = true
```

e manter exceções explícitas e justificadas.

Tailwind segue as convenções do próprio projeto; evitar plugins apenas por estética se não agregarem valor.

---

# 216. Formatação e qualidade — backend

Configurar uma solução de formatação Java consistente.

Requisitos:

```text
build reproduzível
warnings relevantes visíveis
testes no mvn verify
```

Evitar adicionar uma coleção grande de plugins de qualidade antes de existir necessidade.

Checkstyle/Spotless/PMD podem ser avaliados, com preferência por configuração simples e baixo ruído.

---

# 217. Testes mobile

Camadas:

## Unitários

```text
domain
value objects
use cases
stores
mappers
cálculos
```

Framework:

```text
Vitest
```

## Integração local

Testar:

```text
repositories SQLite
migrations
transações
sync queue
```

quando a infraestrutura de teste permitir.

## Dispositivo real

Obrigatório para:

```text
scanner
câmera
haptics
permissões
offline real
background/foreground
```

Mocks não substituem teste no aparelho.

---

# 218. Testes backend

Ferramentas:

```text
JUnit
Mockito
Spring Boot Test
Testcontainers
```

## Unitários

```text
domain
application services
money
rounding
bundle rules
```

## Integração

Com PostgreSQL real via Testcontainers:

```text
repositories
Flyway migrations
constraints
transactions
idempotency
```

## API

Testar:

```text
HTTP contracts
validation
authentication
authorization
error format
```

## Arquitetura

ArchUnit deverá verificar regras como:

```text
domain não depende de infrastructure
módulo shopping não acessa repository interno de pricing
```

---

# 219. Testes de segurança no repositório

O backlog deve incluir testes automatizados para:

```text
cross-user access
token ausente
token inválido
version conflict
replay operationId
payload inválido
limites de paginação
mass assignment
```

SQL Injection é mitigado primariamente por parametrização e design, mas inputs maliciosos também devem entrar nos testes de API.

---

# 220. CI do mobile

GitHub Actions inicial:

```text
checkout
↓
setup Node
↓
npm ci
↓
lint
↓
test
↓
build
```

Executar em:

```text
pull request
push em main
```

Build nativo Android/iOS pode entrar depois, pois possui custo e configuração adicionais.

---

# 221. CI do backend

Pipeline:

```text
checkout
↓
setup Java 25
↓
mvn verify
```

`verify` deverá cobrir:

```text
unit tests
integration tests
architecture tests
```

quando configurados.

Testcontainers utilizará o ambiente Docker do runner.

---

# 222. CI de segurança

Adicionar gradualmente:

```text
dependency review
Dependabot
secret scanning
SAST
```

Não bloquear desenvolvimento inicial com ferramentas excessivamente ruidosas.

Critério:

> ferramenta de segurança deve gerar sinal útil, não apenas volume de alertas.

---

# 223. README de cada repositório

Cada README deve explicar:

```text
objetivo do repositório
stack
pré-requisitos
como executar
como testar
estrutura principal
variáveis de ambiente
regras de segurança relevantes
link para documento mestre
```

Não colocar segredos ou credenciais reais no README.

---

# 224. Configuração backend

Arquivos versionados:

```text
application.yml
application-local.yml
```

Segredos via environment variables.

Exemplo:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Produção não usa senha hardcoded.

---

# 225. Flyway

Estrutura:

```text
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__...
└── ...
```

Regra:

```text
migration aplicada em ambiente compartilhado
→ nunca editar retroativamente
→ criar uma nova migration
```

---

# 226. Migrations SQLite

Estrutura versionada:

```text
src/app/core/database/migrations/
├── 001-initial-schema.ts
├── 002-...
└── ...
```

Toda migration precisa ser:

```text
determinística
versionada
testável
```

Não alterar banco do usuário de forma manual fora do mecanismo de migration.

---

# 227. Definition of Done técnica

Uma tarefa não está concluída apenas porque "funciona na tela".

Definition of Done inicial:

```text
[ ] regra implementada
[ ] código segue arquitetura
[ ] validações necessárias
[ ] segurança considerada
[ ] testes relevantes
[ ] lint/build passando
[ ] estados de erro tratados
[ ] offline considerado quando aplicável
[ ] documentação atualizada quando decisão mudou
```

Para features mobile nativas:

```text
[ ] testado em dispositivo real quando aplicável
```

---

# 228. Ordem de implementação revisada

Após estrutura e segurança, a ordem permanece:

```text
Foundation
↓
UI base
↓
SQLite
↓
Supermercados
↓
ShoppingSession
↓
Limite
↓
Produto manual
↓
Carrinho
↓
Scanner
↓
Peso
↓
BUNDLE
↓
Finalização
↓
Histórico
↓
Teste em supermercado real
↓
Polimento
↓
Backend
↓
Identidade anônima
↓
REST
↓
Sync
↓
Segurança/robustez final
↓
V1.0.0
```

Segurança não aparece somente no fim: tarefas de segurança entram em cada etapa correspondente.

---

# 229. Próxima etapa

Com a estrutura dos repositórios definida, o próximo passo é transformar o backlog macro em **issues/tarefas executáveis**.

Cada atividade deve conter:

```text
objetivo
escopo
fora de escopo
dependências
critérios de aceite
testes
observações de segurança
```

Depois disso:

```text
escolher nome final do produto/repositórios
↓
criar projetos
↓
executar V1.0 — Foundation
```

---

# 230. Backlog executável da V1

O backlog abaixo transforma o planejamento arquitetural em atividades implementáveis.

Princípios:

- tarefas pequenas;
- dependências explícitas;
- critérios de aceite objetivos;
- segurança distribuída ao longo do desenvolvimento;
- primeiro completar o fluxo offline;
- depois backend;
- por último sincronização e robustez final.

---

# 231. Fase 0 — Fundação

## FND-001 — Definir nome final do produto

Objetivo:

Escolher o nome definitivo do aplicativo antes da criação oficial dos repositórios e packages.

Critérios de aceite:

```text
[ ] nome do produto definido
[ ] nome do repositório mobile definido
[ ] nome do repositório backend definido
[ ] package Java definido
[ ] applicationId Android definido
[ ] bundle identifier iOS definido
```

Dependências:

```text
nenhuma
```

---

## FND-002 — Criar repositório mobile

Objetivo:

Criar o projeto Ionic/Angular base.

Stack:

```text
Ionic 9
Angular 22
Capacitor 8
TypeScript 6
Node 24 LTS
Tailwind CSS 4
```

Critérios de aceite:

```text
[ ] projeto inicia localmente
[ ] Android configurado pelo Capacitor
[ ] estrutura core/features/shared/theme criada
[ ] app.ts e app.html separados
[ ] strict mode ativo
[ ] package-lock.json versionado
[ ] sem segredos no repositório
```

---

## FND-003 — Criar repositório backend

Objetivo:

Criar o projeto Spring Boot base.

Stack:

```text
Java 25 LTS
Spring Boot 4.1
Maven
PostgreSQL 18
Flyway
Spring Security
```

Estrutura:

```text
domain/
application/
adapter/in/
adapter/out/
config/
security/
shared/
```

Critérios de aceite:

```text
[ ] projeto executa
[ ] mvn verify passa
[ ] package base definido
[ ] arquitetura física criada
[ ] sem dependência de Spring no domain
[ ] sem credenciais reais versionadas
```

---

## FND-004 — Configurar lint e format do mobile

Critérios:

```text
[ ] ESLint configurado
[ ] Prettier configurado
[ ] scripts npm para lint e format
[ ] CI falha se lint falhar
```

---

## FND-005 — Configurar qualidade do backend

Objetivo:

Definir formatação e validações mínimas sem excesso de ferramentas.

Critérios:

```text
[ ] mvn verify executa testes
[ ] formatação Java consistente
[ ] warnings relevantes visíveis
[ ] configuração simples e documentada
```

---

## FND-006 — Criar CI mobile

Pipeline:

```text
npm ci
npm run lint
npm test
npm run build
```

Critérios:

```text
[ ] roda em pull request
[ ] roda em push para main
[ ] build falha se testes falharem
```

---

## FND-007 — Criar CI backend

Pipeline:

```text
mvn verify
```

Critérios:

```text
[ ] roda em pull request
[ ] roda em push para main
[ ] Testcontainers disponível
```

---

## SEC-001 — Configurar security baseline inicial

Critérios:

```text
[ ] secret scanning habilitado
[ ] Dependabot/dependency review configurado quando disponível
[ ] .env ignorado
[ ] .env.example sem segredos
[ ] documentação de secrets criada
```

---

# 232. Fase 1 — Base visual e navegação

## UI-001 — Configurar Ionic Tabs

Criar:

```text
Início
Escanear
Carrinho
Histórico
```

Critérios:

```text
[ ] 4 tabs fixas
[ ] tabs permanecem visíveis sem compra ativa
[ ] rotas funcionam
[ ] Android/iOS respeitam safe areas
```

---

## UI-002 — Configurar Tailwind

Critérios:

```text
[ ] Tailwind integrado ao Angular
[ ] build funciona
[ ] nenhuma substituição desnecessária de componentes Ionic
[ ] guideline Ionic → Tailwind → CSS documentada no README
```

---

## UI-003 — Tema base

Definir:

```text
cores
tipografia
spacing
surface
dark/light strategy
```

Critérios:

```text
[ ] visual coerente com os mockups
[ ] Ionic CSS variables usadas quando apropriado
[ ] sem folha CSS global excessiva
```

---

## UI-004 — Componentes estruturais compartilhados

Criar apenas:

```text
empty-state
sync-status-indicator
budget-progress
```

se já houver uso concreto.

Critérios:

```text
[ ] sem design system prematuro
[ ] componentes usam Ionic primeiro
```

---

# 233. Fase 2 — SQLite e infraestrutura local

## DB-001 — Integrar SQLite

Critérios:

```text
[ ] banco abre no app
[ ] banco persiste após fechar/reabrir
[ ] Android real testado
[ ] erros de inicialização tratados
```

---

## DB-002 — Criar migration inicial SQLite

Tabelas:

```text
products
supermarkets
shopping_sessions
shopping_items
price_observations
sync_queue
sync_state
app_metadata
```

Critérios:

```text
[ ] PKs/FKs criadas
[ ] índices criados
[ ] constraints principais criadas
[ ] migration idempotente pelo mecanismo escolhido
```

---

## DB-003 — Criar transaction helper

Objetivo:

Garantir:

```text
mudança de negócio
+
sync_queue
=
mesma transação
```

Critérios:

```text
[ ] rollback funciona
[ ] testes cobrem falha no meio da transação
```

---

## DB-004 — Criar abstração de banco

Critérios:

```text
[ ] DatabaseService centraliza lifecycle
[ ] features não manipulam conexão SQLite diretamente
```

---

# 234. Fase 3 — Supermercados

## MARKET-001 — Persistência local de supermercado

Critérios:

```text
[ ] criar
[ ] listar
[ ] editar nome
[ ] pesquisar localmente
```

---

## MARKET-002 — UI de seleção de supermercado

Critérios:

```text
[ ] recentes aparecem primeiro
[ ] novo supermercado pode ser criado
[ ] recém-criado fica selecionado
[ ] estado vazio tratado
```

---

# 235. Fase 4 — Shopping Session

## SHOP-001 — Modelo local de ShoppingSession

Critérios:

```text
[ ] ACTIVE
[ ] COMPLETED
[ ] CANCELED
[ ] startedAt
[ ] finishedAt
[ ] budgetCents
[ ] checkoutTotalCents
```

---

## SHOP-002 — Criar compra

Critérios:

```text
[ ] exige supermercado
[ ] limite opcional
[ ] uma única ACTIVE
[ ] persiste offline
```

---

## SHOP-003 — Recuperar compra ativa

Critérios:

```text
[ ] app reinicia e recupera compra
[ ] home exibe compra ativa
```

---

## SHOP-004 — Cancelar compra

Critérios:

```text
[ ] ACTIVE → CANCELED
[ ] não gera PriceObservation
[ ] não pode ser editada depois
```

---

# 236. Fase 5 — Limite de gastos

## PRICE-001 — Value/conversão de dinheiro

Critérios:

```text
[ ] centavos inteiros
[ ] nenhuma regra usa float/double
[ ] formatter pt-BR
[ ] parser de input testado
```

---

## BUDGET-001 — Budget progress

Critérios:

```text
[ ] mostra total
[ ] mostra restante
[ ] mostra percentual
[ ] estados 80/90/100/>100
[ ] não depende só de cor
```

---

## BUDGET-002 — Alterar/remover limite

Critérios:

```text
[ ] pode alterar durante ACTIVE
[ ] pode remover
[ ] não bloqueia novas adições
```

---

# 237. Fase 6 — Catálogo e produto manual

## CAT-001 — Product local

Critérios:

```text
[ ] UUID local
[ ] barcode opcional
[ ] nome obrigatório
[ ] UNIT/WEIGHT
[ ] barcode salvo como texto
```

---

## CAT-002 — Cadastro manual UNIT

Critérios:

```text
[ ] nome
[ ] preço
[ ] quantidade
[ ] subtotal calculado
```

---

## CAT-003 — Cadastro manual WEIGHT

Critérios:

```text
[ ] preço por kg
[ ] peso em gramas
[ ] subtotal arredondado corretamente
[ ] UI aceita kg amigável
```

---

## CAT-004 — Busca/reuso de produto manual

Critérios:

```text
[ ] pesquisar por nome
[ ] reutilizar produto existente
[ ] não criar duplicata desnecessária
```

---

# 238. Fase 7 — Carrinho

## CART-001 — ShoppingItem REGULAR UNIT

Critérios:

```text
[ ] quantidade
[ ] preço unitário
[ ] subtotal
[ ] snapshot do nome
```

---

## CART-002 — Adicionar item

Critérios:

```text
[ ] persiste localmente
[ ] atualiza total imediatamente
[ ] cria sync_queue na mesma transação
```

---

## CART-003 — Editar item

Critérios:

```text
[ ] alterar quantidade
[ ] alterar preço
[ ] subtotal recalculado
```

---

## CART-004 — Remover item

Critérios:

```text
[ ] swipe com IonItemSliding
[ ] toast Desfazer
[ ] total atualizado
```

---

## CART-005 — Produto repetido

Critérios:

```text
[ ] mesma configuração incrementa quantidade
[ ] não cria linha duplicada
```

---

# 239. Fase 8 — Scanner

## SCAN-001 — Integrar ML Kit

Critérios:

```text
[ ] EAN-13
[ ] EAN-8
[ ] UPC-A/UPC-E quando suportado
[ ] dispositivo Android real
```

---

## SCAN-002 — Permissão de câmera

Critérios:

```text
[ ] pedir apenas ao entrar no fluxo
[ ] denied tratado
[ ] abrir configurações quando necessário
[ ] alternativa manual sempre disponível
```

---

## SCAN-003 — Scanner lock

Critérios:

```text
[ ] pausa após leitura
[ ] não duplica múltiplas leituras do mesmo frame
[ ] retoma após sheet fechar/adicionar
```

---

## SCAN-004 — Produto conhecido

Critérios:

```text
[ ] resolve primeiro pelo SQLite
[ ] mostra último preço local se existir
[ ] usuário confirma preço atual
```

---

## SCAN-005 — Produto desconhecido

Critérios:

```text
[ ] cadastro rápido
[ ] barcode já preenchido
[ ] salva e adiciona em um fluxo
```

---

# 240. Fase 9 — Peso e promoções

## PRICE-002 — ShoppingItem WEIGHT

Critérios:

```text
[ ] weightGrams
[ ] pricePerKgCents
[ ] subtotal correto
[ ] testes de arredondamento
```

---

## PRICE-003 — BUNDLE

Critérios:

```text
[ ] bundleQuantity
[ ] bundlePriceCents
[ ] quantidade múltipla do bundle
[ ] preço equivalente exibido
[ ] subtotal correto
```

---

## PRICE-004 — Combinação promoção + unidade normal

Critérios:

```text
[ ] bundle e regular coexistem em linhas distintas
[ ] total correto
```

---

# 241. Fase 10 — Finalização local

## SHOP-005 — Complete transaction

Em uma única transação:

```text
recalcular itens
calcular total
status COMPLETED
finishedAt
checkoutTotal opcional
PriceObservation
sync_queue COMPLETE
```

Critérios:

```text
[ ] rollback se qualquer etapa falhar
[ ] finalização idempotente localmente
```

---

## PRICE-005 — PriceObservation

Critérios:

```text
[ ] somente em compra COMPLETED
[ ] UNIT normalizado
[ ] KG normalizado
[ ] BUNDLE normalizado
[ ] shoppingItemId UNIQUE
```

---

## SHOP-006 — Total do caixa

Critérios:

```text
[ ] opcional
[ ] diferença calculada
[ ] não altera itens
```

---

## SHOP-007 — Finalização offline UX

Critérios:

```text
[ ] alerta informativo
[ ] não bloqueia
[ ] mostra sucesso local
```

---

# 242. Fase 11 — Histórico local

## HIST-001 — Lista de compras concluídas

Critérios:

```text
[ ] mais recente primeiro
[ ] total
[ ] supermercado
[ ] itemCount
[ ] funciona offline
```

---

## HIST-002 — Detalhe histórico

Critérios:

```text
[ ] somente leitura
[ ] snapshots preservados
[ ] total calculado
[ ] checkoutTotal
[ ] diferença
```

---

# 243. Milestone A — V1 offline utilizável

Nesse ponto deverá ser possível:

```text
abrir app
↓
criar mercado
↓
criar compra
↓
definir limite
↓
adicionar produtos manuais
↓
escanear
↓
usar UNIT / WEIGHT / BUNDLE
↓
editar/remover
↓
finalizar
↓
consultar histórico
```

Sem backend.

Critério principal:

> utilizar o app em uma compra real de supermercado.

---

# 244. Fase 12 — Teste em supermercado real

## TEST-001 — Dogfooding do fluxo

Avaliar:

```text
velocidade do scan
quantidade de toques
entrada de preço
produto repetido
peso
brilho/legibilidade
uso com uma mão
internet ruim
retorno do scanner
```

Registrar problemas como issues.

Não avançar cegamente se o teste real mostrar que o fluxo precisa mudar.

---

# 245. Fase 13 — Polimento

## UX-001 — Haptics

Critérios:

```text
[ ] scan leve
[ ] add confirmação
[ ] complete sucesso
[ ] sem vibração excessiva
```

---

## UX-002 — Animações

Critérios:

```text
[ ] rápidas
[ ] não atrasam scanner
[ ] prefers-reduced-motion considerado
```

---

## UX-003 — Skeletons e empty states

Critérios:

```text
[ ] home
[ ] cart
[ ] history
[ ] scanner sem sessão
```

---

## UX-004 — Acessibilidade

Critérios:

```text
[ ] labels em ícones
[ ] áreas de toque adequadas
[ ] teclado correto
[ ] não depender de cor
[ ] contraste aceitável
```

---

# 246. Fase 14 — Backend Hexagonal Foundation

## ARCH-001 — Criar testes ArchUnit

Regras mínimas:

```text
domain não depende de Spring
domain não depende de adapter
application não depende de adapter
```

---

## ARCH-002 — Money no domínio

Critérios:

```text
[ ] representação exata
[ ] operações testadas
[ ] sem double
```

---

## ARCH-003 — Domínio shopping

Criar:

```text
ShoppingSession
ShoppingItem
ShoppingStatus
PricingType
MeasurementType
```

Critérios:

```text
[ ] regras de status
[ ] subtotal
[ ] total
[ ] bundle
[ ] weight
```

---

## ARCH-004 — Domínio catalog/pricing/supermarket

Criar:

```text
Product
Supermarket
PriceObservation
```

---

# 247. Fase 15 — PostgreSQL e Flyway

## DBAPI-001 — Migration V1

Criar tabelas definidas no modelo.

Critérios:

```text
[ ] migrations passam em PostgreSQL 18
[ ] constraints
[ ] índices
[ ] version fields
[ ] user ownership
```

---

## DBAPI-002 — Persistence adapters

Critérios:

```text
[ ] JPA entities fora do domain
[ ] mappers explícitos
[ ] adapters implementam ports
```

---

## DBAPI-003 — Testcontainers

Critérios:

```text
[ ] repositories testados contra PostgreSQL real
[ ] Flyway executado nos testes
```

---

# 248. Fase 16 — Segurança e identidade

## SEC-002 — Spring Security deny-by-default

Critérios:

```text
[ ] /api/v1/** autenticado por padrão
[ ] allowlist mínima
[ ] testes de 401
```

---

## AUTH-001 — Identidade anônima

Endpoint:

```text
POST /api/v1/auth/anonymous
```

Critérios:

```text
[ ] installationId idempotente
[ ] userId técnico
[ ] tokens emitidos
```

---

## AUTH-002 — Secure Token Storage mobile

Critérios:

```text
[ ] Keystore/Keychain via abstração
[ ] nada em localStorage
[ ] tokens não aparecem em logs
```

---

## SEC-003 — BOLA/IDOR protection

Critérios:

```text
[ ] A não lê dados de B
[ ] A não altera dados de B
[ ] ownership derivado do token
```

---

# 249. Fase 17 — API de domínio

## API-001 — Produtos

Implementar:

```text
GET by barcode
GET search
POST
PATCH
last-price
```

---

## API-002 — Supermercados

Implementar:

```text
GET
POST
PATCH
```

---

## API-003 — Shopping Sessions

Implementar:

```text
POST
GET active
GET by id
PATCH budget
cancel
complete
history
```

---

## API-004 — Shopping Items

Implementar:

```text
POST
PUT
DELETE
```

Critérios gerais:

```text
[ ] DTOs explícitos
[ ] validação
[ ] sem JPA entity em controller
[ ] erros padronizados
```

---

# 250. Fase 18 — Segurança da API

## SEC-004 — Limites de payload/paginação

Critérios:

```text
[ ] page size máximo
[ ] sync batch máximo
[ ] strings com tamanho máximo
[ ] request body limitado
```

---

## SEC-005 — Rate limiting

Aplicar principalmente em:

```text
auth/anonymous
sync
search
```

Critérios:

```text
[ ] 429
[ ] política configurável
```

---

## SEC-006 — SQL Injection tests/design review

Critérios:

```text
[ ] queries parametrizadas
[ ] nenhuma concatenação insegura
[ ] sort/filter por allowlist
```

---

## SEC-007 — Mass assignment tests

Critérios:

```text
[ ] campos desconhecidos não elevam privilégio
[ ] userId nunca controlado pelo payload
```

---

# 251. Fase 19 — Sync local

## SYNC-001 — Sync queue

Critérios:

```text
[ ] persistente
[ ] operationId
[ ] sequence_number
[ ] status
[ ] attempts
```

---

## SYNC-002 — Coalescência

Cobrir:

```text
CREATE + UPDATE
UPDATE + UPDATE
CREATE + DELETE
```

quando seguro.

---

## SYNC-003 — Retry/backoff

Critérios:

```text
[ ] persiste attempts
[ ] sobrevive restart
[ ] erros permanentes viram FAILED
```

---

## SYNC-004 — Recover stuck SYNCING

Critérios:

```text
[ ] stale SYNCING volta PENDING
[ ] operationId impede duplicidade
```

---

# 252. Fase 20 — Sync backend

## SYNC-005 — Idempotency store

Critérios:

```text
[ ] operationId único
[ ] ALREADY_APPLIED
[ ] replay não duplica
```

---

## SYNC-006 — Sync push

Critérios:

```text
[ ] lote
[ ] ownership
[ ] baseVersion
[ ] APPLIED/CONFLICT/REJECTED
```

---

## SYNC-007 — Change log + pull

Critérios:

```text
[ ] cursor opaco
[ ] mudanças ordenadas
[ ] isolamento por usuário
```

---

## SYNC-008 — Bootstrap

Critérios:

```text
[ ] reconstrução do estado básico
[ ] sync cursor devolvido
```

---

# 253. Fase 21 — Conflitos

## SYNC-009 — Version conflict

Critérios:

```text
[ ] 409
[ ] serverVersion retornada
[ ] estado remoto suficiente para reconciliar
```

---

## SYNC-010 — Auto-rebase seguro

Aplicável a casos simples definidos.

Critérios:

```text
[ ] não usa last-write-wins silencioso
[ ] retry limitado
```

---

## SYNC-011 — Terminal conflict

Cobrir:

```text
local COMPLETE
server CANCELED
```

Critérios:

```text
[ ] conflito explícito
[ ] nenhuma alteração silenciosa
```

---

# 254. Milestone B — V1 sincronizada

Critérios:

```text
[ ] fluxo offline completo
[ ] backend seguro
[ ] push
[ ] pull
[ ] bootstrap
[ ] retry
[ ] conflitos básicos
```

Teste obrigatório:

```text
modo avião
↓
criar compra
↓
cadastrar produtos
↓
editar/remover
↓
finalizar
↓
fechar app
↓
religar internet
↓
abrir app
↓
estado sincronizado corretamente
```

---

# 255. Fase 22 — Observabilidade e produção

## OPS-001 — Logs estruturados

Critérios:

```text
[ ] traceId
[ ] operationId quando aplicável
[ ] sem tokens
[ ] sem secrets
```

---

## OPS-002 — Health checks

Critérios:

```text
[ ] health endpoint
[ ] actuator protegido
```

---

## OPS-003 — Config produção

Critérios:

```text
[ ] debug off
[ ] HTTPS
[ ] secrets externos
[ ] SQL sensível não logado
```

---

# 256. Fase 23 — Testes finais

## TEST-002 — Testes de domínio

Cobrir:

```text
money
weight
bundle
status
totals
```

---

## TEST-003 — Testes API

Cobrir:

```text
validation
errors
auth
authorization
idempotency
```

---

## TEST-004 — Testes security

Cobrir:

```text
BOLA
token inválido
token expirado
replay
mass assignment
payload excessivo
rate limiting
```

---

## TEST-005 — Teste de dispositivo real

Cobrir:

```text
scanner
permissões
haptics
offline
restart
foreground/background
```

---

# 257. Fase 24 — Release V1.0.0

Critérios finais:

```text
[ ] fluxo principal estável
[ ] compra real testada
[ ] backend sincronizando
[ ] security checklist concluído
[ ] CI verde
[ ] documentação atualizada
[ ] migrations validadas
[ ] backup definido para produção
[ ] build release Android validado
```

iOS poderá ser incluído no mesmo ciclo ou imediatamente depois, conforme disponibilidade de ambiente Apple.

---

# 258. Ordem prática resumida

```text
FUNDAÇÃO
↓
UI BASE
↓
SQLITE
↓
SUPERMERCADOS
↓
SHOPPING SESSION
↓
LIMITE
↓
PRODUTO MANUAL
↓
CARRINHO
↓
SCANNER
↓
PESO/BUNDLE
↓
FINALIZAÇÃO
↓
HISTÓRICO
↓
TESTE REAL NO SUPERMERCADO
↓
POLIMENTO
↓
BACKEND HEXAGONAL
↓
POSTGRESQL
↓
SEGURANÇA/AUTH
↓
API
↓
SYNC LOCAL
↓
SYNC BACKEND
↓
CONFLITOS
↓
OBSERVABILIDADE
↓
TESTES FINAIS
↓
V1.0.0
```

---

# 259. Identidade oficial do projeto

Nome do produto:

```text
Carrim
```

Repositórios:

```text
carrim-mobile
carrim-api
```

Nome exibido no dispositivo:

```text
Carrim
```

Namespace técnico oficial:

```text
br.com.carrim
```

Usar como base em:

```text
Package Java
Android applicationId
iOS bundle identifier
```

Exemplos:

```text
br.com.carrim.domain.shopping
br.com.carrim.application.shopping
br.com.carrim.adapter.in.web.shopping
```

A escolha poderá ser revista no futuro se houver um motivo real, mas deve ser tratada como padrão oficial do projeto a partir da fundação.

---

# 260. Versionamento do produto

O versionamento real do Carrim seguirá Semantic Versioning:

```text
MAJOR.MINOR.PATCH
```

O desenvolvimento começa em:

```text
0.0.1
```

Não começar diretamente em `1.0.0`.

Estratégia inicial sugerida:

```text
0.0.1  projeto base compilando
0.0.x  fundação e infraestrutura inicial

0.1.0  primeiro fluxo offline utilizável
0.2.0  scanner funcional
0.3.0  peso e BUNDLE consolidados
0.4.0  finalização e histórico local
0.5.0  backend conectado
0.6.0  sincronização funcional
0.7.0  segurança/hardening
0.8.0  testes reais e estabilização
0.9.0  release candidate

1.0.0  primeira versão pública considerada estável
```

Essa sequência é um norte e pode mudar conforme o desenvolvimento real.

Durante `0.x.x`, mudanças relevantes de arquitetura, contratos e comportamento ainda podem acontecer.

---

# 261. IDs de backlog não são versões

Os identificadores das tarefas devem ser independentes do versionamento do aplicativo.

Preferir códigos por área:

```text
FND-001   Fundação
UI-001    Interface
DB-001    Banco local
SHOP-001  Compras
CAT-001   Catálogo
SCAN-001  Scanner
PRICE-001 Preços
SEC-001   Segurança
API-001   API
SYNC-001  Sincronização
TEST-001  Testes
REL-001   Release
```

Exemplos:

```text
FND-001  Criar projeto mobile
FND-002  Criar projeto backend

DB-001   Integrar SQLite
DB-002   Criar migration inicial

SHOP-001 Criar ShoppingSession
SHOP-002 Recuperar compra ativa

SCAN-001 Integrar ML Kit

SEC-001  Configurar Spring Security
SEC-002  Testar BOLA/IDOR

SYNC-001 Criar sync queue
```

Assim:

```text
SHOP-003
```

é uma tarefa, enquanto:

```text
0.3.0
```

é uma versão do Carrim.

Não misturar os dois conceitos.

---

# 262. Próximo passo

Com o nome e o namespace definidos, já podemos iniciar oficialmente a fundação:

```text
Produto:
Carrim

Mobile:
carrim-mobile

Backend:
carrim-api

Namespace:
br.com.carrim

Primeira versão técnica:
0.0.1
```

A implementação começa pela fundação dos dois projetos, CI, lint/format, estrutura inicial e security baseline.

Antes de criar qualquer repositório remoto, fazer commit, push ou deploy, confirmar explicitamente essa ação no fluxo de desenvolvimento.
