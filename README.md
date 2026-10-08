# Carrim API

API do Carrim para acompanhar compras de supermercado. Este repositório e `JoaoVFAraujo/carrim-mobile` formam o mesmo produto.

## Estado: 0.0.8 — APIs de negócio protegidas

Aplicação Spring Boot com PostgreSQL, identidade anônima e APIs protegidas de catálogo/compras. A execução exige banco e credenciais locais. Integração HTTP, armazenamento seguro e sincronização no mobile continuam pendentes.

## Stack e pré-requisitos

- JDK 25 LTS, selecionado por `JAVA_HOME`.
- Spring Boot 4.1.1, Spring MVC, Spring Security e Bean Validation.
- Maven Wrapper; não é necessário instalar Maven globalmente.
- JUnit e suporte de testes Spring MVC/Security.
- PostgreSQL 18, JPA/Hibernate e Flyway. Testes usam uma instância isolada, via Testcontainers ou o script Windows abaixo.

## Executar e validar

```bash
./mvnw verify
./mvnw spring-boot:run
```

No Windows, com Docker ativo para os testes:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

A porta padrão é 8082 (`http://localhost:8082`). Qualquer rota é negada. GET sem autenticação retorna 401; uma identidade autenticada de teste também é negada, com 403. Requisições de mutação sem CSRF podem retornar 403 antes da autorização.

No IntelliJ, recarregue o projeto Maven e mantenha na configuração de execução:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/carrim
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<senha configurada somente no ambiente local>
```

O banco `carrim` deve existir; a aplicação não cria banco ou usuário PostgreSQL. Ao iniciar, Flyway cria o schema `carrim` dentro desse banco e aplica as migrations. Hibernate apenas valida o schema (`ddl-auto=validate`), sem criar/apagar tabelas automaticamente; Flyway clean está desabilitado. As variáveis do IntelliJ não se propagam automaticamente ao terminal, e arquivos `.env` não são carregados automaticamente.

Sem Docker ativo, este Windows pode executar os mesmos testes com os binários PostgreSQL 18 instalados:

```powershell
.\scripts\verify-local-postgres.ps1
```

O script cria um cluster exclusivo em `target/postgres-tests`, escuta somente em loopback numa porta livre e usa o banco `carrim_test`/usuário de teste. Para essa instância temporária usa autenticação trust; encerra o processo ao terminar e mantém arquivos/logs ignorados pelo Git para diagnóstico. Não usa a porta 5432, credenciais administrativas ou bancos existentes. Paths de PostgreSQL/JDK podem ser informados por `-PostgresBin` e `-JavaHome`. O caminho alternativo Testcontainers requer Docker ativo e cria PostgreSQL 18 descartável. Nenhum teste usa automaticamente a conexão configurada no IntelliJ.

```bash
./mvnw package
java -jar target/carrim-api-0.0.8.jar
```

Os testes verificam inicialização com migrations, regras do domínio, persistência/isolamento/versionamento e bloqueio de acesso HTTP.

## Arquitetura

Namespace: `br.com.carrim`. Monólito modular com **uma única Hexagonal**, organizada por camadas:

- `domain`: Java puro, sem Spring, JPA ou HTTP.
- `application`: casos de uso e portas; não depende de adapters.
- `adapter/in`: controllers e DTOs explícitos.
- `adapter/out`: persistência e integrações concretas.
- `config`, `security`, `shared`: apenas responsabilidades transversais reais.

A aplicação contém `security/SecurityConfig`, `domain/shared/Money`, domínio de compras/catálogo e casos de uso transacionais. As demais áreas serão criadas quando houver implementação; não existem entidades vazias ou casos de uso fictícios. ArchUnit verifica as dependências de produção entre as camadas durante os testes.

JPA entities e mapeamento explícito do catálogo ficam em `adapter/out/persistence`; o adapter implementa `application/catalog/CatalogRepository`. O agregado de compra usa um adapter JDBC com SQL explícito e uma transação por comando, compartilhando o datasource e o gerenciador de transação Spring. Domínio, casos de uso e portas permanecem independentes de Spring/JPA. Flyway V1 cria catálogo/propriedade; V2 cria compras, itens e histórico.

## Segurança e configuração

- Apenas POST `/api/v1/auth/anonymous` é público. Identidade, catálogo e compras exigem Bearer emitido pela aplicação; outras rotas permanecem negadas. Propriedade é derivada do token, nunca de userId no JSON.
- Sem login por formulário, HTTP Basic, usuário padrão ou senha gerada.
- Sem sessão HTTP ou autenticação por cookies. CSRF desabilitado somente na cadeia `/api/v1/**`, que usa Bearer/prova JSON; outras rotas preservam CSRF e permanecem fechadas.
- Erros não expõem stack traces, mensagens internas ou binding errors.
- Nenhum segredo hardcoded; arquivos `.env` reais são ignorados.
- Não há CORS amplo ou Actuator público.
- Credenciais de banco vêm do ambiente local; nenhuma senha está no repositório.

O runtime Cloud pode exigir proxy e truststore na execução do Maven. Essas configurações pertencem ao ambiente e não devem ser adicionadas ao projeto. Verificação TLS deve permanecer habilitada.

Formatação Java: quatro espaços, LF e newline final, definidos em `.editorconfig`; o código atual segue essa convenção.

## Referência e próximas etapas

[Documento-base v12](docs/carrim-documento-base-v12.md): produto, domínio, arquitetura, segurança, UX e backlog. É uma referência revisável, não um contrato imutável.

A experiência offline do mobile será validada antes de avançar para persistência, APIs de domínio e sincronização. CI, commit, push, PR e deploy não fazem parte desta fundação local.

## Validação local — 30/09/2026

Fundação transferida para `C:\workspace\backend\mercado`, na branch `main`. Java instalado: 21.0.10; Maven global: 3.9.12. Esta API exige JDK 25.

O Maven Wrapper recebeu um ajuste para não indexar uma propriedade `Target` nula em diretórios comuns no Windows PowerShell. O wrapper alcança o download do Maven, mas a conexão remota falha no ambiente de execução. A verificação offline também não consegue resolver o parent Spring Boot 4.1.1 ausente no cache. `verify`, os seis testes e a inicialização do JAR permanecem pendentes no Windows; nenhum resultado do ambiente Linux foi tratado como validação local.

Nenhuma ferramenta global foi instalada ou substituída. Para manter os caches dentro da pasta do projeto durante a validação nesta sessão:

```powershell
$env:MAVEN_USER_HOME = "$PWD\target\maven-user-home"
.\mvnw.cmd "-Dmaven.repo.local=$PWD\target\maven-repository" verify
```

## Formatação Java com Spotless

Spotless usa Palantir Java Format com indentação de quatro espaços, LF e newline final. A verificação de fontes Java de produção e testes faz parte de `verify` e não altera arquivos automaticamente.

No IntelliJ, execute os goals pelo painel Maven usando o JDK 25:

```text
spotless:apply
spotless:check
verify
```

A execução pelo assistente ficou bloqueada por acesso negado ao arquivo `conf/security/java.security` do JDK local. A aplicação da formatação e a validação com Spotless ainda precisam ser executadas no IntelliJ.

## Validação atualizada — 03/10/2026

`mvnw.cmd verify` passou no Windows usando o JDK 25.0.4.1 já instalado, selecionado somente para o processo de validação. Os seis testes passaram, o JAR foi empacotado e o Spotless confirmou a formatação. Não houve alteração de configuração global nem conexão com banco. Esta verificação resolve as pendências de `verify` registradas acima; execução manual do JAR continua sem validação específica.

## Valores no domínio — 07/10/2026

`Money` representa BRL em centavos inteiros com `long`, soma, subtração, multiplicação e comparação. Saldos e diferenças podem ser negativos; overflow provoca erro em vez de alterar silenciosamente o valor. Não existe conversão por ponto flutuante.

`ItemSubtotal` calcula unidade, peso e promoção com as regras atuais do mobile. Preço de referência: 1 a 100000000 centavos; quantidade: 1 a 9999; peso: 1 a 9999999 gramas. Peso arredonda meio centavo para cima por linha. Promoção exige grupos completos de pelo menos duas unidades e multiplica o preço do grupo, sem arredondar um preço unitário intermediário. Ex.: 824 g a R$ 6,99/kg = R$ 5,76; seis unidades em promoção 3 por R$ 10,00 = R$ 20,00.

Essas regras são Java puro e ainda não estão expostas por HTTP. A verificação do backend já em execução em `http://localhost:8082` confirmou 401 para `/`, `/api/v1/products` e `/actuator/health`, conforme a segurança fechada. O frontend está disponível em `http://localhost:4202`; comunicação entre os aplicativos dependerá das futuras APIs, identidade e persistência. Próxima entrega do domínio: itens e sessão de compra, com estados e total.

Validação: `mvnw.cmd spotless:apply verify` passou com Java 25; 32 testes passaram (26 de domínio e seis da fundação), Spotless confirmou a formatação e o JAR foi empacotado. Os vetores monetários reproduzem os exemplos e limites atuais do mobile, incluindo subtotal zero para pesos pequenos e soma de subtotais já arredondados.

## Itens e sessões de compra — 0.0.2 — 07/10/2026

`ShoppingItem` preserva ID do cliente, vínculo da sessão, produto opcional para linhas manuais e nome snapshot. Rejeita campos incompatíveis entre unidade, peso e bundle; subtotal sempre é derivado do preço, sem aceitar total informado pelo cliente.

`ShoppingSession` é um agregado imutável: adicionar, substituir, remover item e alterar limite retornam nova sessão ACTIVE. Total soma os subtotais arredondados; saldo pode ficar negativo sem impedir finalização. Finalizar exige ao menos um item e data não anterior ao início. Total do caixa é opcional, aceita zero e produz diferença assinada. Cancelamento pode ocorrer sem itens. COMPLETED e CANCELED recusam todas as alterações; listas não podem ser modificadas externamente.

Linhas preservam a identidade já atribuída no dispositivo. IDs repetidos ou itens de outra sessão são recusados; linhas com IDs distintos não são agrupadas automaticamente pelo servidor. Idempotência de requisição, uma compra ativa por proprietário, geração de observações de preço e transações serão responsabilidades dos próximos casos de uso e adapters. Esta versão ainda não expõe APIs de negócio nem conecta banco.

Validação: 48 testes passaram (42 de domínio e seis da fundação), Spotless e empacotamento com Java 25. Artefato: `target/carrim-api-0.0.2.jar`. A versão identifica a evolução da fundação do backend; o frontend tem ciclo independente e continua em 0.0.1 nesta entrega.

A revisão removeu o teto de preço unitário aplicado ao total do caixa: o domínio aceita centavos não negativos representáveis por Money, inclusive quando a soma de itens ultrapassa aquele teto. Teste confirma total calculado de 200000000 centavos com diferença zero. O formulário mobile atual ainda limita o caixa a 100000000 centavos; esse limite deverá ser alinhado ao contrato HTTP e à persistência quando forem implementados.

## Catálogo e observações de preço — 0.0.3 — 07/10/2026

`Product` e `Supermarket` são valores imutáveis com UUID fornecido pelo cliente e nomes de 1 a 120 caracteres. Produto aceita código opcional, armazenado como texto, com 8, 12 ou 13 dígitos ASCII, preservando zeros iniciais. Não valida dígito verificador nem unifica formatos equivalentes nesta etapa, como no mobile. Renomear preserva identidade sem alterar snapshots históricos.

`PriceObservation.fromCompletedSession` deriva uma observação imutável por linha de uma sessão COMPLETED. Sessões ACTIVE e CANCELED são recusadas. A observação mantém sessão, mercado, data de finalização e o item completo, inclusive produto opcional, nome snapshot, peso e preço original. O ID da linha é reutilizado como ID da observação para derivação estável; gravação transacional e idempotência ainda dependerão da persistência.

Preço por kg usa base KG; regular e bundle usam UNIT. Equivalência de bundle arredonda HALF_UP para centavos com BigDecimal, sem ponto flutuante binário, e identifica aproximação quando a divisão não é exata. Pode arredondar para zero em grupos de preço muito baixo; o preço exato do grupo continua preservado para totalizar a compra. Essas observações não são uma sugestão automática de preço de hoje.

Validação: 69 testes passaram, Spotless e verify com Java 25. JAR: `target/carrim-api-0.0.3.jar`. Frontend sem alterações nesta entrega. Não há APIs de negócio, banco ou sincronização. Próxima etapa: persistência PostgreSQL local e casos de uso; autenticação/propriedade precedem exposição das APIs.

## Persistência inicial — 0.0.4 — 07/10/2026

A migration V1 cria `users`, `products` e `supermarkets` no schema `carrim`. `users` representa proprietários técnicos da aplicação, não usuários de login do PostgreSQL; ainda não existe endpoint para criar identidades. Produtos e supermercados exigem proprietário existente, usam UUIDs do cliente e versionamento otimista. Códigos são únicos por proprietário; produtos manuais aceitam código nulo e mercados podem ter nomes iguais para filiais.

`JpaCatalogRepository` implementa a porta de catálogo com transações, consultas por proprietário/ID, mapeamento explícito e `@Version`. Escritas com versão antiga ou concorrência não sobrescrevem silenciosamente os dados. IDs existentes não são reutilizados por outro proprietário. A interface ainda não está exposta por HTTP.

Validação: 77 testes passaram em PostgreSQL 18.2 isolado, incluindo migrations, reaplicação sem perder dados, round-trip, constraints, propriedade e concorrência; Spotless/verify e JAR 0.0.4 passaram. Docker estava parado, portanto a execução usou o script Windows; o caminho Testcontainers está implementado, mas não foi executado nesta sessão.

A instância de teste foi encerrada. O banco `carrim` criado pelo usuário em localhost:5432 não foi conectado pelo assistente: as credenciais estão na execução do IntelliJ. Para aplicar ali, recarregue Maven e reinicie o backend com essas variáveis. O log do Flyway deve indicar schema `carrim` na versão 1. No pgAdmin, atualize `Databases → carrim → Schemas → carrim → Tables`. Próxima migration: compras, itens e observações; depois casos de uso e identidade/API.

## Compras e histórico — 0.0.5 — 07/10/2026

A V2 adiciona `shopping_sessions`, `shopping_items` e `price_observations`, mantendo a V1 intacta. FKs compostas preservam propriedade de mercados/produtos e índice parcial admite só uma compra ACTIVE por proprietário. O preço de referência acompanha a configuração UNIT/WEIGHT/BUNDLE do domínio; subtotal é coluna gerada em centavos. Campos incompatíveis são recusados.

`ShoppingOperations` oferece início, adição/edição/remoção, limite, finalização e cancelamento por meio da porta `ShoppingRepository`. O adapter bloqueia a linha do agregado antes de validar a versão e executar a alteração, grava nova versão e mantém todas as escritas na mesma transação. Edições substituem as linhas do agregado ACTIVE, preservando seus IDs e ordem. Consultas usam snapshot REPEATABLE_READ para ler sessão e itens consistentemente.

Finalização muda o estado e insere uma observação por item na mesma transação. O banco deriva data, mercado, base e equivalência do preço a partir da compra/item. Um trigger diferido exige histórico completo antes do commit; falha reverte estado, versão e observações. Registros e itens de compras encerradas são imutáveis, inclusive por SQL direto. Valores normalizados são gravados no histórico; nomes e preços originais vêm das linhas congeladas. Cancelamento não gera observações. Repetições com versão antiga são recusadas sem duplicar o histórico; idempotência HTTP por chave continua para a etapa de APIs/sync.

Validação: 87 testes passaram em PostgreSQL 18.2 isolado, Spotless/verify e JAR 0.0.5. Incluem upgrade V1→V2 preservando catálogo, round-trip misto, propriedade, exclusividade ACTIVE, edição, histórico imutável, checkout opcional/alto, rollback após falha no segundo registro e finalização concorrente. O banco do usuário não foi migrado pelo assistente; ao reiniciar esta versão com as variáveis do IntelliJ, Flyway aplicará V2. Sem endpoints novos ou mudanças no frontend.

## Verificações de arquitetura — 0.0.6 — 07/10/2026

ArchUnit 1.5.0 está limitado ao escopo de teste. Quatro regras verificam o bytecode de produção: domínio depende apenas de Java/domínio; aplicação depende apenas de Java/aplicação/domínio; entidades JPA ficam no adapter de persistência; pacotes principais não formam ciclos. Classes de teste são excluídas da importação. Referência: [guia oficial do ArchUnit](https://www.archunit.org/userguide/html/000_Index.html).

Validação final: 91 testes passaram, Spotless/verify e JAR 0.0.6 com Java 25. Schema permanece na V2; esta entrega não modifica runtime ou frontend além do número de versão. Para atualizar o banco local, recarregue Maven e reinicie com a configuração existente; o log do Flyway deverá indicar V2. Próximas entregas: identidade anônima segura, APIs e sincronização do mobile. A integração HTTP ainda não está implementada.

## Identidade anônima — 0.0.7 — 07/10/2026

V3 adiciona instalações anônimas. O cliente cria UUID e prova aleatória de 32 bytes, codificada em base64url canônico sem padding (43 caracteres). Envia instalação, prova, plataforma e versão a POST `/api/v1/auth/anonymous`. O servidor cria proprietário ANONYMOUS e token opaco aleatório de 256 bits, válido por 30 dias. Repetir com a mesma prova mantém proprietário e renova token, revogando o anterior; UUID isolado não permite recuperar conta. Criação/renovação concorrente é serializada e transacional.

Provas e tokens são persistidos somente como hashes SHA-256; comparação da prova usa MessageDigest.isEqual. GET `/api/v1/auth/me` verifica token e expiração via introspector local do Spring Security Resource Server. Não há introspecção remota, JWT/segredo de assinatura ou senha de aplicação. Objetos de credenciais têm toString redigido e erros não retornam valores rejeitados. Bootstrap deve ser enviado sem Bearer antigo; prova permite renovação após expiração. O mobile ainda precisa implementar armazenamento seguro da prova/token, sem localStorage, e integração HTTP.

Validação: 99 testes passaram em PostgreSQL 18.2 isolado, Spotless/verify e JAR. Incluem bootstrap HTTP, prova inválida, renovação/revogação, expiração, concorrência, hashes em banco, principal Bearer e rejeição de cookie. Contrato: [API local](docs/API-LOCAL.md). Progresso: [status de desenvolvimento](docs/STATUS-DESENVOLVIMENTO.md).

## APIs de negócio — 0.0.8 — 08/10/2026

Rotas protegidas oferecem criação/listagem/consulta/edição de produtos e mercados; lookup por barcode com último preço regular no mesmo mercado/medida; início, consulta/listagem/compra ativa, limite, itens, cancelamento, finalização e histórico de preços. DTOs explícitos usam centavos/gramas inteiros. Frações não são truncadas para inteiros no JSON. Coleções têm paginação limitada; lista de compras retorna resumos sem carregar todos os itens. A API admite até 1000 linhas por compra e checkout até o inteiro seguro de JavaScript, sem usar esse teto no domínio.

Mutações usam versão da compra ou do cadastro. Finalização exige Idempotency-Key UUID; V4 persiste recibo/hash do pedido na mesma transação da compra/histórico. Replay com mesmo proprietário/chave/pedido devolve compra congelada; chave com outro pedido dá 409. Falha ao gravar recibo reverte compra e preços. Timestamps de início/finalização/cancelamento são truncados para microssegundos, conforme precisão PostgreSQL. O bootstrap tem limite local de 60 chamadas por endereço de conexão/janela de 60s, com no máximo 4096 endereços em memória; ignora X-Forwarded-For não confiável. Escala distribuída e proteção de borda ficam para hospedagem.

Validação: 113 testes passaram com Java 25/PostgreSQL 18.2 isolado, Spotless/verify e JAR 0.0.8. Incluem fluxo HTTP misto, BOLA/IDOR, versão antiga, revogação/expiração, preço por mercado/configuração, concorrência/replay, rollback de recibo, centavos fracionários, limite de bootstrap e HTTP real em porta loopback isolada. Banco local do usuário não foi alterado pelo assistente; recarregar Maven/reiniciar aplica migrations pendentes até V4. Frontend continua 0.0.1, sem chamadas remotas; próxima etapa será armazenamento seguro e cliente HTTP antes do sync.

A revisão colocou o limite de 1000 linhas no agregado, validado dentro da mutação após o bloqueio da sessão. O construtor também rejeita estados maiores que esse limite. A regressão PostgreSQL usa alterações concorrentes com versão atual e seguinte prevista, confirmando que a segunda não adiciona o item 1001 nem incrementa a versão.
