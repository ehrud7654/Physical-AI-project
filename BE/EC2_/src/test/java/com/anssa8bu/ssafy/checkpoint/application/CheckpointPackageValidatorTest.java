package com.anssa8bu.ssafy.checkpoint.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.anssa8bu.ssafy.common.storage.ObjectStoragePort;
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
  @TempDir Path directory;

  @Test
  void acceptsDownloadedPackage() throws IOException {
    Path source = archive(FILES);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    doAnswer(
            invocation -> {
              Path destination = invocation.getArgument(2, Path.class);
              Files.copy(source, destination);
              return null;
            })
        .when(storage)
        .download(anyString(), anyString(), any(Path.class));

    new CheckpointPackageValidator(storage).validate("bucket", "model.tgz");
  }

  @Test
  void rejectsPackageWithExtraFile() throws IOException {
    Path source = archive(
        java.util.stream.Stream.concat(FILES.stream(), java.util.stream.Stream.of("extra.bin"))
            .toList());
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    doAnswer(
            invocation -> {
              Path destination = invocation.getArgument(2, Path.class);
              Files.copy(source, destination);
              return null;
            })
        .when(storage)
        .download(anyString(), anyString(), any(Path.class));

    assertThatThrownBy(() -> new CheckpointPackageValidator(storage).validate("bucket", "model.tgz"))
        .hasMessageContaining("필수 파일");
  }

  private Path archive(List<String> names) throws IOException {
    Path target = directory.resolve(java.util.UUID.randomUUID() + ".tgz");
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
