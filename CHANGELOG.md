# Changelog

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
