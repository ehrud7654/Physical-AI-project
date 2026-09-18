package com.anssa8bu.ssafy.common.storage;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class ObjectVerificationService {
  private final ObjectStoragePort storage;
  public ObjectVerificationService(ObjectStoragePort storage) { this.storage = storage; }

  public void verify(String bucket, String key, long expectedBytes, String expectedSha256) {
    var actual = storage.inspect(bucket, key);
    if (actual.sizeBytes() != expectedBytes)
      throw new BusinessConflictException("OBJECT_SIZE_MISMATCH", "업로드된 객체 크기가 예약값과 다릅니다.");
    String expected = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(expectedSha256));
    if (actual.checksumSha256Base64() == null || !expected.equals(actual.checksumSha256Base64()))
      throw new BusinessConflictException("OBJECT_CHECKSUM_MISMATCH", "업로드된 객체 체크섬이 예약값과 다릅니다.");
  }

  public void verifyCheckpoint(String bucket, String key, long expectedBytes, String expectedSha256) {
    verify(bucket,key,expectedBytes,expectedSha256);
    byte[] prefix=storage.readPrefix(bucket,key,4);
    boolean zip=prefix.length>=2 && prefix[0]=='P' && prefix[1]=='K';
    boolean pickle=prefix.length>=2 && (prefix[0]&0xff)==0x80;
    if (!zip && !pickle)
      throw new BusinessConflictException("INVALID_CHECKPOINT_STRUCTURE", "지원하는 PyTorch CKPT 구조가 아닙니다.");
  }
}
