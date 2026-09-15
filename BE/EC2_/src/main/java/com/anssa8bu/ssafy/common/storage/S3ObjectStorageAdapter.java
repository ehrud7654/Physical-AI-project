package com.anssa8bu.ssafy.common.storage;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

@Component
public class S3ObjectStorageAdapter implements ObjectStoragePort {
  private final S3Presigner presigner;
  private final S3Client client;
  private final Clock clock;

  public S3ObjectStorageAdapter(S3Presigner presigner, S3Client client, Clock clock) {
    this.presigner = presigner;
    this.client = client;
    this.clock = clock;
  }

  @Override
  public PresignedObject presignPut(String bucket, String key, String contentType, long sizeBytes,
      String sha256, Duration ttl) {
    String checksum = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256));
    PutObjectRequest object = PutObjectRequest.builder().bucket(bucket).key(key)
        .contentType(contentType).contentLength(sizeBytes).checksumSHA256(checksum)
        .ifNoneMatch("*").build();
    var request = PutObjectPresignRequest.builder().signatureDuration(ttl)
        .putObjectRequest(object).build();
    return new PresignedObject(presigner.presignPutObject(request).url().toString(),
        clock.instant().plus(ttl));
  }

  @Override
  public PresignedObject presignGet(String bucket, String key, Duration ttl) {
    var object = GetObjectRequest.builder().bucket(bucket).key(key).build();
    var request = GetObjectPresignRequest.builder().signatureDuration(ttl)
        .getObjectRequest(object).build();
    return new PresignedObject(presigner.presignGetObject(request).url().toString(),
        clock.instant().plus(ttl));
  }

  @Override
  public StoredObject inspect(String bucket, String key) {
    HeadObjectResponse response = client.headObject(HeadObjectRequest.builder().bucket(bucket)
        .key(key).checksumMode(ChecksumMode.ENABLED).build());
    return new StoredObject(response.contentLength(), response.checksumSHA256());
  }
}
