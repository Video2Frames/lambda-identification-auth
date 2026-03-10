# Lambda Identification Auth

Descrição

Esta Lambda implementa endpoints HTTP (via API Gateway v2) para criar clientes em um User Pool do Cognito, autenticar usuários e retornar informações do usuário a partir de um token JWT.

Observações rápidas

- A Lambda está implementada em `app/src/main/java/tech/buildrun/lambda/Handler.java`.
- A região do SDK está fixa como `us-east-1` no código.
- Variáveis de ambiente necessárias:
  - `USER_POOL_ID` — ID do Cognito User Pool.
  - `CLIENT_ID` — ID do App Client (deve permitir o fluxo USER_PASSWORD_AUTH).

Endpoints

1) POST /clients
- Descrição: cria um usuário (AdminCreateUser + AdminSetUserPassword) no User Pool.
- Código de sucesso: 201
- Corpo da requisição (JSON):
  - email (string)
  - password (string)

Exemplo (curl):

curl -X POST "https://{API_GATEWAY_URL}/clients" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Secret123!"}'

Exemplo de resposta (201):
{
  "message": "cliente criado com sucesso"
}

Notas:
- A criação usa `MessageActionType.SUPPRESS`, portanto nenhum e-mail de convite será enviado.
- A senha é definida como permanente via `AdminSetUserPassword`.

2) POST /login
- Descrição: realiza autenticação via `USER_PASSWORD_AUTH` e retorna tokens (id_token, access_token, refresh_token).
- Código de sucesso: 200
- Corpo da requisição (JSON):
  - email (string)
  - password (string)

Exemplo (curl):

curl -X POST "https://{API_GATEWAY_URL}/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Secret123!"'}

Exemplo de resposta (200):
{
  "id_token": "eyJ...",
  "access_token": "eyJ...",
  "refresh_token": "..."
}

Dica para usar o token (com jq):

TOKEN=$(curl -s -X POST "https://{API_GATEWAY_URL}/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Secret123!"}' | jq -r '.id_token')

3) GET /me
- Descrição: decodifica o JWT (sem validação de assinatura) e retorna campos `id` (sub) e `email` do payload.
- Código de sucesso: 200
- Header obrigatório:
  - Authorization: Bearer {JWT}

Exemplo (curl) usando o token obtido no login:

curl -X GET "https://{API_GATEWAY_URL}/me" \
  -H "Authorization: Bearer $TOKEN"

Exemplo de resposta (200):
{
  "id": "xxxx-xxxx-xxxx",
  "email": "user@example.com"
}

Erros comuns e códigos retornados

- 401 Token não informado / inválido / JWT inválido — quando o header Authorization estiver ausente, mal formado ou o token não possuir 3 partes.
- 404 endpoint não encontrado — rota desconhecida.
- 500 erro interno — exceções diversas (ver logs CloudWatch para stacktrace).

