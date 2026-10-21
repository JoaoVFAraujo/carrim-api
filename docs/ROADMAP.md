# Estado, métricas e próximos passos

Atualizado em **21/10/2026**. Fonte única de retomada: atualizar estado/prioridades aqui, sem novos documentos por sessão.

## Onde paramos

Base do Cloud: API 8515b48 e mobile 700bcce, consolidada com as retomadas 08f0f52/915a113. As entregas do PC incluem layout (#21–#26), integração web (#27), governança (#28), extração do Repository (#29) e agendamento adaptativo (#30). Versões técnicas permanecem 0.0.8/0.0.2. A entrega de fotos em validação acrescenta PostgreSQL V7 e SQLite 011; sem release/deploy.

| Área           | Estado real                                                                                                                                    |
| -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Compra offline | Mercado, edição/remoção do limite, cancelamento confirmado, itens, peso/promoção, total, finalização e Histórico                               |
| Catálogo       | Busca local por nome/código, preço anterior regular por mercado/data; preço de hoje obrigatório                                                |
| Scanner        | MLKit integrado; câmera/permissões físicas pendentes                                                                                           |
| Backend        | Domínio, ArchUnit, APIs protegidas, PostgreSQL/Flyway V1–V7, versões e finalização idempotente                                                 |
| Identidade     | Keystore/Keychain no nativo, prova HttpOnly na web e vínculo SQLite/dono; divergência pausa sync                                               |
| Sync           | Push idempotente, journal/pull, reservas/retry, pedido congelado e agendamento adaptativo; SQLite V1–V11                                       |
| Conflitos      | Revisão assistida consulta estado atual, compara e permite reaplicação explícita em cadastros/compra ativa; casos complexos continuam pausados |
| Android        | APK anterior instalado/aberto no Moto G73 em 20/10; APK de fotos e validação física completa pendentes                                              |
| Produção       | Perfil, TLS verificado no banco, health privado, logs JSON, imagem sem root e CI preparados; sem deploy público                                |
| Layout         | Quatro abas recompostas; fotos contidas no scanner/Carrinho em revisão local; aceite do usuário e aparelho pendentes             |

API **0.0.8**, mobile **0.0.2**, Android versionCode **2** são versões dos manifests, não release V1.0.0. Web e nativo sincronizam compras persistidas. Web usa proxy na mesma origem e prova HttpOnly; nativo continua exigindo endpoint HTTPS.

## Evidências e métricas técnicas

Validação da entrega de fotos no PC em 21/10/2026, nas branches `feature/fotos-produtos-aprovacao`: backend com 164 testes sem falhas/erros/skips e frontend com 244 testes em 35 arquivos, integração HTTP/PostgreSQL/SQLite habilitada. Spotless/ArchUnit/backend build, lint/formatação/SQLite/build mobile passaram. Os registros anteriores abaixo conservam seu contexto histórico; não equivalem a validação física ou produção. PRs/revisão/CI, merge e APK desta entrega ainda estão pendentes neste registro.

| Verificação                           | Resultado registrado                                                                | Limite                                                                                  |
| ------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Backend verify + PostgreSQL isolado   | 164 testes, sem falhas/erros/skips em 21/10                       | PostgreSQL descartável; sem banco pessoal ou produção                  |
| Mobile com CARRIM_LIVE_SYNC=1         | 244 testes, 35 arquivos, sem skips em 21/10                                                  | Plugins/ciclo de vida nativos substituídos                                              |
| Integração real local                 | API/PostgreSQL/SQLite; reabertura, resposta perdida, replay, push/pull e Repository | API/cluster em portas temporárias de loopback; CI mobile padrão pula os dois casos live |
| Qualidade mobile                      | Lint, formatação, SQLite real e build passaram                                      | Aviso de bundle inicial ~1,20 MB permanece                                              |
| Android assembleDebug                 | Passou no Cloud/CI, incluindo PR #29                                                | Não é release assinada nem execução física                                              |
| Produção descartável (registro Cloud) | UID 10001, PostgreSQL TLS de teste, readiness UP/DOWN, health privado               | Não comprova domínio/CA/backup/segredos reais                                           |

Sem CARRIM_LIVE_SYNC=1, live é explicitamente pulado. A entrega de fotos ainda aguarda PRs e revisão/CI do último head; os PRs históricos abaixo não aprovam essa branch. Testes nativos usam adaptadores de plugins e não substituem aparelho.

| Entrega integrada                              | Evidência                                                                                                                                                                                                                                              |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Backend push/pull/produção                     | [#11](https://github.com/JoaoVFAraujo/carrim-api/pull/11), [#12](https://github.com/JoaoVFAraujo/carrim-api/pull/12), [#13](https://github.com/JoaoVFAraujo/carrim-api/pull/13)                                                                        |
| Mobile identidade/pull/integração/configuração | [#13](https://github.com/JoaoVFAraujo/carrim-mobile/pull/13), [#14](https://github.com/JoaoVFAraujo/carrim-mobile/pull/14), [#15](https://github.com/JoaoVFAraujo/carrim-mobile/pull/15), [#16](https://github.com/JoaoVFAraujo/carrim-mobile/pull/16) |
| CI das entregas finais                         | [Backend](https://github.com/JoaoVFAraujo/carrim-api/actions/runs/38487350275), [Mobile](https://github.com/JoaoVFAraujo/carrim-mobile/actions/runs/38487529655)                                                                                       |

Ao repetir, atualizar evidência com data, ambiente, commit e resultado; preservar histórico no Git. Contagem de testes não é cobertura de todos os riscos.

## Entregas do PC — 17–18/10/2026

- [Mobile #18](https://github.com/JoaoVFAraujo/carrim-mobile/pull/18): cancelar com confirmação e definir/editar/remover limite, com SQLite/fila atômicos e testes de rollback/concorrência.
- [Mobile #19](https://github.com/JoaoVFAraujo/carrim-mobile/pull/19): comparar alteração local/servidor, arquivar a revisão e reaplicar somente após confirmação, com novo comando explícito. Mudança local durante o diálogo invalida a decisão. UPDATE de catálogo/limite e alterações de itens exigem dados ativos e íntegros; encerramento, cadastro conflitante e importação inválida seguem pausados.
- [Mobile #20](https://github.com/JoaoVFAraujo/carrim-mobile/pull/20): revisão local conforme LAYOUT, com resumo compacto, nomes/totais longos, Histórico, fechamento de formulários e foco. Fluxo misto R$ 54,66, caixa R$ 55,00 e diferença R$ 0,34, persistidos após reload. Não foi gerado APK local.

### Correção da avaliação visual — 18/10/2026

O usuário apontou que a captura da revisão anterior não reproduzia a referência. Os testes funcionais continuam válidos, mas não representam aprovação de fidelidade. [Mobile #21](https://github.com/JoaoVFAraujo/carrim-mobile/pull/21) refaz a composição do Carrinho (resumo compacto, cartões com área de miniatura, menus e controles agrupados, ações abaixo da lista). Comparação em 320/390 px com total R$ 112,36; 125 testes locais passaram, 1 live pulado. A sequência [mobile #22](https://github.com/JoaoVFAraujo/carrim-mobile/pull/22) recompõe Início, Escanear e Histórico: cesta própria sem marcas, tipografia/hierarquia, filtro em cápsula, cartões expansíveis e ficha inferior de produto. Comparação interativa em 320/390 px, navegação e itens do Histórico conferidos; 127 testes passaram, 1 live pulado, lint/formatação/SQLite/build passaram. Aviso do bundle inicial (~1,14 MB) permanece; estilos por componente abaixo de 4 kB. Fotos do catálogo e conferência visual do usuário continuam pendentes. Câmera real, teclado e acessibilidade nativa dependem de aparelho; sem APK local novo ou deploy.

## Integração completa web e nativo — 19/10/2026

- Sincronização web habilitada com prova HttpOnly/SameSite=Strict, __Host-/Secure no HTTPS, Bearer em memória, conferência do proprietário e bloqueio de perda/troca de identidade. Os contratos de negócio continuam protegidos por Bearer, sem autenticação por cookie.
- Cada alteração grava SQLite/fila e desperta envio; retorno da conexão e verificação periódica recuperam pendências. Recebimento usa journal e preserva alterações locais. Web Locks impede duas abas gravando cópias concorrentes do banco web.
- Validação da integração inicial no PC: backend verify com 134 testes passou em PostgreSQL isolado; frontend com integração HTTP habilitada passou 144 testes, incluindo web e nativo, sem skips. As verificações normais pulam explicitamente os dois casos live. Lint, formatação, SQLite real e build passam; permanece aviso do bundle inicial (~1,16 MB).
- Integração confere dados criados offline antes da primeira conexão, reabertura, resposta perdida/replay, renovação, produtos/mercados, edição/remoção de orçamento, itens, cancelamento, compra mista finalizada e preços nos dois bancos. Compra criada pela API chega completa ao SQLite/Histórico sem ecoar fila.
- Revisão interativa em cópia isolada: compra de R$ 39,98, caixa R$ 40,00, PostgreSQL com compra COMPLETED, um item, uma observação de preço e um proprietário após reload; console sem erros. API descartável desligada/reiniciada: outra compra salva durante a falha chegou ao PostgreSQL sem perda; segunda aba foi bloqueada com orientação. Não usa o banco pessoal carrim.
- Execução reproduzível: script existente do backend com -MobileSync inicia API/cluster temporários em loopback e encerra somente esses processos. Não houve deploy ou teste físico Android. Em produção web, configurar proxy na mesma origem e HTTPS confiável antes de publicar.

## Organização e consultas de sincronização — 19/10/2026

- [Mobile #29](https://github.com/JoaoVFAraujo/carrim-mobile/pull/29): ShoppingRepository concentra SQL/transações com fila; ShoppingSessionStore conserva signals, validações e serialização das ações. Mesmos contratos e migrations, sem mudança de layout; 147 testes passaram com integração real habilitada.
- [Mobile #30](https://github.com/JoaoVFAraujo/carrim-mobile/pull/30): SyncSchedulerService troca o ciclo fixo de 2 s por consultas ociosas de 15/30/60 s. Escrita local e retorno ao app/conexão despertam envio; retries e leases mantêm prazos persistidos. Sem rede ou em segundo plano não inicia novas consultas; requisição em andamento pode terminar. FAILED/conflitos continuam pausados para revisão.
- Validação local: 176 testes passaram com HTTP/PostgreSQL/SQLite isolados nos modos web/nativo, sem skips; lint, formatação, SQLite real e build aprovados. No navegador foram medidos pulls ociosos de 15,047 s e 30,034 s e primeiro push de nova compra 82 ms após o clique, com compra confirmada no PostgreSQL. O timer de 60 s, pausa/retomada, eventos nativos, backoff, concorrência, limpeza de listeners e liberação de reservas ainda não enviadas foram verificados nos testes automatizados. A verificação de primeiro plano é repetida após preparar SQLite/token e antes de despachar; o pedido congelado é preservado quando o envio fica adiado. O navegador de inspeção manteve documentos visíveis mesmo ao abrir outra aba; essa tentativa não comprova pausa real por ocultamento.
- Backend não teve código ou migrations alterados nesta atividade; sua última validação completa registrada é de 134 testes. Os testes integrados usam API/cluster descartáveis; banco pessoal preservado. Execução física Android, aprovação visual e produção continuam pendentes. Aviso do bundle inicial (~1,16 MB) permanece.

## Próximas atividades

As duas primeiras etapas aprovadas (organizar services e reduzir consultas) estão implementadas nos PRs acima. A sequência restante é:

| Prioridade | Entrega                                | Critério                                                                                                                                                                                                                                                                                          |
| ---------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1          | Fluxo completo e aprovação do layout   | Conferir compra, itens, orçamento, finalização e Histórico; indisponibilidade/reconexão/pendências; comparar as quatro telas com as referências, definir fotos do catálogo e obter aprovação antes do APK.                                                                                        |
| 2          | APK de teste e aparelho (TEST-001/005) | Após aprovação visual: validar câmera, teclado/leitor de tela, Keystore, SQLite, offline/online, pausa/retomada nativa e atualização preservando dados.                                                                                                                                           |
| 3          | Homologação segura e preparação da V1  | Resolver bloqueios/conflitos complexos de compras encerradas, colisões e importações; validar HTTPS/CORS WebView, banco/CA/segredos, backup/restauração, gateway e alertas. Release V1.0.0 depende de aprovação, acessibilidade, ajuste do bundle, assinatura e distribuição após testes físicos. |

A revisão anterior foi funcional; a fidelidade visual foi reprovada. A correção e a conferência visual devem vir antes da preparação do APK. Aprovação no aparelho também permanece pendente. iOS depende de macOS/Xcode. Não limpar dados, substituir proprietário, alterar pedido enviado ou reativar FAILED automaticamente. Reaplicação assistida é uma nova decisão confirmada e arquivada, não reciclagem automática de uma chave idempotente.

## Métricas de produto a validar

Sem painel, coleta ou metas numéricas aprovadas. Começar por observação com dados de teste; telemetria exige finalidade, minimização e retenção conforme Segurança.

| Pergunta                    | Medida                                                             | Uso                                                   |
| --------------------------- | ------------------------------------------------------------------ | ----------------------------------------------------- |
| Adicionar item é rápido?    | Tempo/passos para manual, código conhecido e desconhecido          | Reduzir atrito, inclusive offline                     |
| Total ajuda antes do caixa? | Diferença caixa − app e causas observadas                          | Corrigir fluxo, sem ajustar histórico automaticamente |
| Compra sobrevive a falhas?  | Reabertura/atualização sem perda, rollback e replay sem duplicação | Confiabilidade                                        |
| Câmera ajuda?               | Sucesso de leitura e alternativa manual por aparelho               | Melhorar fluxo sem guardar imagens do usuário         |
| Sync termina?               | Pendências, idade da fila, retries e conflitos                     | Diagnóstico/reconciliação                             |
| App é acessível?            | Conclusão com fonte ampliada, teclado e tecnologia assistiva       | Aceitação da UI                                       |

Operação futura: latência/erros, readiness, conexões do banco e restaurações testadas. SLOs após carga real; não inventar disponibilidade ou economia.

## Futuro e ideias

| Horizonte                | Ideias preservadas                                                                                                                                                                            |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V2 — estou pagando caro? | Histórico de preços com data/mercado e base UNIT/KG; menor/médio/último; comparação de mercados/compras; gráficos; gastos semanais/mensais/por mercado; frequência; favoritos e lista simples |
| Identidade/recuperação   | Conta opcional Google/Apple, vínculo anônimo, backup recuperável e múltiplos dispositivos; conflitos/privacidade antes de habilitar                                                           |
| V3 — onde devo comprar?  | Estimar lista antes de sair, comparar cesta inteira, dividir mercados e estimar economia; orçamento mensal, categorias, análise e alertas                                                     |
| Depois do núcleo         | OCR, NFC-e/notas fiscais, crowdsourcing, ofertas públicas, integrações oficiais, compartilhamento familiar e análises avançadas                                                               |
| Técnicas condicionais    | Coalescência segura, índices de busca conforme volume, CSP/telemetria mínima e análise contínua de dependências                                                                               |

Ideias não são entregas nem compromissos de prazo. Promoções complexas (leve/pague, cartão, cashback, cupons, progressivos), GPS e IA ficam fora do núcleo V1. Rever prioridades com uso real, sem criar arquitetura/documento por possibilidade.

## Catálogo automático — 20/10/2026

Etapas 1–3 implementadas nas branches feature/catalogo-automatico: avaliação inicial do Open Food Facts, consulta protegida no backend e ficha do scanner com sugestão/atribuição/foto opcional e cadastro manual. Sem migrations, mudança de versão ou deploy. Revisão e CI de cada head devem ser conferidos nos respectivos PRs antes da integração.

Amostra de conveniência consultada em 20/10: 7891000100103 (Leite Condensado Moça), 7894900011517 (Coca-Cola 2 L) e 3017620422003 (Nutella) encontrados com imagem; 7896001122333 (código ilustrativo) e 7891515431146 não encontrados. Retornos de aproximadamente 0,67–1,08 s. Essa amostra selecionada não mede cobertura do Brasil nem substitui códigos reais/regionais do usuário. Fonte escolhida para homologação; release depende de licença/capacidade/privacidade conforme Segurança.

Validação no PC: backend 147 testes passaram com PostgreSQL isolado, Spotless/ArchUnit/build; frontend 204 testes passaram com integração HTTP/PostgreSQL/SQLite real habilitada, sem skips, incluindo consulta pessoal autenticada nos modos web e nativo sem gravar catálogo/fila. Lint, formatação, SQLite e build passaram; permanece aviso do bundle inicial (~1,16 MB), sem novo aviso de CSS. Revisão independente encontrou troca de compra durante await e guarda de produto por peso após recebimento pelo sync; ambos corrigidos com testes e revisão final sem novos apontamentos. Navegador em origem isolada consultou fornecedor real, exibiu foto/atribuição/preço vazio e não criou produtos/itens antes da confirmação; após salvar, PostgreSQL recebeu uma unidade por 899 centavos e a segunda leitura usou SQLite sem nova consulta online. Captura sem overflow horizontal na largura DOM efetiva de 300 px; não equivale a aprovação visual do usuário ou teste Android. Permanecem etapas 4–5: revisão completa de fluxo/layout com usuário, fotos offline e distribuição de APK atualizado, além de API HTTPS acessível ao aparelho. APK debug anterior instalado no Moto G73 em 20/10 não contém esta funcionalidade e está sem API nativa configurada; instalação/abertura/SQLite foram conferidos, não equivalem a aceitação física completa.
## Fotos persistentes e aprovação — 21/10/2026

Implementadas nas branches `feature/fotos-produtos-aprovacao` dos dois repositórios, ainda sem PR/merge ou APK desta entrega neste registro. Sem incremento dos manifests e sem deploy. Migrações PostgreSQL V7 e SQLite 011 preservam compras, identidade e filas existentes nos testes isolados.

- Foto enviada pelo usuário vira candidatura pública PENDING: autor e administrador podem vê-la; outros clientes autenticados recebem somente APPROVED. Uma substituição pendente conserva a imagem pública aprovada anterior. Aprovação exige candidato ainda vinculado e versão atual; rejeição/retirada fecha novas leituras públicas e registra auditoria. Cópia já baixada offline só conhece a retirada ao reconectar.
- Câmera/galeria pelo plugin oficial, arquivo no navegador, prévia, substituição, remoção e restauração explícita do catálogo. Item manual sem barcode usa seu identificador real. Retorno de câmera após encerramento do processo vira rascunho local com validade de 24 horas e exige confirmação. Fotos não alteram valores ou itens de compras encerradas.
- SQLite guarda JPEG limitado em base64 por compatibilidade dos drivers; PostgreSQL guarda BYTEA. Fila de mídia separada conserva pedidos congelados/idempotência e espera o produto/item, sem bloquear compras. Há retry com espera, recuperação de resposta perdida, remoção/tombstone, cache limitado e proteção contra ressurreição por replay.
- Servidor valida JPEG/PNG reais, tamanho/dimensões e proprietário; reprocessa pixels para JPEG, descartando EXIF. Entrada até 500 KiB, 4096 pixels por lado/16 megapixels; saída até 1024 pixels/500 KiB. Cotas: 100 fotos com bytes, 1000 registros e 200 preferências por proprietário; 20 escritas/min. Importação externa tem orçamento próprio de 10/min e duas operações concorrentes, destino HTTPS fixo, timeout e corpo limitados, sem redirecionamentos/credenciais ou URL arbitrária do cliente.
- Painel mínimo `/admin/fotos`: filtros pendente/aprovada/rejeitada, ampliação e decisão versionada. Chave aleatória de operador único separada da identidade anônima; backend recebe apenas hash pelo ambiente, frontend mantém chave só em memória e limpa ao sair. Sem configuração, acesso fechado. HTTPS em produção; HTTP apenas em loopback fora de prod. Contratos e controles completos em API/Segurança.

Evidência atual: 164 testes backend e 244 frontend em 35 arquivos com HTTP/PostgreSQL/SQLite isolados nos modos web e nativo; qualidade/builds aprovados e `npm audit` sem vulnerabilidades conhecidas na execução registrada. A revisão independente corrigiu corrida na troca de compra/foto durante gravação, importação externa sem orçamento de bytes, bloqueio indevido do administrador após falhas, aprovação de candidato substituído, preferência oculta recuperando foto externa e recuperação de resposta perdida. Contagens/audit não garantem ausência de outros defeitos. O bundle inicial (~1,20 MB) continua com aviso.

Conferência visual local: login administrativo, ampliação, aprovação e saída foram exercitados na interface. PostgreSQL registrou a foto própria APPROVED (6077 bytes, versão 1) e auditoria APPROVE da versão 0; a importação Open Food Facts também chegou como EXTERNAL (3726 bytes). Carrinho exibiu ambas as fotos contidas, sem sobreposição, e a garrafa alta ficou dentro da miniatura na ficha. Houve falha temporária de conexão Java com `images.openfoodfacts.org`, recuperada após reinício da API isolada; o download real foi então confirmado. Durante a falha, a compra continuou disponível com placeholder. TLS, validação de certificado e limites foram preservados. Isso comprova o fluxo local exercitado, não disponibilidade contínua do fornecedor, aceite do usuário ou execução física Android. A correção adicional de recuperação de imagem pública retirada foi validada na rodada completa de 244 testes e na revisão independente, sem apontamentos pendentes nessa revisão. Outra identidade descartável consultou o código e recebeu CARRIM_APPROVED com own:false e foto APPROVED, sem vínculo privado. Prévias alta/larga/quadrada foram conferidas; cancelar preservou a foto anterior. Após reload, ambas as imagens do Carrinho carregaram com 88 pixels e object-fit:contain, sem overflow na largura efetiva de 353 pixels.

Próxima sequência: abrir PRs para main com comentário exato `@codex review`, corrigir achados e aguardar revisão/CI do último head; integrar por merge commit; gerar APK atualizado e conferir câmera/galeria/offline/atualização no aparelho. O APK segue sem endpoint nativo HTTPS configurado: não afirmar sincronização física online antes dessa configuração. Antes de produção, validar backup/restauração com fotos, política de conteúdo/retirada/privacidade/retenção, HTTPS e gestão da chave. Não usar banco pessoal, apagar dados ou publicar por consequência dos testes.