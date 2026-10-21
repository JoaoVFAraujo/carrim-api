# Segurança e operação

Baseline do produto inteiro. Código implementado não comprova configuração ou teste em produção. Evidências no [roadmap](ROADMAP.md); contratos na [API](API.md).

## Confiança e controles

Dispositivo, rede e entradas remotas não são confiáveis. UUID, campo escondido, validação Angular, ofuscação ou CORS não provam autorização. Referências: OWASP API Security Top 10 e MASVS/MASTG, sem declarar certificação.

| Implementado                      | Limite ou condição                                                     |
| --------------------------------- | ---------------------------------------------------------------------- |
| Negação por padrão e allowlist    | Só bootstraps explícitos são públicos na API de negócio                |
| Bearer opaco validado localmente  | Expira em 30 dias; não é JWT nem exige segredo de assinatura           |
| Prova de instalação de 32 bytes   | UUID sozinho não recupera usuário; servidor guarda hashes              |
| Propriedade derivada do Bearer    | Validação de dono; UUID alheio retorna 404                             |
| DTOs, limites e SQL parametrizado | Não vincular JSON diretamente a entidades nem confiar em total enviado |
| Finalização e sync idempotentes   | Mesma chave/pedido repete resultado; conteúdo diferente é conflito     |
| Limite local de bootstrap         | 60 chamadas/IP de conexão/60 s; não é proteção distribuída             |
| Limite de corpo após autorização  | POST/PUT/PATCH até 2 MiB; payload sync até 32 KiB, lote até 50         |
| Erros sanitizados                 | Sem SQL, stack trace, segredos ou valores rejeitados                   |
| Perfil prod e health privado      | Exigem rede/configuração abaixo                                        |

Rotas de negócio usam Bearer sem sessão/cookie/Basic/login por formulário. O cookie de prova web é aceito exclusivamente nos bootstraps web, protegidos por cabeçalho customizado, Origin/Fetch Metadata, SameSite=Strict e ausência de CORS; não autentica compras ou catálogo. HTTPS usa prefixo __Host-, Secure e host exclusivo. CSRF é desabilitado somente na cadeia /api/v1/** desse modelo; rotas externas mantêm CSRF e acesso fechado. Adicionar cookies exige revisão. Limiter compartilha matcher normalizado da autorização e ignora IP encaminhado não confiável.

## Mobile e privacidade

- Android: secure-storage cifra credenciais com chave no Keystore. iOS: Keychain sem iCloud, acesso no aparelho desbloqueado e sem migração entre aparelhos. Validação física pendente.
- Nunca guardar token/prova no bundle, Git, SQLite comum, Web Storage, screenshots ou logs. Não chamar fallback web em texto aberto do plugin: o navegador mantém somente o Bearer em memória; a prova persistente vem do cookie HttpOnly preparado pelo servidor.
- Salvar prova antes do bootstrap, validar resposta e serializar renovação, inclusive após 401 durante leitura do cache. Renovação revoga token anterior. Falha de armazenamento bloqueia conexão, sem fallback ou troca silenciosa de usuário.
- SQLite guarda somente installationId/userId no vínculo sync_owner. Ausência, corrupção ou divergência de credenciais num banco vinculado pausa sync.
- HTTP dedicado restringe destino, omite cookies e bloqueia redirects/cache, com timeout. Após 401, renova no máximo uma vez; retry de sync conserva pedido idempotente.
- Android bloqueia cleartext e backup/transferência. Atualizar com mesma assinatura e adb install -r; nunca limpar dados/desinstalar para contornar erro.
- SQLite fica no sandbox; criptografia integral não é requisito implementado nesta etapa. Proteger exports/dumps e cópias de teste. Perder identidade anônima/dados não tem recuperação garantida.
- Câmera só na ação do scanner, com alternativa manual. Sem localização, contatos, microfone ou arquivos sem função real.
- Preservar sanitização Angular: sem HTML/script remoto arbitrário, bypass ou URLs externas sem validação.
- Esta consolidação não implanta telemetria. Antes de coletar, definir finalidade, minimização, acesso e retenção. Compras detalhadas não são logs técnicos.

## Desenvolvimento

Navegador mantém SQLite e identidade por origem; usar Web Locks para impedir gravações concorrentes de abas. Perda do cookie não autoriza substituir proprietário, limpar fila ou reenviar dados sob outra identidade.

Frontend 4202 usa proxy /api/v1/** para 127.0.0.1:8082, sem abrir CORS. HTTP só local; restringir servidores à máquina. Não publicar portas, dumps ou credenciais de teste.

Segredos só no ambiente/gerenciador apropriado; .env reais, configurações privadas, bancos, APKs e logs brutos ficam fora do Git. Usar bancos isolados nos testes. Usuário local postgres não é conta de produção.

## Preparação de produção

Ainda sem deploy nesta retomada. Não conectar banco remoto ou publicar infraestrutura automaticamente.

1. Validar/empacotar API com JDK 25 e banco isolado; depois docker build -t carrim-api:local . O Dockerfile usa JAR validado e UID/GID 10001. Limitar CPU/memória externamente.
2. Ativar perfil prod com SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME e SPRING_DATASOURCE_PASSWORD externos.
3. JDBC PostgreSQL exige sslmode=verify-full; quando necessário, sslrootcert aponta para CA montada somente leitura, legível por UID 10001. Não embutir segredos/truststore privado nem desabilitar validação de certificado/hostname.
4. API 8082 atrás de TLS; banco em rede privada e conta mínima. Separar administração e, quando possível, migrations.
5. Health em loopback:9090 (ou MANAGEMENT_SERVER_PORT), somente health/liveness/readiness sem detalhes. Readiness inclui banco. Porta principal nega health; monitorar no mesmo host/namespace sem publicar gerenciamento.
6. Logs JSON no prod sem Authorization, prova, token, senha, connection string privada ou payload completo. Diagnóstico com código sanitizado e IDs necessários.
7. Configurar gateway/rate limit por IP/identidade/rota, concorrência/timeouts e alertas privados. CDN/WAF não substituem autorização nem eliminam DDoS.
8. Web exige /api/v1 na mesma origem, com proxy preservando Host/origem pública e HTTPS resolvido por encaminhamento confiável (por exemplo, server.forward-headers-strategy=framework atrás de gateway restrito que remove cabeçalhos externos). Perfil prod recusa bootstrap web em HTTP. Não abrir CORS nos bootstraps web. Para WebView nativa de outra origem, configurar/testar allowlist mínima somente nas rotas necessárias. Código atual não fornece CORS amplo. Ajustar CSP aos hosts reais sem liberar scripts/conexões indiscriminadamente.
9. Validar backend HTTPS antes de configurar mobile. O endpoint público não pode conter segredo; geração descrita no README mobile.

Pool prod: até 10 conexões, mínimo 2, espera de 10 s; Tomcat tem limites de conexões/threads. Revalidar capacidade com carga real antes de definir SLOs.

## Condições para liberar

- HTTPS/CA reais, banco privado, privilégio mínimo, rotação/revogação de segredos.
- Backup protegido, retenção definida e restauração testada em banco isolado: integridade, completude e fluxo da aplicação restaurada; nunca sobre o banco de uso.
- Monitoramento privado de saúde, erros, latência, banco e sync, com alertas acionáveis.
- Testes de autenticação, BOLA/IDOR, injection, payload, replay e concorrência; auditoria de dependências e segredos/artefatos.
- Assinatura/distribuição e [roteiro Android](https://github.com/JoaoVFAraujo/carrim-mobile/blob/main/docs/VALIDACAO-ANDROID.md), incluindo atualização preservando dados.
- Reconciliação explícita de conflitos; não desbloquear FAILED, limpar SQLite ou modificar pedido/idempotency key já enviados para forçar envio. A revisão assistida arquiva pedido/estado local/remoto e, após confirmação, cria um novo comando para casos suportados. Comparação e escrita compartilham o lock SQLite; edição local durante o diálogo invalida a decisão. Casos encerrados ou não reconhecidos continuam pausados.
- Autorização específica para infraestrutura/deploy/release.

Pinning e criptografia integral de SQLite não são exigências automáticas da V1; reavaliar conforme risco. SAST/DAST, dependency/container scanning e automação de auditoria são melhorias a avaliar, não controles já comprovados. Audit datado não garante ausência de outras falhas.

## Catálogo externo por código de barras

Open Food Facts é acessado somente pelo servidor em destino HTTPS fixo, com User-Agent público do Carrim, sem Bearer, cookies, identidade, localização, compra ou preço do usuário. Código é validado antes do acesso. A resposta é não confiável: campos limitados, controles removidos, código conferido, imagem restrita à origem images.openfoodfacts.org e caminhos de produtos. Não existe proxy aberto nem URL arbitrária recebida do cliente; a importação de mídia resolve o endereço pelo código na fonte permitida, conforme a seção de fotos abaixo. Timeout inclui corpo e há limite de bytes; 404 representa ausência e falha temporária não é cache negativo.

Somente respostas públicas do fornecedor entram no cache compartilhado. Dados privados são consultados por proprietário antes do cache, sem reutilização entre instalações. O cache externo é limitado e volátil, separado dos cadastros pessoais e do catálogo de candidaturas aprovadas descrito abaixo. A confirmação continua usando SQLite/fila e sync existentes; consulta não grava dados de negócio.

A foto externa é opcional: na sugestão pode ser carregada diretamente do domínio permitido, com referrerpolicy=no-referrer e sem tokens do Carrim; após confirmação, há importação limitada pelo servidor e cache local de mídia, conforme a seção de fotos abaixo. No carregamento direto, o fornecedor recebe IP/metadados normais da conexão do cliente; na importação recebe a conexão do servidor. Registrar ambos no aviso de privacidade antes da publicação. Falha sem cópia local usa placeholder e mantém reconhecimento por nome/código e compra disponíveis.

Licenças: base [ODbL](https://opendatacommons.org/licenses/odbl/1-0/), conteúdos [DbCL](https://opendatacommons.org/licenses/dbcl/1-0/), imagens [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). A ficha e o scanner identificam Open Food Facts e licenças; a mídia importada conserva atribuição e informa adaptação de tamanho/formato pelo reprocessamento. A confirmação de um nome não elimina obrigações sobre extrações repetidas/base derivada. Antes de uso público, revisar atribuição persistente, exportação/redistribuição e obrigações share-alike; não publicar dados privados para cumprir licença nem considerar a separação do cache uma dispensa automática. Referência: [licenças oficiais](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/).

Produção mantém a fonte externa desabilitada por padrão. Habilitar somente após homologação HTTPS, avaliação de cobertura, adequação da privacidade/licenças e capacidade. Cache e orçamento são por processo: uma réplica enquanto habilitado, ou coordenação dos limites entre réplicas e serviços que compartilham IP antes de escalar. Orçamento de 10/min reserva margem ao limite oficial consultado; revalidar regras do fornecedor antes de habilitar. Não ativar retries ilimitados nem buscar a cada frame/tecla. Falha/limite sempre oferece cadastro manual.
## Upload e moderação de fotos

Persistência: V7 cria arquivos BYTEA limitados, preferências separadas e auditoria. O cliente armazena JPEG normalizado em texto base64 no SQLite por compatibilidade com o driver nativo/web; é conteúdo de imagem limitado, nunca credencial. Upload usa endpoint próprio, fora da fila JSON de compras. Não ampliar o limite global de 2 MiB para acomodar originais.

Servidor valida base64 canônico, JPEG/PNG reais, bytes e dimensões antes de decodificar (máximo 4096 por lado e 16 milhões de pixels), concorrência 2 e saída JPEG de até 1024 pixels no maior lado/512000 bytes. A reconstrução por pixels descarta EXIF, inclusive localização; tipos ativos e formatos incompatíveis são recusados. Cliente limita entrada a 8 MiB/16 megapixels e otimiza normalmente para até 256 KiB; validação local não substitui o servidor. A câmera não salva o original na galeria por padrão; resultado restaurado após encerramento do processo vira rascunho local com validade de 24 h e exige confirmação.

Fotos USER são candidatas PENDING, visíveis ao autor e ao moderador. Apenas APPROVED é compartilhada. Negar/remover impede novas leituras públicas; clientes descartam cópia pública quando confirmam a retirada online, inclusive com preferência pendente. Cópia já disponível offline só conhece a retirada ao reconectar. Conteúdo, proprietário e vínculos pessoais de outro usuário não são retornados no catálogo público. Substituir candidato invalida sua aprovação antiga; imagem pública anterior permanece até aprovação da nova. Aprovação e preferência usam locks/versões para evitar publicação de candidato já retirado.

Importação externa nunca aceita URL do usuário: resolve código na fonte permitida, valida proprietário antes de buscar e usa domínio HTTPS fixo de imagens, sem redirecionamentos ou credenciais. Corpo limitado a 500 KiB, timeout total de 5 s, duas importações simultâneas e orçamento próprio de 10/min limitam também os bytes, não apenas consultas JSON. Licença/atribuição Open Food Facts são mantidas; UI informa adaptação de tamanho/formato. Não envia fotos dos usuários ao Open Food Facts automaticamente nem usa serviços pagos.

Moderação de operador único: chave administrativa aleatória separada da identidade de instalação, configurada por hash no ambiente. A tela `/admin/fotos` mantém a chave somente em memória e a descarta ao sair; URLs não contêm a chave. Sem hash válido, operação fica bloqueada. Configurar/rotacionar por canal local seguro e usar HTTPS com proxy confiável em produção; nunca habilitar administração com chave pública padrão. Auditoria é para o operador configurado; múltiplos moderadores exigem identidade/auditoria individual antes de habilitar esse cenário.

Retenção/cotas: remoção elimina bytes, conserva identificação/hash de entrada para impedir ressurreição por replay. Há cotas por usuário e cache local limitado a 100 imagens; pedidos locais superados têm retenção limitada e não são reciclados. Avaliar política de expiração e backup/restauração com volume real antes de produção, sem descartar referências ou decisões ainda pendentes. Banco pessoal não é usado na validação; o ambiente de revisão usa somente dados descartáveis.