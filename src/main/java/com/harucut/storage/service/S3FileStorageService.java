package com.harucut.storage.service;

import com.harucut.storage.config.AwsProperties;
import com.harucut.storage.dto.PresignedUploadResponse;
import com.harucut.storage.enums.ContentType;
import com.harucut.storage.enums.UploadType;
import com.harucut.storage.strategy.UploadPathStrategy;
import com.harucut.storage.util.ContentDispositions;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class S3FileStorageService implements FileStorageService {

    private static final Duration EXPIRY = Duration.ofHours(24);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucket;
    private final Map<UploadType, UploadPathStrategy> strategies;

    public S3FileStorageService(S3Presigner s3Presigner, S3Client s3Client,
                              AwsProperties awsProperties, List<UploadPathStrategy> strategies) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucket = awsProperties.s3().bucket();
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(UploadPathStrategy::getUploadType, Function.identity()));

        for (UploadType type : UploadType.values()) {
            if (!this.strategies.containsKey(type)) {
                throw new IllegalStateException("UploadPathStrategy 미등록: " + type);
            }
        }
    }

    @Override
    public PresignedUploadResponse generatePresignedUploadUrl(UploadType type, String filename, ContentType contentType, long fileSize, String publicId) {
        String extension = contentType.validateExtension(extractExtension(filename));
        String key = strategies.get(type).generateKey(publicId, extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType.getMimeType())
                .contentLength(fileSize)
                .build();

        String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(EXPIRY)
                        .putObjectRequest(putObjectRequest)
                        .build())
                .url().toString();

        return new PresignedUploadResponse(key, url, contentType.getMimeType(), EXPIRY);
    }

    @Override
    public String generatePresignedGetUrl(String key) {
        return presignGet(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public String generatePresignedDownloadUrl(String key, String downloadFileName) {
        String filename = (downloadFileName == null || downloadFileName.isBlank())
                ? filenameFromKey(key)
                : downloadFileName;

        return presignGet(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition(ContentDispositions.attachment(filename))
                .responseContentType("application/octet-stream")
                .build());
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public byte[] downloadBytes(String key) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void uploadBytes(String key, byte[] bytes, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    private String presignGet(GetObjectRequest getObjectRequest) {
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(EXPIRY)
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url().toString();
    }

    private static String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return (idx == -1 || idx == filename.length() - 1) ? "" : filename.substring(idx + 1);
    }

    private static String filenameFromKey(String key) {
        int idx = key.lastIndexOf('/');
        return (idx >= 0 && idx < key.length() - 1) ? key.substring(idx + 1) : key;
    }
}
