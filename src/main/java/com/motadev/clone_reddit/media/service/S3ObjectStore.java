package com.motadev.clone_reddit.media.service;

import com.motadev.clone_reddit.media.logging.MediaEventLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.List;

@Component
public class S3ObjectStore {

    private final S3Client s3Client;
    private final MediaEventLog mediaEventLog;
    private final String bucketName;

    public S3ObjectStore(S3Client s3Client,
                         MediaEventLog mediaEventLog,
                         @Value("${aws.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.mediaEventLog = mediaEventLog;
        this.bucketName = bucketName;
    }

    public ObjectPage listObjects(String continuationToken, int maxKeys) {
        ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .maxKeys(maxKeys);
        if (continuationToken != null) {
            request.continuationToken(continuationToken);
        }

        ListObjectsV2Response response = s3Client.listObjectsV2(request.build());
        List<ListedObject> objects = response.contents().stream()
                .map(this::toListedObject)
                .toList();
        String nextToken = Boolean.TRUE.equals(response.isTruncated())
                ? response.nextContinuationToken()
                : null;
        return new ObjectPage(objects, nextToken);
    }

    public boolean deleteObjectQuietly(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
            return true;
        } catch (RuntimeException ex) {
            mediaEventLog.orphanSweepDeleteFailed(bucketName, objectKey, ex);
            return false;
        }
    }

    public String bucketName() {
        return bucketName;
    }

    private ListedObject toListedObject(S3Object object) {
        Instant lastModified = object.lastModified() != null
                ? object.lastModified()
                : Instant.EPOCH;
        return new ListedObject(object.key(), lastModified);
    }

    public record ListedObject(String key, Instant lastModified) {
    }

    public record ObjectPage(List<ListedObject> objects, String nextContinuationToken) {
    }
}
