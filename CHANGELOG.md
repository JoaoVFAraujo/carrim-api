# Changelog

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
