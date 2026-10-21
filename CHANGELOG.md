# Changelog

## Não lançado — fotos com aprovação em 21/10/2026

- PostgreSQL V7 e SQLite 011: foto limitada, preferência versionada e fila de mídia separada, com upload idempotente, remoção e recuperação após falhas sem bloquear compras. Foto própria persiste offline e no servidor; item manual sem código também é suportado.
- Fotos USER são candidatas PENDING: visíveis ao autor, públicas para outros clientes somente depois de APPROVED. Painel mínimo de aprovação/retirada com chave administrativa separada, decisões versionadas e auditoria; substituição pendente mantém a imagem pública anterior.
- JPEG/PNG reais, cotas, limites de bytes/pixels/concorrência, normalização sem EXIF e importação externa limitada. Scanner/Carrinho usam miniaturas contidas; câmera/galeria, prévia e restauração explícita do catálogo.
- Validação: 165 testes backend, 244 frontend em 35 arquivos com integração real isolada nos modos web/nativo, qualidade/SQLite/builds aprovados. Upload próprio, importação real Open Food Facts e aprovação administrativa conferidos na interface e no PostgreSQL isolado; falha temporária de conexão com o CDN recuperou após reinício, sem relaxar TLS. Fotos contidas no Carrinho. Recuperação de imagem pública retirada corrigida, validada na rodada completa e revisada sem apontamentos pendentes; outra identidade confirmou acesso somente à contribuição aprovada. Prévias alta/larga/quadrada, cancelamento e fotos após reload conferidos. PRs API #22/mobile #32 abertos com `@codex review`, revisão em andamento, CI API aprovado e mobile em andamento. APK debug assinado v2 gerado e instalado como atualização no Moto G73, MainActivity/processo iniciados; tela apagada impede confirmar layout/câmera físicos. Merge pendente; sem incremento de versão, release ou deploy.

## Não lançado — catálogo automático em 20/10/2026

- Consulta autenticada somente leitura por código, com catálogo pessoal e sugestões Open Food Facts v3, cache limitado, orçamento de chamadas e ativação externa desabilitada por padrão em prod. Scanner confirma preço e grava pelo sync existente; foto opcional sem persistência offline.
- Backend: 147 testes com PostgreSQL isolado; mobile: 204 testes com integração real. Sem incremento de versão ou deploy; aparelho e aceitação final pendentes.

## Não lançado — consolidação em 16/10/2026

- Push idempotente (V5), journal/pull por proprietário (V6), perfil de produção e CI integrados pelos PRs #11–#13.
- Revisões finais: autorização antes do limite de corpo e health sem sessão/cache.
- Última execução registrada: 130 testes backend passaram. Sem nova versão publicada.
- Documentação consolidada a partir do Cloud e do PC: produto, segurança, contrato, roadmap/métricas e referências visuais; sem mudança funcional ou incremento de versão.
- Ver [roadmap](docs/ROADMAP.md) para evidências, entregas e validações pendentes; resultados acima pertencem ao Cloud.

## 0.0.8 — 08/10/2026

- APIs protegidas de catálogo, mercados, compras, itens e histórico com propriedade derivada do Bearer.
- DTOs monetários exatos, paginação, validação e conflitos de versão.
- V4 com recibos de finalização idempotente e rollback transacional completo.
- Limite local no bootstrap anônimo; HTTP real, concorrência, replay e isolamento testados.
- Limite de 1000 itens validado no agregado dentro da mutação bloqueada, incluindo versões seguintes previstas.
- Matcher normalizado compartilhado entre autorização e limite de bootstrap, evitando bypass por URL codificada.
- 119 testes, Spotless/verify e JAR; contratos e status atualizados.

## 0.0.7 — 07/10/2026

- Identidade anônima com prova da instalação e token Bearer opaco, expiração e renovação.
- Migration V3 guarda somente hashes de credenciais.
- Spring Security Resource Server com validação local e rota protegida de identidade.
- 99 testes passaram; documentação de contrato e status adicionada.

## 0.0.6 — 07/10/2026

- Regras ArchUnit para manter domínio/aplicação independentes de frameworks e adapters.
- Verificação de localização das entidades JPA e ciclos entre pacotes principais.
- 91 testes passaram, Spotless e JAR; sem nova migration ou mudança funcional no mobile.

## 0.0.5 — 07/10/2026

- Migration V2 para compras, itens e registros de preço, preservando o catálogo V1.
- Casos de uso e adapter transacional JDBC com propriedade e versão por agregado.
- Finalização atômica com histórico completo; imutabilidade reforçada no banco.
- 87 testes em PostgreSQL 18.2, incluindo upgrade, rollback e concorrência.

## 0.0.4 — 07/10/2026

- Conexão PostgreSQL por variáveis de ambiente e migrations Flyway no schema carrim.
- Persistência JPA de produtos/supermercados, isolamento por proprietário e versão otimista.
- Migration V1 com chaves, constraints e unicidade de código por proprietário.
- Testes de integração em PostgreSQL 18 isolado; script Windows alternativo ao Docker.
- Aplicação passa a exigir banco/credenciais na execução. Segurança HTTP permanece fechada.
- 77 testes passaram em PostgreSQL 18.2 isolado, com reaplicação de migrations e conflitos concorrentes.

## 0.0.3 — 07/10/2026

- Produtos e supermercados com identidade estável e validação de nomes/códigos.
- Observações derivadas somente de sessões finalizadas, preservando snapshots e preço original.
- Bases de comparação UNIT/KG e equivalência promocional aproximada, com HALF_UP.
- 69 testes, Spotless e JAR empacotado com Java 25.

## 0.0.2 — 07/10/2026

- Domínio Java de dinheiro, subtotais, itens e sessões de compra.
- Cálculos exatos para unidade, peso e grupos promocionais completos.
- Limite opcional, total do caixa e diferença; finalização e cancelamento imutáveis.
- Validação de IDs, snapshots, combinações de preço/medida e datas.
- Backend na porta 8082, com segurança fechada e sem APIs de negócio ou banco.
- Total do caixa não negativo, sem reutilizar o teto de preço por unidade.
- 48 testes, Spotless e JAR empacotado com Java 25.

## 0.0.1

- Fundação Spring Boot, Maven Wrapper e segurança fechada.
