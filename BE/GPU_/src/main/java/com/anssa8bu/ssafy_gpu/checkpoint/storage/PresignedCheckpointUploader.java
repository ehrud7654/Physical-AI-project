package com.anssa8bu.ssafy_gpu.checkpoint.storage;

import com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.UploadSessionResponse;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PresignedCheckpointUploader {
  private final HttpClient client;

  public PresignedCheckpointUploader(HttpClient presignedHttpClient) {
    this.client = presignedHttpClient;
  }

  public void upload(UploadSessionResponse session, Path file, String sha256) {
    if (session.expiresAt().isBefore(Instant.now()))
      throw new IllegalStateException("Presigned URL이 만료되었습니다.");
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(session.uploadUrl()))
              .PUT(HttpRequest.BodyPublishers.ofFile(file));
      session
          .headers()
          .forEach(
              (name, value) -> {
                if (!name.equalsIgnoreCase("content-length")) builder.header(name, value);
              });
      if (session.headers().keySet().stream()
          .noneMatch(k -> k.equalsIgnoreCase("x-amz-checksum-sha256"))) {
        String checksum = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256));
        builder.header("x-amz-checksum-sha256", checksum);
      }
      HttpResponse<Void> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("S3 CKPT 업로드 실패: HTTP " + response.statusCode());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("S3 업로드가 중단되었습니다.", e);
    } catch (Exception e) {
      if (e instanceof IllegalStateException state) throw state;
      throw new IllegalStateException("S3 CKPT 업로드에 실패했습니다.", e);
    }
  }
}
