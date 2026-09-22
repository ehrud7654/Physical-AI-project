package com.anssa8bu.ssafy_gpu.checkpoint.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckpointPackageValidatorTest {
  private static final List<String> FILES =
      List.of(
          "encoder.pt",
          "denoiser.pt",
          "metadata.json",
          "dataset.report.json",
          "config.yaml",
          "reference.npz",
          "pc_check.json",
          "diffusion.manifest.json");
  private final CheckpointPackageValidator validator = new CheckpointPackageValidator();
  @TempDir Path directory;

  @Test
  void acceptsExactPackageContract() throws IOException {
    Path packageFile = archive("model.tgz", FILES);
    validator.validate(packageFile);
  }

  @Test
  void rejectsMissingFile() throws IOException {
    Path packageFile = archive("model.tgz", FILES.subList(1, FILES.size()));
    assertThatThrownBy(() -> validator.validate(packageFile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("필수 파일");
  }

  private Path archive(String filename, List<String> names) throws IOException {
    Path target = directory.resolve(filename);
    try (var output = new TarArchiveOutputStream(Files.newOutputStream(target))) {
      for (String name : names) {
        byte[] content = "test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
      }
    }
    return target;
  }
}
