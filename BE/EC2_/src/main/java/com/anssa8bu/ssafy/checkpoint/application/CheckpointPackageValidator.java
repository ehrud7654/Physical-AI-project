package com.anssa8bu.ssafy.checkpoint.application;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.storage.ObjectStoragePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;

@Component
public class CheckpointPackageValidator {
  private static final Set<String> REQUIRED =
      Set.of(
          "encoder.pt",
          "denoiser.pt",
          "metadata.json",
          "dataset.report.json",
          "config.yaml",
          "reference.npz",
          "pc_check.json");
  private final ObjectStoragePort storage;

  public CheckpointPackageValidator(ObjectStoragePort storage) {
    this.storage = storage;
  }

  public void validate(String bucket, String key) {
    Path temporaryDirectory = null;
    Path temporary = null;
    try {
      temporaryDirectory = Files.createTempDirectory("model-package-");
      temporary = temporaryDirectory.resolve("model.tgz");
      storage.download(bucket, key, temporary);
      validateArchive(temporary);
    } catch (IOException exception) {
      throw invalid("모델 패키지 검사 중 파일 처리에 실패했습니다.", exception);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
        }
      }
      if (temporaryDirectory != null) {
        try {
          Files.deleteIfExists(temporaryDirectory);
        } catch (IOException ignored) {
        }
      }
    }
  }

  private void validateArchive(Path packageFile) {
    Set<String> names = new HashSet<>();
    int manifestCount = 0;
    try (var input = Files.newInputStream(packageFile);
        var archive = new TarArchiveInputStream(input)) {
      TarArchiveEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        String name = entry.getName();
        if (!entry.isFile() || name.isBlank() || name.contains("/") || name.contains("\\"))
          throw invalid("모델 패키지에는 루트의 일반 파일만 포함할 수 있습니다.");
        if (!names.add(name)) throw invalid("모델 패키지에 중복 파일명이 있습니다.");
        if (name.endsWith(".manifest.json")) manifestCount++;
      }
    } catch (IOException exception) {
      throw invalid("모델 패키지는 압축하지 않은 TAR 형식의 .tgz여야 합니다.", exception);
    }
    Set<String> nonManifest = new HashSet<>(names);
    nonManifest.removeIf(name -> name.endsWith(".manifest.json"));
    if (names.size() != REQUIRED.size() + 1
        || manifestCount != 1
        || !nonManifest.equals(REQUIRED))
      throw invalid("모델 패키지는 필수 파일 7개와 *.manifest.json 1개만 포함해야 합니다.");
  }

  private static BusinessConflictException invalid(String message) {
    return new BusinessConflictException("INVALID_CHECKPOINT_PACKAGE", message);
  }

  private static BusinessConflictException invalid(String message, Throwable cause) {
    var exception = new BusinessConflictException("INVALID_CHECKPOINT_PACKAGE", message);
    exception.initCause(cause);
    return exception;
  }
}
