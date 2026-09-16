package com.anssa8bu.ssafy_gpu.common.file;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class FileHashCalculator {
  public String sha256(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[1024 * 1024];
      for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception e) {
      throw new IllegalStateException("파일 SHA-256 계산에 실패했습니다.", e);
    }
  }
}
