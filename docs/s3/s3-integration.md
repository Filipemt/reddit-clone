# Integração com S3 (módulo de media)

Este documento descreve a camada de integração com o Amazon S3 do módulo `media`: os componentes envolvidos, como o upload e a leitura de um objeto acontecem, o modelo de dados que guarda a referência do arquivo, as propriedades de configuração e o que precisa existir do lado da AWS para a camada funcionar.

## 1. Visão geral

A aplicação usa o **AWS SDK for Java v2** (`software.amazon.awssdk:s3`, versão `2.29.0` em `pom.xml`) e opera em **dois clientes distintos**:

| Cliente | Responsabilidade |
|---|---|
| `S3Client` | operações de objeto no bucket — hoje, apenas o `putObject` do upload |
| `S3Presigner` | geração de **URLs pré-assinadas** (presigned URLs) para leitura do objeto |

O binário do arquivo **nunca é persistido no banco**: ele vai para o bucket e o PostgreSQL guarda apenas a referência (`bucket`, `object_key`) e metadados (`content_type`, `size_bytes`, `created_at`) na tabela `tb_media`.

O fluxo de leitura não expõe o bucket publicamente: cada leitura gera uma URL pré-assinada de curta duração, assinada com as mesmas credenciais usadas pela aplicação. O objeto permanece privado no bucket.

```
Upload (via backend)                    Leitura
─────────────────────                   ───────
POST /communities                       Cliente chama a API
  (multipart: icon, banner)               │
        │                                ▼
        ▼                          S3Presigner.presignGetObject()
  S3Client.putObject()                         │
        │                                      ▼
        ▼                                URL assinada (expira em N s)
  tb_media (bucket, object_key, metadata)      │
        │                                      ▼
        └──────────────► getUrl(mediaId) ──► resposta da API
```

## 2. Arquitetura

A camada vive em `src/main/java/com/motadev/clone_reddit/media/` e a configuração dos clientes em `shared/config/`.

```
CommunityController  (POST /communities, multipart)
        │
        ▼
CommunityServiceImpl  ──► MediaServiceI ──► S3ServiceImpl
                                             ├── S3Client        (putObject)
                                             ├── S3Presigner     (presignGetObject)
                                             ├── MediaRepository (tb_media)
                                             └── MediaConverter  (MultipartFile ⇄ DTO/entidade)
```

Responsabilidades de cada componente:

| Componente | Responsabilidade |
|---|---|
| `shared/config/StorageConfig.java` | Cria os beans `S3Client` e `S3Presigner` com região e credenciais estáticas vindas da configuração |
| `media/service/MediaServiceI.java` | Contrato da camada: `upload(MultipartFile, String folder)` e `getUrl(UUID mediaId)` |
| `media/service/impl/S3ServiceImpl.java` | Monta a `objectKey`, executa o `putObject`, persiste os metadados e assina a URL de leitura |
| `media/converter/MediaConverter.java` | Converte `MultipartFile` → `MediaUploadRequest`, `MediaUploadRequest` → `Media` e `Media` → `MediaResponse` |
| `media/entity/Media.java` | Entidade JPA da tabela `tb_media` |
| `media/repository/MediaRepository.java` | Repositório JPA de mídia (apenas as operações padrão do `JpaRepository`) |
| `media/dtos/request/MediaUploadRequest.java` | Record com `content` (`byte[]`), `contentType` e `folder` |
| `media/dtos/response/MediaResponse.java` | Record com `mediaId` e `url` |

`S3ServiceImpl` é a única implementação de `MediaServiceI` — a separação entre contrato e implementação é o que permite trocar o backend de storage (S3, MinIO, outro) sem alterar os consumidores, como o módulo `community`.

## 3. Configuração dos clientes — `StorageConfig`

`shared/config/StorageConfig.java` é um `@Configuration` que constrói os dois clientes na inicialização do contexto Spring:

```java
@Bean
public S3Client s3Client() {
    return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider())
            .build();
}

@Bean
public S3Presigner s3Presigner() {
    return S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider())
            .build();
}
```

Pontos da implementação:

- **Credenciais estáticas** — `StaticCredentialsProvider` com `AwsBasicCredentials`, montado a partir de `aws.access-key` e `aws.secret-access-key` (`StorageConfig.java:30-34`). As mesmas credenciais assinam o upload e a URL pré-assinada.
- **Um provider para os dois clientes** — a assinatura da presigned URL usa a mesma identidade que faz o `putObject`, ou seja, quem consegue ler via URL é a identidade da aplicação, com as permissões que ela tiver no bucket.
- **Sem `endpointOverride`** — o builder é configurado apenas com `region` e credenciais; o SDK resolve o endpoint da região da AWS. Não há uso de *path-style access*.
- **Propriedades lidas por `@Value`** — `aws.region`, `aws.access-key` e `aws.secret-access-key` não têm valor default: se a env var correspondente faltar, a aplicação não sobe.

## 4. Propriedades e variáveis de ambiente

Bloco `aws` em `src/main/resources/application.yaml:37-43`:

```yaml
aws:
  access-key: ${AWS_ACCESS_KEY_ID}
  secret-access-key: ${AWS_SECRET_ACCESS_KEY}
  region: ${AWS_REGION}
  s3:
    bucket-name: ${AWS_S3_BUCKET_NAME}
    presigned-url-expiration-seconds: ${AWS_S3_PRESIGNED_URL_SECONDS}
```

| Propriedade | Variável de ambiente | Tipo | Consumida em |
|---|---|---|---|
| `aws.region` | `AWS_REGION` | String | `StorageConfig.s3Client/s3Presigner` |
| `aws.access-key` | `AWS_ACCESS_KEY_ID` | String | `StorageConfig.credentialsProvider` |
| `aws.secret-access-key` | `AWS_SECRET_ACCESS_KEY` | String | `StorageConfig.credentialsProvider` |
| `aws.s3.bucket-name` | `AWS_S3_BUCKET_NAME` | String | `S3ServiceImpl.bucketName` (upload e resposta) |
| `aws.s3.presigned-url-expiration-seconds` | `AWS_S3_PRESIGNED_URL_SECONDS` | long | `S3ServiceImpl.presignedUrlExpirationSeconds` (GET) |

O `.env.example` da raiz lista exatamente essas cinco variáveis:

```
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_REGION=
AWS_S3_BUCKET_NAME=
AWS_S3_PRESIGNED_URL_SECONDS=
```

Sobre a validade da URL pré-assinada: `S3ServiceImpl` declara `@Value("${aws.s3.presigned-url-expiration-seconds:3600}")` (`S3ServiceImpl.java:33-34`), com fallback de 3600 s (1 h). Como o `application.yaml` sempre define a propriedade a partir da env var, na prática o valor vem do ambiente.

O bucket é único por ambiente e entra na construção da `objectKey`; o valor em vigor é o mesmo para todas as mídias, porque é lido da configuração e não do request.

## 5. Fluxo de upload

`S3ServiceImpl.upload(MultipartFile file, String folder)` (`S3ServiceImpl.java:46-67`):

**1. Leitura do arquivo para memória** — `MediaConverter.toUploadRequest` chama `file.getBytes()` e monta o record `MediaUploadRequest(content, contentType, folder)`. Uma `IOException` na leitura é reembalada como `UncheckedIOException("Failed to read uploaded file")` (`MediaConverter.java:15-21`).

**2. Montagem da `objectKey`** — a extensão vem do nome original do arquivo (o que vem depois do último `.`, incluindo o ponto; string sem ponto resulta em `""` — `S3ServiceImpl.java:69-74`) e a chave é composta como:

```java
String objectKey = request.folder() + "/" + UUID.randomUUID() + extension;
```

O UUID aleatório evita colisão entre uploads e não expõe o nome original do arquivo. A pasta (`folder`) é definida pelo consumidor — ver [seção 8](#8-uso-pela-comunidade).

**3. Envio ao S3** — `putObject` com a chave e o `contentType` vindo do upload:

```java
s3Client.putObject(
        PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(request.contentType())
                .build(),
        RequestBody.fromBytes(request.content())
);
```

O corpo é enviado de uma vez (`RequestBody.fromBytes`), a partir do `byte[]` já carregado em memória.

**4. Persistência dos metadados** — `MediaConverter.toEntity` grava `bucket`, `objectKey`, `contentType` e `sizeBytes` (o `length` do `byte[]`), e `mediaRepository.save(media)` persiste a linha em `tb_media`. O `mediaId` é um UUID gerado pelo Hibernate (`@GeneratedValue(strategy = GenerationType.UUID)`) e o `createdAt` via `@CreationTimestamp`.

**5. Resposta** — o serviço chama `getUrl(media.getMediaId())` e devolve `MediaResponse(mediaId, url)`, com a URL pré-assinada já pronta para o cliente consumir.

O `upload` não é anotado com `@Transactional`; a gravação em `tb_media` é a única operação de banco do método, feita pelo `save` do próprio repositório.

## 6. Fluxo de leitura (URL pré-assinada)

`S3ServiceImpl.getUrl(UUID mediaId)` (`S3ServiceImpl.java:76-92`):

1. Busca a mídia por `mediaId` em `tb_media`; se não existir, lança `ResourceNotFoundException("Media not found")`, tratado pelo `GlobalExceptionHandler` como **404** (`shared/exception/GlobalExceptionHandler.java:21-26`).
2. Monta um `GetObjectPresignRequest` com `signatureDuration = Duration.ofSeconds(presignedUrlExpirationSeconds)` e o `GetObjectRequest` montado com o **bucket e a key vindos do registro do banco** (não da configuração) — assim a assinatura acompanha o bucket em que o objeto foi realmente gravado.
3. `s3Presigner.presignGetObject(presignRequest).url()` devolve a URL assinada, que já contém a query SigV4 (`X-Amz-Signature`, `X-Amz-Expires`, `X-Amz-Credential`) e só é válida até `X-Amz-Expires`.

O bucket é lido de `media.getBucket()` porque o `mediaId` persistido carrega o bucket junto — a coluna existe justamente para o registro ser autossuficiente na geração da URL.

## 7. Modelo de dados

Tabela `tb_media`, criada pelo changelog Liquibase `db/changelog/changes/20260921_create_tb_media.xml` (incluído em `master.yaml:14-15`):

| Coluna | Tipo | Restrições | Origem no código |
|---|---|---|---|
| `media_id` | UUID | PK, `NOT NULL`, default `gen_random_uuid()` | `Media.mediaId` (`@GeneratedValue(UUID)` — gerado pela aplicação) |
| `bucket` | VARCHAR(100) | `NOT NULL` | `bucketName` da configuração |
| `object_key` | VARCHAR(255) | `NOT NULL` | `folder + "/" + UUID + extensao` |
| `content_type` | VARCHAR(100) | `NOT NULL` | `file.getContentType()` |
| `size_bytes` | BIGINT | `NOT NULL` | `byte[].length` |
| `created_at` | TIMESTAMP | `NOT NULL` | `@CreationTimestamp` |

A entidade usa `@Table(name = "tb_media")` e o Hibernate roda com `ddl-auto: validate` (`application.yaml:11-13`): o schema é de responsabilidade do Liquibase, e a aplicação apenas valida que a entidade bate com a migration.

O `media_id` é gerado pela aplicação (`@GeneratedValue(strategy = GenerationType.UUID)`); o `default gen_random_uuid()` em `tb_media` foi adicionado depois pelo changelog `20260921_add_uuid_defaults.xml:12` e cobre a geração no lado do banco.

**Referências a partir da comunidade** — `tb_community` guarda apenas o UUID da mídia, nunca a URL nem o binário:

| Coluna | FK | Changelog |
|---|---|---|
| `icon_media_id` | `fk_community_icon_media` → `tb_media.media_id` | `20260928_rename_community_media_to_icon.xml` |
| `banner_media_id` | `fk_community_banner` → `tb_media.media_id` | `20260921_add_banner_media_to_community.xml` |

Isso mantém o desacoplamento entre `community` e `media`: a comunidade conhece a referência (UUID), e a resolução do arquivo (bucket + key) é responsabilidade do módulo `media`.

## 8. Uso pela comunidade

Hoje o único consumidor da camada é a criação de comunidade. `CommunityController` (`community/controller/CommunityController.java:22-30`) recebe `multipart/form-data` com a parte `data` (JSON da comunidade) e as partes opcionais `icon` e `banner`.

`CommunityServiceImpl` (`community/service/impl/CommunityServiceImpl.java`) define as pastas por uso e por período:

```java
private static final String COMMUNITY_ICON_FOLDER =
        "communities/icons/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));

private static final String COMMUNITY_BANNER_FOLDER =
        "communities/banners/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
```

Como são `static final`, o `yyyy/MM` é resolvido **no carregamento da classe** — a partição do dia só muda ao reiniciar a aplicação.

O serviço:

1. Chama `uploadIfPresent(iconFile, COMMUNITY_ICON_FOLDER)` e o equivalente para o banner; o método ignora partes ausentes ou vazias (`file == null || file.isEmpty()`) e não gera upload nesse caso.
2. Salva a comunidade com `iconMediaId` / `bannerMediaId` extraídos do `MediaResponse`.
3. Devolve `CommunityResponseDTO` com `icon` e `banner` do tipo `MediaResponse` — ou seja, `mediaId` + a URL pré-assinada de cada arquivo, pronta para o front consumir.

Os prefixos `communities/icons/` e `communities/banners/` funcionam como a organização lógica do bucket; o particionamento por `yyyy/MM` é o que a AWS usa como *prefix* para cobrança e políticas de ciclo de vida.

## 9. Configuração do lado da AWS

Esta parte é infraestrutura — não está no repositório — mas é o que a camada pressupõe existir.

### 9.1 Criação do bucket

```bash
aws s3api create-bucket --bucket <NOME_DO_BUCKET> --region <REGIAO>
```

O nome do bucket deve ser o mesmo valor de `AWS_S3_BUCKET_NAME`, e a região deve ser a mesma de `AWS_REGION` — o `S3Client` assina a requisição para a região configurada.

Para subir o mesmo código contra um S3 compatível local (MinIO, conforme a decisão registrada no `README.md`), o `docker-compose.yml` da raiz hoje sobe **apenas o PostgreSQL**, e o `StorageConfig` não define `endpointOverride` nem *path-style access* — a configuração de endpoint é um passo adicional a fazer no código para esse cenário.

### 9.2 Credenciais e IAM

A aplicação usa uma access key de longa duração (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`). As únicas operações que o código executa no bucket são `PutObject` (upload) e leitura via URL assinada — o que é autorizado por `s3:GetObject` na identidade que assina. A policy mínima equivalente:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::<NOME_DO_BUCKET>/*"
    }
  ]
}
```

O `s3:ListBucket` não é necessário para o fluxo atual — a camada não lista objetos. Ele só entra na policy se a evolução incluir listagem ou navegação por prefixo.

### 9.3 CORS

O upload atual trafega **pelo backend** (o arquivo vem no `multipart/form-data` da aplicação e é enviado ao S3 pelo servidor), então **não há CORS configurado nem necessário** para o fluxo vigente. CORS só passa a ser requisito se o upload for movido para o browser (presigned `PUT` direto no bucket), caso em que o bucket precisa de algo como:

```json
[
  {
    "AllowedOrigins": ["http://localhost:3000"],
    "AllowedMethods": ["GET", "PUT"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

`ExposeHeaders: ["ETag"]` só é necessário se o front precisar ler o `ETag` retornado no upload direto.

## 10. Estado atual da camada

Fatos do que existe hoje no código, para evitar suposições ao consumir a camada:

- **Não há controller de `media`.** O upload só é disparado por outro módulo (`POST /communities`); `MediaServiceI` é chamado internamente, não exposto em rota própria.
- **Não há operação de delete.** O `S3Client` é usado apenas para `putObject`; não existe remoção de objeto nem de registro em `tb_media`.
- **Não há validação de arquivo.** O `upload` carrega o `TODO: Adicionar validações de tamanhos de arquivos / extensões permitidas para ícone e banner` (`S3ServiceImpl.java:48`); hoje não há limite de tamanho, checagem de tipo real do arquivo nem allowlist de extensão — o `content_type` é o declarado pelo cliente.
- **Sem transação em volta do upload.** Não há `@Transactional` no `S3ServiceImpl`; o objeto é gravado no bucket antes de o registro ser salvo no banco, e não há compensação se o `save` falhar.
- **Sem testes cobrindo a camada.** Não há classe de teste para `media`; os testes de integração (`CloneRedditApplicationTests`, `AuthenticationFlowIntegrationTest`, `SecurityConfigIntegrationTest`) sobem o contexto com `@ActiveProfiles("test")`, e `src/test/resources/application-test.yaml` sobrescreve apenas as chaves JWT — as variáveis `AWS_*` precisam estar presentes no ambiente para que o `StorageConfig` seja construído.
- **Sem cache de URL.** `getUrl` assina uma nova URL a cada chamada; a validade é a de `AWS_S3_PRESIGNED_URL_SECONDS` a partir do momento da geração.
