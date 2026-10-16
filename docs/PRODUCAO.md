# Preparação de produção

Esta entrega prepara configuração e verificações; não publica a API nem libera a V1.0.0.

Executar `bash mvnw verify` com JDK 25 e PostgreSQL isolado nos testes. Após sucesso, `docker build -t carrim-api:local .` usa o JAR validado e roda com usuário 10001. Configurar limites de memória/CPU no orquestrador e montar certificados de banco somente leitura. Não incluir credenciais, truststores do ambiente cloud ou arquivos .env na imagem.

O perfil `prod` exige SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME e SPRING_DATASOURCE_PASSWORD. A URL PostgreSQL deve usar `sslmode=verify-full`; disponibilizar a CA do banco via `sslrootcert` quando necessário. Segredos entram pelo ambiente/secret manager. A API HTTP 8082 deve ficar atrás de um terminador TLS; o aplicativo nativo aceita somente HTTPS. Nenhum domínio público foi configurado nesta entrega.

Saúde fica em loopback, porta 9090 por padrão (MANAGEMENT_SERVER_PORT), somente `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`, sem detalhes. Readiness inclui banco. A porta principal não libera esses endpoints. Monitoramento deve executar na mesma máquina/namespace; não publicar a porta de gerenciamento. Logs estruturados são JSON e não devem registrar Bearer, prova da instalação ou payload de autenticação.

Antes de produção: banco/CA e HTTPS reais; backup criptografado do PostgreSQL e teste de restauração em banco isolado; rotação/revogação de credenciais; limite de requisições no gateway (a baseline local de bootstrap não é distribuída); métricas/alertas privados; verificação de APK assinado e dispositivo real; teste de atualização preservando identidade e SQLite. Estas dependências externas continuam abertas.

PRs #11, #12 e #13 integrados na main, nessa ordem. Preparação não representa deploy; publicação de infraestrutura ainda depende de autorização específica. Consulte [retomada](RETOMADA-PC.md).

Validação local realizada: Maven verify com PostgreSQL isolado; imagem Docker construída e inicializada como 10001:10001 com CA pública de teste montada somente leitura. Perfil prod conectou usando sslmode=verify-full; readiness retornou UP com banco e DOWN ao interrompê-lo, depois voltou a UP. A rota de saúde na porta principal retornou 401. Certificado de teste e banco descartável não fazem parte do repositório ou da imagem. A CA deve ser legível pelo usuário 10001, sem tornar a chave privada pública.

Pedidos POST/PUT/PATCH de API são limitados a 2 MiB antes de materializar JSON; sync limita cada payload a 32 KiB e o lote a 50 operações. Limites de gateway e proteção distribuída ainda devem ser configurados na infraestrutura real.
