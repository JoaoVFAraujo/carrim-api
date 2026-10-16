# Estado, métricas e próximos passos

Consolidado em **16/10/2026**. Fonte única de retomada: atualizar estado/prioridades aqui, sem novos documentos por sessão.

## Onde paramos

Main funcional de referência: API 8515b48, mobile 700bcce. Esta consolidação incorpora retomadas Cloud 08f0f52/915a113 e visão do PC, sem alterar funcionalidades, migrations, configurações ou versões.

| Área           | Estado real                                                                                                     |
| -------------- | --------------------------------------------------------------------------------------------------------------- |
| Compra offline | Mercado, limite inicial, itens manuais/código, peso/promoção, total, finalização e Histórico                    |
| Catálogo       | Busca local por nome/código, preço anterior regular por mercado/data; preço de hoje obrigatório                 |
| Scanner        | MLKit integrado; câmera/permissões físicas pendentes                                                            |
| Backend        | Domínio, ArchUnit, APIs protegidas, PostgreSQL/Flyway V1–V6, versões e finalização idempotente                  |
| Identidade     | Keystore/Keychain e vínculo SQLite/dono; divergência pausa sync                                                 |
| Sync           | Push idempotente, journal/pull, bootstrap, reservas/retry e pedido congelado; SQLite V1–V10                     |
| Conflitos      | Preserva estados e pausa dependentes; sem resolução assistida ou auto-rebase                                    |
| Android        | APK debug compilado no Cloud/CI; aparelho, Keystore/SQLite nativos e atualização física pendentes               |
| Produção       | Perfil, TLS verificado no banco, health privado, logs JSON, imagem sem root e CI preparados; sem deploy público |
| Layout         | Direção e imagens versionadas; redesenho adiado e aprovação final em aparelho pendente                          |

API **0.0.8**, mobile **0.0.2**, Android versionCode **2** são versões dos manifests, não release V1.0.0. Web permanece local e não sincroniza compras persistidas.

## Evidências e métricas técnicas

Resultados Cloud vieram das retomadas; logs brutos e APK da sessão não estão no Git. Não foram repetidos no PC nesta consolidação documental.

| Verificação                         | Resultado registrado                                                                                        | Limite                                                       |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Backend verify + PostgreSQL isolado | 130 testes, sem falhas/erros/skips                                                                          | Não comprova banco pessoal ou produção                       |
| Mobile com CARRIM_LIVE_SYNC=1       | 107 testes, 21 arquivos                                                                                     | Plugins nativos substituídos                                 |
| Integração real local               | API/PostgreSQL/SQLite; reabertura, resposta perdida, replay sem duplicar e alteração REST recebida por pull | Exige API isolada em 8082; CI mobile padrão não executa live |
| Qualidade mobile                    | Lint, formatação, SQLite, build e cap sync passaram                                                         | Aviso de bundle >1 MB permanece                              |
| Android assembleDebug               | Passou no Cloud/CI                                                                                          | Não é release assinada nem execução física                   |
| Produção descartável                | UID 10001, PostgreSQL TLS de teste, readiness UP/DOWN, health negado na porta principal                     | Não comprova domínio/CA/backup/segredos reais                |

Sem CARRIM_LIVE_SYNC=1, live é explicitamente pulado. Os últimos PRs e seus checks de CI foram conferidos no GitHub nesta retomada; não substituem teste nativo.

| Entrega integrada                              | Evidência                                                                                                                                                                                                                                              |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Backend push/pull/produção                     | [#11](https://github.com/JoaoVFAraujo/carrim-api/pull/11), [#12](https://github.com/JoaoVFAraujo/carrim-api/pull/12), [#13](https://github.com/JoaoVFAraujo/carrim-api/pull/13)                                                                        |
| Mobile identidade/pull/integração/configuração | [#13](https://github.com/JoaoVFAraujo/carrim-mobile/pull/13), [#14](https://github.com/JoaoVFAraujo/carrim-mobile/pull/14), [#15](https://github.com/JoaoVFAraujo/carrim-mobile/pull/15), [#16](https://github.com/JoaoVFAraujo/carrim-mobile/pull/16) |
| CI das entregas finais                         | [Backend](https://github.com/JoaoVFAraujo/carrim-api/actions/runs/38487350275), [Mobile](https://github.com/JoaoVFAraujo/carrim-mobile/actions/runs/38487529655)                                                                                       |

Ao repetir, atualizar evidência com data, ambiente, commit e resultado; preservar histórico no Git. Contagem de testes não é cobertura de todos os riscos.

## Próximas atividades

| Prioridade | Entrega                                | Critério                                                                                                |
| ---------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1          | Resolução assistida (SYNC-009/010/011) | Mostrar local/remoto, decidir explicitamente e preservar idempotência; auto-rebase só com regras/testes |
| 2          | Ações mobile (SHOP-004, BUDGET-002)    | Cancelar compra e editar/remover orçamento com SQLite/fila e testes; API já oferece                     |
| 3          | Aparelho e supermercado (TEST-001/005) | Roteiro Android, offline/online, câmera, Keystore, SQLite, segundo plano e atualização                  |
| 4          | Homologação segura (OPS/SEC)           | HTTPS, CORS/WebView, banco/CA/segredos, backup/restauração, gateway e alertas, com autorização          |
| 5          | Polimento e release (UX, V1.0.0)       | Layout conforme referências, acessibilidade, haptics/animações úteis, bundle, assinatura e distribuição |

Redesenho permanece adiado; versionar referências não autoriza mudar a UI agora. iOS depende de macOS/Xcode. Não limpar dados, substituir proprietário, trocar operationId ou reativar FAILED automaticamente para contornar conflitos.

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
