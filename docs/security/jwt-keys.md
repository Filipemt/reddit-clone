# Chaves JWT (app.key / app.pub)

O projeto assina e valida tokens JWT usando criptografia assimétrica RSA.

- **`src/main/resources/app.key`** — chave privada. Usada pelo `JwtEncoder` para **assinar** os tokens emitidos no login.
- **`src/main/resources/app.pub`** — chave pública. Usada pelo `JwtDecoder` do resource server para **validar** a assinatura de tokens recebidos em requisições autenticadas.

As chaves são referenciadas em `application.yaml`, sob `jwt.public.key` e `jwt.private.key`, e carregadas pelo `SecurityConfig` via `@Value`.

> **Importante:** `app.key` e `app.pub` estão no `.gitignore`. A chave privada **nunca deve ser commitada** — quem a possuir consegue forjar tokens.

## Localização

```
src/main/resources/
├── application.yaml
├── app.key     (gerado localmente)
├── app.pub     (gerado localmente)
└── db/
```

As chaves ficam no classpath, por isso o caminho na configuração é `classpath:app.key` / `classpath:app.pub`.

## Geração

Pré-requisito: **OpenSSL 3.x** instalado.

### macOS / Linux

```bash
openssl genpkey -algorithm RSA -out src/main/resources/app.key -pkeyopt rsa_keygen_bits:2048
chmod 600 src/main/resources/app.key
openssl rsa -in src/main/resources/app.key -pubout -out src/main/resources/app.pub
```

### Windows (PowerShell)

```powershell
openssl genpkey -algorithm RSA -out src\main\resources\app.key -pkeyopt rsa_keygen_bits:2048
openssl rsa -in src\main\resources\app.key -pubout -out src\main\resources\app.pub
```

O `chmod 600` restringe a leitura da chave privada apenas ao usuário dono do arquivo (ignorar no Windows).

## Verificação

Confirme que o par foi gerado corretamente:

```bash
openssl rsa -in src/main/resources/app.key -check
openssl rsa -in src/main/resources/app.key -pubout | diff - src/main/resources/app.pub && echo "PAIR OK"
```

## Rotação de chaves

Para trocar as chaves (ex.: suspeita de vazamento ou política de segurança):

1. Gere um novo par, conforme a seção [Geração](#geração).
2. Reinicie a aplicação.
3. **Tokens emitidos antes da troca deixam de ser válidos** — o resource server passa a validar apenas com a nova chave pública.

> Configurar período de validade menor para o token (`jwt.expiresIn`) reduz o impacto de uma rotação não programada.

## Troubleshooting

| Sintoma | Causa provável | Solução |
|---|---|---|
| App não sobe; erro de recurso não encontrado (`app.key` / `app.pub`) | Chaves não foram geradas | Rode a [geração](#geração) antes de subir a aplicação |
| `401` em requisições autenticadas após troca de chaves | Tokens antigos assinados com a chave anterior | Gere um novo token no `/authentication/login` |
| Integer size / "key too small" ao gerar | OpenSSL 2.x legado | Atualize para OpenSSL 3.x ou use `openssl req -x509 ...` com OpenSSL 1.x |