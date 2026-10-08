# API local do Carrim

Base: `http://localhost:8082/api/v1`. HTTP somente no desenvolvimento local; tokens/provas exigem HTTPS quando houver hospedagem. Contrato implementado prevalece sobre exemplos históricos do documento-base.

## Identidade — backend 0.0.7

POST `/auth/anonymous`, JSON:

```json
{
  "installationId": "UUID criado no dispositivo",
  "installationSecret": "32 bytes de CSPRNG em base64url canônico sem padding",
  "devicePlatform": "ANDROID",
  "appVersion": "0.0.1"
}
```

Plataformas: ANDROID, IOS, WEB. Prova tem 43 caracteres; UUID/prova devem ser preservados em armazenamento seguro do dispositivo. UUID sozinho não autentica. Não incluir Bearer antigo no bootstrap. A resposta contém `userId`, `installationId`, `accountType`, `accessToken` e `expiresAt` UTC. Token opaco tem 256 bits e validade de 30 dias.

Repetir com a mesma instalação/prova mantém usuário, gera novo token e revoga o anterior. Prova divergente: 403; entrada inválida: 400. Banco guarda hashes, não credenciais em claro. Perder instalação/prova significa perder o mecanismo de recuperação anônimo; vinculação de conta fica para etapa futura.

GET `/auth/me` exige `Authorization: Bearer <accessToken>` e retorna identidade técnica. Token desconhecido, expirado ou inválido: 401. Cookies, HTTP Basic e sessão do navegador não autenticam. Não registrar tokens/provas em logs ou commitá-los; frontend ainda não implementa armazenamento seguro nem chamadas à API.

Rotas de negócio ainda não estão liberadas nesta versão. Integração do mobile, sync, vinculação de contas e deploy ficam fora desta entrega.

Referência de implementação: [Spring Security — tokens opacos](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html).
