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
      (@Transactional)                     ├── MediaFileValidator (tamanho/tipo/extensão)
                                             ├── S3Client        (putObject, deleteObject)
                                             ├── S3Presigner     (presignGetObject)
                                             ├── MediaRepository (tb_media)
                                             └── MediaConverter  (MultipartFile ⇄ DTO/entidade)
```

Responsabilidades de cada componente:

| Componente | Responsabilidade |
|---|---|
| `shared/config/StorageConfig.java` | Cria os beans `S3Client` e `S3Presigner` com região e credenciais estáticas vindas da configuração |
| `media/service/MediaServiceI.java` | Contrato da camada: `upload(MultipartFile, String folder)` e `getUrl(UUID mediaId)` |
| `media/service/impl/S3ServiceImpl.java` | Valida o arquivo, monta a `objectKey`, executa o `putObject`, persiste os metadados, assina a URL de leitura e registra a compensação do objeto quando a transação não confirma |
| `media/validator/MediaFileValidator.java` | Rejeita tamanho e combinações `content-type`/extensão fora das regras antes de ler os bytes; devolve a extensão normalizada |
| `media/config/MediaUploadProperties.java` | Record de configuração (`maxSizeBytes` + regras de tipo), vinculado por `@ConfigurationProperties` e validado no boot |
| `media/config/MediaTypeRule.java` | Record de uma regra: um `content-type` e as extensões aceitas para ele |
| `media/converter/MediaConverter.java` | Converte `MultipartFile` → `MediaUploadRequest`, `MediaUploadRequest` → `Media` e `Media` → `MediaResponse` |
| `media/entity/Media.java` | Entidade JPA da tabela `tb_media` |
| `media/repository/MediaRepository.java` | Repositório JPA de mídia (apenas as operações padrão do `JpaRepository`) |
| `media/dtos/request/MediaUploadRequest.java` | Record com `content` (`byte[]`), `contentType` e `folder` |
| `media/dtos/response/MediaResponse.java` | Record com `mediaId` e `url` |

`S3ServiceImpl` é a única implementação de `MediaServiceI` — a separação entre contrato e implementação é o que permite trocar o backend de storage (S3, MinIO, outro) sem alterar os consumidores, como o módulo `community`.

Repare que a compensação do S3 ([seção 5.1](#51-compensacao-do-s3-quando-a-transacao-nao-confirma)) mora na implementação e **não** aparece no contrato `MediaServiceI`: ela é um efeito colateral do upload, não uma operação que o consumidor possa pedir. Isso mantém os consumidores livres de qualquer preocupação com o storage — o módulo `community` continua apenas esperando um `MediaResponse` e um rollback de banco.

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

Bloco `aws` em `src/main/resources/application.yaml:42-48`:

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

Sobre a validade da URL pré-assinada: `S3ServiceImpl` declara `@Value("${aws.s3.presigned-url-expiration-seconds:3600}")` (`S3ServiceImpl.java:40-41`), com fallback de 3600 s (1 h). Como o `application.yaml` sempre define a propriedade a partir da env var, na prática o valor vem do ambiente.

O bucket é único por ambiente e entra na construção da `objectKey`; o valor em vigor é o mesmo para todas as mídias, porque é lido da configuração e não do request.

### 4.1 Configuração de validação e limite de upload

Além das credenciais, a camada lê um bloco `media.upload` e o limite de multipart do container:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 12MB

media:
  upload:
    max-size-bytes: ${MEDIA_MAX_SIZE_BYTES:5242880}
    types:
      - content-type: image/jpeg
        extensions: [".jpg", ".jpeg"]
      - content-type: image/png
        extensions: [".png"]
      - content-type: image/webp
        extensions: [".webp"]
      - content-type: image/gif
        extensions: [".gif"]
```

| Propriedade | Origem | Tipo | Consumida em |
|---|---|---|---|
| `spring.servlet.multipart.max-file-size` | arquivo | DataSize | Container (Tomcat), antes do serviço |
| `spring.servlet.multipart.max-request-size` | arquivo | DataSize | Container (Tomcat), antes do serviço |
| `media.upload.max-size-bytes` | `MEDIA_MAX_SIZE_BYTES` | `long` | `MediaUploadProperties.maxSizeBytes` |
| `media.upload.types[].content-type` | arquivo | `String` | `MediaTypeRule.contentType` |
| `media.upload.types[].extensions` | arquivo | `Set<String>` | `MediaTypeRule.extensions` |

A configuração é vinculada por `@ConfigurationProperties` (`media/config/MediaUploadProperties.java` e `media/config/MediaTypeRule.java`), com o bean descoberto por `@ConfigurationPropertiesScan` em `CloneRedditApplication`. O `@Validated` faz a aplicação falhar no boot — não no primeiro upload — se `max-size-bytes` for `<= 0` ou se `types` vier vazio.

Os dois limites precisam concordar: `max-file-size` é o que o container impõe antes de qualquer código rodar, e `media.upload.max-size-bytes` é a checagem no serviço. Sem o primeiro, o padrão do Tomcat (1 MB) tornaria o limite de 5 MB inalcançável. `max-request-size` existe porque `POST /communities` envia ícone e banner na mesma requisição — 12 MB acomoda dois arquivos de 5 MB e o `data` em JSON.

Uma observação sobre o `.env`: `MEDIA_MAX_SIZE_BYTES` tem fallback (`5242880`) no próprio `${...:default}`, então não precisa ser exportada. As cinco variáveis `AWS_*` não têm fallback e são obrigatórias.

## 5. Fluxo de upload

`S3ServiceImpl.upload(MultipartFile file, String folder)` (`S3ServiceImpl.java:56-82`):

**0. Validação do arquivo** — antes de qualquer leitura de bytes, `mediaFileValidator.validateAndResolveExtension(file)` (`MediaFileValidator.java:19-38`) rejeita três coisas, cada uma com `ResourceInvalidException` (tratada pelo `GlobalExceptionHandler` como **422**):

| Checagem | Origem da informação |
|---|---|
| `file.getSize() > maxSizeBytes` | Cabeçalho do multipart |
| `file.getContentType()` não casa com nenhuma regra | Cabeçalho do multipart |
| Extensão não casa com a regra do `content-type` declarado (ou está ausente) | `file.getOriginalFilename()` |

A checagem é feita em **duas passadas** sobre as regras, e não sobre uma lista plana de extensões. Primeiro o `content-type` precisa existir em alguma regra; depois a extensão precisa estar **na mesma regra** que define aquele `content-type`. Isso rejeita a combinação contraditória — um arquivo `.jpg` declarado como `image/png` passa nas duas listas soltas, mas não na regra pareada — e mantém as mensagens de erro distinguíveis.

A mesma extensão pode aparecer em mais de uma regra de propósito: navegadores do mundo real declaram `image/jpg` (não-padrão) junto de um arquivo `.jpg`, e um invariante rígido de "uma extensão, um único content-type" rejeitaria upload legítimo. O teste `deveAceitarContentTypeNaoPadraoQueCompartilhaExtensao` fixa essa decisão.

O método devolve a extensão já normalizada em minúsculas, que é o que alimenta a `objectKey` — assim a lista de permissões tem uma única fonte de verdade e `S3ServiceImpl` não mantém uma segunda lógica de extração de extensão.

Duas consequências que valem registrar:

- **A allowlist de extensões é também a sanitização da `objectKey`.** O Spring não sanitiza o filename (`StandardMultipartFile.getOriginalFilename()` devolve o valor cru do `Content-Disposition`), e a chave é `pasta + "/" + uuid + extensão`. Como `/` nunca casa com uma extensão permitida, um filename como `icon.png/../../../evil` é rejeitado em vez de produzir uma chave que escapa do prefixo.
- **A validação não inspeciona os *magic bytes*.** O `content_type` continua sendo o declarado pelo cliente; cruzar tipo e extensão reduz o espaço de abuso, mas um JPEG renomeado de `.png` passa. Fechar isso exigiria leitura dos primeiros bytes do arquivo, o que está registrado como dívida em `README.md`.

**1. Leitura do arquivo para memória** — `MediaConverter.toUploadRequest` chama `file.getBytes()` e monta o record `MediaUploadRequest(content, contentType, folder)`. Uma `IOException` na leitura é reembalada como `UncheckedIOException("Failed to read uploaded file")` (`MediaConverter.java:15-21`).

**2. Montagem da `objectKey`** — a extensão já foi validada e normalizada no passo 0, e a chave é composta como:

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

Entre os passos 3 e 5 há o registro da compensação (passo 4b), detalhado na [seção 5.1](#51-compensacao-do-s3-quando-a-transacao-nao-confirma).

### 5.1 Compensação do S3 quando a transação não confirma

O `putObject` acontece sobre a rede e não participa da transação do banco. Se a transação for revertida, o `tb_media` some junto com ela — e a `objectKey`, que só existe naquela linha, também. Sobraria um objeto no bucket que **ninguém conseguiria localizar pelo banco**. A camada fecha essa janela em dois pontos distintos.

**4b-a. Falha entre o `putObject` e o `save`** — coberta por um `try/catch` em volta do `save` (`S3ServiceImpl.java:72-77`):

```java
Media media = mediaConverter.toEntity(request, bucketName, objectKey);
try {
    mediaRepository.save(media);
} catch (RuntimeException ex) {
    deleteObjectQuietly(media.getBucket(), media.getObjectKey());
    throw ex;
}
```

O `catch` é obrigatório aqui porque o `mediaId` nunca chega ao chamador: se `upload` lança, quem chamou não sabe que um objeto foi gravado. A única fonte da `objectKey` é o próprio `S3ServiceImpl`.

**4b-b. Falha depois do `save`, dentro da transação do chamador** — coberta por `registerRollbackOnRollback` (`S3ServiceImpl.java:84-114`). Quando existe uma transação ativa (o caso de `CommunityServiceImpl.create`, anotada com `@Transactional`), o upload registra uma `TransactionSynchronization` que, no `afterCompletion`, apaga o objeto:

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
            deleteObjectQuietly(media.getBucket(), media.getObjectKey());
            return;
        }
        if (status == STATUS_UNKNOWN) {
            log.atWarn() /* media.upload.rollback_unknown */ .log();
        }
    }
});
```

Três decisões desse código merecem justificativa:

- **`afterCompletion`, e não `afterCommit` ou `beforeCommit`.** `afterCommit` só dispara no caminho feliz, então não consegue expressar "desfaz". `beforeCommit` apagaria o objeto antes do resultado ser conhecido, e um commit que falhasse depois deixaria uma referência confirmada apontando para um objeto inexistente. `afterCompletion` é o ponto onde o desfecho já é certo — e é também o ponto onde **não se deve tocar no banco**, porque a transação já terminou. Por isso a única informação usada ali é a que está em memória na closure.
- **Ler `bucket` e `objectKey` da entidade, e não buscar por `mediaId`.** Depois de um rollback o registro em `tb_media` não existe mais, e uma chamada a `mediaRepository.findById` lançaria `ResourceNotFoundException` justamente no caminho que precisa consertar o S3. Por isso os valores são capturados do `Media` ainda em memória, na closure. Isso também explica por que a `objectKey` nunca aparece na `MediaResponse`: ela é interna por necessidade de desenho, não por esquecimento.
- **`STATUS_UNKNOWN` mantém o objeto.** Nos dois desfechos incertos o custo é assimétrico: apagar e o commit ter sido OK produz `tb_community` referenciando um objeto apagado (ícone quebrado, sem caminho de recuperação); manter e o rollback ter ocorrido produz apenas um órfão. Opta-se por manter e sinalizar em `media.upload.rollback_unknown`.

`deleteObjectQuietly` (`S3ServiceImpl.java:116-132`) **nunca relança**: como roda de dentro de `afterCompletion`, uma exceção ali escaparia pelo proxy transacional e mascararia a falha original — um `DataIntegrityViolationException` (409) viraria 500. Falha de delete fica registrada em `media.upload.rollback_failed` com o stack trace.

**O guarda `isSynchronizationActive()`** (`S3ServiceImpl.java:85-93`) cobre o caso de um upload feito **fora** de transação: sem ele, `registerSynchronization` lançaria `IllegalStateException`. Semanticamente, fora de transação o `save` do repositório já rodou e commitou na transação curta que o Spring Data abre, então não há o que reverter e a limpeza volta a ser responsabilidade de quem chamou — o que o log `DEBUG` `media.upload.no_active_transaction` registra.

A consequência arquitetural que vale registrar: essa compensação está na camada de mídia, e não no serviço que consome o upload. Ela é a única camada que sabe a `objectKey`, e por estar presa ao ciclo de vida da transação, **todo chamador futuro** (avatar, post com imagem) fica protegido sem precisar repetir a lista de arquivos a reverter.

## 6. Fluxo de leitura (URL pré-assinada)

`S3ServiceImpl.getUrl(UUID mediaId)` (`S3ServiceImpl.java:135-150`):

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

`CommunityServiceImpl` (`community/service/impl/CommunityServiceImpl.java:33-34, 63-72`) define as pastas por uso e por período:

```java
private static final DateTimeFormatter FOLDER_PERIOD_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy/MM");

private static String communityIconFolder() {
    return "communities/icons/" + LocalDateTime.now().format(FOLDER_PERIOD_FORMATTER);
}

private static String communityBannerFolder() {
    return "communities/banners/" + LocalDateTime.now().format(FOLDER_PERIOD_FORMATTER);
}
```

São métodos, e não constantes `static final`, para que o `yyyy/MM` seja resolvido a cada chamada. A versão anterior era `static final`, e o particionamento ficava congelado no carregamento da classe — a partição só mudaria junto com um restart da aplicação, jogando todos os uploads do período seguinte no prefixo do mês em que o processo subiu.

O serviço:

1. Valida nome e slug (`validateCommunityUniqueness`) e busca o owner **antes** de qualquer upload — se o nome já existir, nenhum objeto é gravado e a compensação do S3 não chega a ser acionada.
2. Chama `uploadIfPresent(iconFile, communityIconFolder())` e o equivalente para o banner; o método ignora partes ausentes ou vazias (`file == null || file.isEmpty()`) e não gera upload nesse caso.
3. Salva a comunidade com `iconMediaId` / `bannerMediaId` extraídos do `MediaResponse`.
4. Devolve `CommunityResponseDTO` com `icon` e `banner` do tipo `MediaResponse` — ou seja, `mediaId` + a URL pré-assinada de cada arquivo, pronta para o front consumir.

O método é anotado com `@Transactional`, e é essa transação que dá sentido à compensação descrita na [seção 5.1](#51-compensacao-do-s3-quando-a-transacao-nao-confirma): os dois `INSERT` (mídia e comunidade) são atômicos entre si, e o `deleteObject` acontece automaticamente no `afterCompletion` caso qualquer um deles falhe.

Os prefixos `communities/icons/` e `communities/banners/` funcionam como a organização lógica do bucket; o particionamento por `yyyy/MM` é o que a AWS usa como *prefix* para cobrança e políticas de ciclo de vida.

**Limite de tamanho por requisição:** `POST /communities` envia ícone e banner na mesma chamada, então o teto efetivo é o menor entre `spring.servlet.multipart.max-request-size` (12 MB) e duas vezes `media.upload.max-size-bytes` (5 MB cada). Os dois arquivos podem ocupar o request inteiro se ambos estiver no limite.

## 9. Configuração do lado da AWS

Esta parte é infraestrutura — não está no repositório — mas é o que a camada pressupõe existir.

### 9.1 Criação do bucket

```bash
aws s3api create-bucket --bucket <NOME_DO_BUCKET> --region <REGIAO>
```

O nome do bucket deve ser o mesmo valor de `AWS_S3_BUCKET_NAME`, e a região deve ser a mesma de `AWS_REGION` — o `S3Client` assina a requisição para a região configurada.

Para subir o mesmo código contra um S3 compatível local (MinIO, conforme a decisão registrada no `README.md`), o `docker-compose.yml` da raiz hoje sobe **apenas o PostgreSQL**, e o `StorageConfig` não define `endpointOverride` nem *path-style access* — a configuração de endpoint é um passo adicional a fazer no código para esse cenário.

### 9.2 Credenciais e IAM

A aplicação usa uma access key de longa duração (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`). As operações que o código executa no bucket são `PutObject` (upload), leitura via URL assinada e `DeleteObject` (a compensação da [seção 5.1](#51-compensacao-do-s3-quando-a-transacao-nao-confirma)) — o que é autorizado por `s3:GetObject` e `s3:DeleteObject` na identidade que assina. A policy mínima equivalente:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::<NOME_DO_BUCKET>/*"
    }
  ]
}
```

> **`s3:DeleteObject` é obrigatório desde a introdução da compensação.** Sem ele, todo rollback termina em `AccessDenied`: o `deleteObjectQuietly` engole a exceção, registra `media.upload.rollback_failed` e o objeto vira órfão sem que a resposta ao cliente indique qualquer problema — ou seja, a falha é silenciosa do lado de fora e só aparece no log. Se a identidade for criada antes desta mudança, a policy precisa ser atualizada.

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
- **O `deleteObject` existe só como compensação.** Não é exposto em `MediaServiceI` nem em rota: não existe como apagar uma mídia pela API, e linhas em `tb_media` e objetos no bucket só crescem. A operação é interna e não deixa metadados de auditoria (a remoção só aparece em `media.upload.rollback_failed`, quando falha).
- **A validação confia no `content_type` declarado.** O tamanho e a combinação `content-type`/extensão são verificados antes de ler os bytes (`MediaFileValidator`), mas não há inspeção de *magic bytes*. Como a checagem é de consistência e não de conteúdo, um JPEG renomeado de `.png` **e** declarado como `image/png` continua passando, e o `tb_media.content_type` registra `image/png`. Fechar isso exigiria ler os primeiros bytes do arquivo e comparar com o que foi declarado.
- **A compensação é síncrona e sem retry.** O `deleteObject` roda no mesmo thread da requisição, dentro do `afterCompletion`, e não há retentativa. Falha de delete fica em `media.upload.rollback_failed` e o objeto vira órfão.
- **A janela "JVM morre entre `putObject` e commit" continua aberta.** Nem a transação do banco nem a compensação resolvem esse caso; a correção é um job de varredura (outbox pattern) que compare o bucket contra `tb_media`.
- **Cobertura de teste:** `media/service/impl/S3ServiceImplTest` (9 casos: upload bem-sucedido, propagação de falha de validação, delete quando o `save` falha, rollback confirmado/não confirmado/desconhecido, delete que falha e não mascara a exceção original) e `media/validator/MediaFileValidatorTest` (11 casos, incluindo extensão com barra). `community/service/impl/CommunityServiceImplTest` cobre o `create` do lado consumidor, e `shared/exception/GlobalExceptionHandlerTest` cobre o 413 do limite de upload.
- **Os testes de integração sobem o contexto e exigem ambiente.** `CloneRedditApplicationTests`, `AuthenticationFlowIntegrationTest` e `SecurityConfigIntegrationTest` usam `@ActiveProfiles("test")`, e `src/test/resources/application-test.yaml` sobrescreve apenas as chaves JWT — as variáveis `AWS_*` precisam estar presentes para que o `StorageConfig` seja construído. Os testes unitários não sobem o contexto e rodam sem elas.
- **Sem cache de URL.** `getUrl` assina uma nova URL a cada chamada; a validade é a de `AWS_S3_PRESIGNED_URL_SECONDS` a partir do momento da geração.
