package com.anssa8bu.ssafy.common.storage;

import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class PresignedDownloadService {
  private final ObjectStoragePort storage;
  public PresignedDownloadService(ObjectStoragePort storage) { this.storage = storage; }
  public ObjectStoragePort.PresignedObject issue(String bucket, String key, Duration ttl) {
    return storage.presignGet(bucket, key, ttl);
  }
}
