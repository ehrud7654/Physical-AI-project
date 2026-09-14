package com.anssa8bu.ssafy.episodeupload.storage;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Component
public class S3EpisodeObjectStorage implements EpisodeObjectStorage {
  private final S3Presigner presigner;
  private final S3Client client;
  private final Clock clock = Clock.systemUTC();

  public S3EpisodeObjectStorage(S3Presigner presigner, S3Client client) {
    this.presigner = presigner; this.client = client;
  }

  @Override
  public PresignedUpload createUpload(String bucket, String key, long sizeBytes, String sha256, Duration ttl) {
    String checksum = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256));
    PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key)
        .contentType("application/zip").contentLength(sizeBytes).checksumSHA256(checksum)
        .ifNoneMatch("*").build();
    PresignedPutObjectRequest signed = presigner.presignPutObject(
        PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build());
    return new PresignedUpload(signed.url().toString(), checksum, clock.instant().plus(ttl));
  }

  @Override
  public StoredObject inspect(String bucket, String key) {
    HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
        .bucket(bucket).key(key).checksumMode(ChecksumMode.ENABLED).build());
    return new StoredObject(response.contentLength(), response.checksumSHA256());
  }

  @Override
  public Optional<StoredObject> inspectIfExists(String bucket, String key) {
    try {
      return Optional.of(inspect(bucket, key));
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) return Optional.empty();
      throw exception;
    }
  }

  @Override
  public InputStream open(String bucket, String key) {
    return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
  }

  @Override
  public void delete(String bucket, String key) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }
}
