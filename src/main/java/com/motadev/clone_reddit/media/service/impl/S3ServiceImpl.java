package com.motadev.clone_reddit.media.service.impl;

import com.motadev.clone_reddit.media.converter.MediaConverter;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.entity.Media;
import com.motadev.clone_reddit.media.repository.MediaRepository;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.media.validator.MediaFileValidator;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class S3ServiceImpl implements MediaServiceI {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MediaRepository mediaRepository;
    private final MediaConverter mediaConverter;
    private final MediaFileValidator mediaFileValidator;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiration-seconds:3600}")
    private long presignedUrlExpirationSeconds;

    public S3ServiceImpl(S3Client s3Client,
                         S3Presigner s3Presigner,
                         MediaRepository mediaRepository,
                         MediaConverter mediaConverter,
                         MediaFileValidator mediaFileValidator) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.mediaRepository = mediaRepository;
        this.mediaConverter = mediaConverter;
        this.mediaFileValidator = mediaFileValidator;
    }

    @Override
    public MediaResponse upload(MultipartFile file, String folder) {
        String extension = mediaFileValidator.validateAndResolveExtension(file);

        var request = mediaConverter.toUploadRequest(file, folder);
        String objectKey = request.folder() + "/" + UUID.randomUUID() + extension;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(request.contentType())
                        .build(),
                RequestBody.fromBytes(request.content())
        );

        Media media = mediaConverter.toEntity(request, bucketName, objectKey);
        try {
            mediaRepository.save(media);
        } catch (RuntimeException ex) {
            deleteObjectQuietly(media.getBucket(), media.getObjectKey());
            throw ex;
        }

        registerRollbackOnRollback(media);

        return mediaConverter.toResponse(media, getUrl(media.getMediaId()));
    }

    private void registerRollbackOnRollback(Media media) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.atDebug()
                    .addKeyValue("event", "media.upload.no_active_transaction")
                    .addKeyValue("mediaId", media.getMediaId())
                    .addKeyValue("objectKey", media.getObjectKey())
                    .setMessage("Upload performed outside a transaction; S3 rollback is the caller's responsibility.")
                    .log();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteObjectQuietly(media.getBucket(), media.getObjectKey());
                    return;
                }

                if (status == STATUS_UNKNOWN) {
                    log.atWarn()
                            .addKeyValue("event", "media.upload.rollback_unknown")
                            .addKeyValue("mediaId", media.getMediaId())
                            .addKeyValue("objectKey", media.getObjectKey())
                            .setMessage("Transaction outcome is unknown; the S3 object was kept to avoid "
                                    + "breaking a reference that may have been committed.")
                            .log();
                }
            }
        });
    }

    private void deleteObjectQuietly(String bucket, String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());

        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "media.upload.rollback_failed")
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("objectKey", objectKey)
                    .setMessage("Failed to delete the S3 object during upload rollback; the object may be orphaned.")
                    .log();
        }
    }

    @Override
    public String getUrl(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpirationSeconds))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(media.getBucket())
                        .key(media.getObjectKey())
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}
