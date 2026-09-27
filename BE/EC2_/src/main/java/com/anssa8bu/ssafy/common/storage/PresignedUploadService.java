package com.anssa8bu.ssafy.common.storage;

import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class PresignedUploadService {
  private final ObjectStoragePort storage;
  public PresignedUploadService(ObjectStoragePort storage) { this.storage = storage; }
  public ObjectStoragePort.PresignedObject issue(String bucket, String key, String contentType,
      long sizeBytes, String sha256, Duration ttl) {
    return storage.presignPut(bucket, key, contentType, sizeBytes, sha256, ttl);
  }
}
