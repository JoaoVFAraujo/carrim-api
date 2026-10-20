# Estado, métricas e próximos passos

Atualizado em **19/10/2026**. Fonte única de retomada: atualizar estado/prioridades aqui, sem novos documentos por sessão.

## Onde paramos

Base do Cloud: API 8515b48 e mobile 700bcce, consolidada com as retomadas 08f0f52/915a113. As entregas do PC incluem layout (#21–#26), integração web (#27), governança (#28), extração do Repository (#29) e agendamento adaptativo (#30). Versões técnicas permanecem 0.0.8/0.0.2; não houve release ou alteração de schema nesta sequência.

| Área           | Estado real                                                                                                                                    |
| -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Compra offline | Mercado, edição/remoção do limite, cancelamento confirmado, itens, peso/promoção, total, finalização e Histórico                               |
| Catálogo       | Busca local por nome/código, preço anterior regular por mercado/data; preço de hoje obrigatório                                                |
| Scanner        | MLKit integrado; câmera/permissões físicas pendentes                                                                                           |
| Backend        | Domínio, ArchUnit, APIs protegidas, PostgreSQL/Flyway V1–V6, versões e finalização idempotente                                                 |
| Identidade     | Keystore/Keychain no nativo, prova HttpOnly na web e vínculo SQLite/dono; divergência pausa sync                                               |
| Sync           | Push idempotente, journal/pull, reservas/retry, pedido congelado e agendamento adaptativo; SQLite V1–V10                                       |
| Conflitos      | Revisão assistida consulta estado atual, compara e permite reaplicação explícita em cadastros/compra ativa; casos complexos continuam pausados |
| Android        | APK debug compilado no Cloud/CI; aparelho, Keystore/SQLite nativos e atualização física pendentes                                              |
| Produção       | Perfil, TLS verificado no banco, health privado, logs JSON, imagem sem root e CI preparados; sem deploy público                                |
| Layout         | Após reprovação, quatro abas recompostas nos mobile #21/#22; placeholders no catálogo, conferência do usuário e aparelho pendentes             |

API **0.0.8**, mobile **0.0.2**, Android versionCode **2** são versões dos manifests, não release V1.0.0. Web e nativo sincronizam compras persistidas. Web usa proxy na mesma origem e prova HttpOnly; nativo continua exigindo endpoint HTTPS.

## Evidências e métricas técnicas

Validação mobile repetida no PC em 19/10/2026: 166 testes passaram com integração real habilitada, além de lint, formatação, SQLite e build. A última validação completa do backend registrou 134 testes; nesta atividade não houve mudança de código Java, e a API foi empacotada para os testes isolados sem repetir sua suíte. Registros anteriores do Cloud permanecem no Git e nas seções históricas; não equivalem a validação física ou produção.

| Verificação                           | Resultado registrado                                                                | Limite                                                                                  |
| ------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Backend verify + PostgreSQL isolado   | Última validação completa: 134 testes, sem falhas/erros/skips                       | Não repetida nesta atividade; sem banco pessoal ou produção                             |
| Mobile com CARRIM_LIVE_SYNC=1         | 166 testes, 28 arquivos, sem skips                                                  | Plugins/ciclo de vida nativos substituídos                                              |
| Integração real local                 | API/PostgreSQL/SQLite; reabertura, resposta perdida, replay, push/pull e Repository | API/cluster em portas temporárias de loopback; CI mobile padrão pula os dois casos live |
| Qualidade mobile                      | Lint, formatação, SQLite real e build passaram                                      | Aviso de bundle inicial ~1,16 MB permanece                                              |
| Android assembleDebug                 | Passou no Cloud/CI, incluindo PR #29                                                | Não é release assinada nem execução física                                              |
| Produção descartável (registro Cloud) | UID 10001, PostgreSQL TLS de teste, readiness UP/DOWN, health privado               | Não comprova domínio/CA/backup/segredos reais                                           |

Sem CARRIM_LIVE_SYNC=1, live é explicitamente pulado. Os últimos PRs e seus checks de CI foram conferidos no GitHub nesta retomada; não substituem teste nativo.

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
- Validação local: 166 testes passaram com HTTP/PostgreSQL/SQLite isolados nos modos web/nativo, sem skips; lint, formatação, SQLite real e build aprovados. No navegador foram medidos pulls ociosos de 15,047 s e 30,034 s e primeiro push de nova compra 82 ms após o clique, com compra confirmada no PostgreSQL. O timer de 60 s, pausa/retomada, eventos nativos, backoff, concorrência e limpeza de listeners foram verificados nos testes automatizados. O navegador de inspeção manteve documentos visíveis mesmo ao abrir outra aba; essa tentativa não comprova pausa real por ocultamento.
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
