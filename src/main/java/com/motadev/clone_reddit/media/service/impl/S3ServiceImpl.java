package com.motadev.clone_reddit.media.service.impl;

import com.motadev.clone_reddit.media.converter.MediaConverter;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.entity.Media;
import com.motadev.clone_reddit.media.repository.MediaRepository;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
public class S3ServiceImpl implements MediaServiceI {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MediaRepository mediaRepository;
    private final MediaConverter mediaConverter;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public S3ServiceImpl(S3Client s3Client,
                         S3Presigner s3Presigner,
                         MediaRepository mediaRepository,
                         MediaConverter mediaConverter) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.mediaRepository = mediaRepository;
        this.mediaConverter = mediaConverter;
    }

    @Override
    public MediaResponse upload(MultipartFile file, String folder) {
        // Todo: Adicionar validações de duplicidade de nome e slug da comunidade
        // Todo: Adicionar validações de tamanhos de arquivos / extensões permitidas para ícone e banner

        var request = mediaConverter.toUploadRequest(file, folder);
        String objectKey = request.folder() + "/" + UUID.randomUUID();

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(request.contentType())
                        .build(),
                RequestBody.fromBytes(request.content())
        );

        Media media = mediaConverter.toEntity(request, bucketName, objectKey);
        mediaRepository.save(media);

        return mediaConverter.toResponse(media, getUrl(media.getMediaId()));
    }

    @Override
    public String getUrl(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
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
