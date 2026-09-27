package com.anssa8bu.ssafy_gpu.preprocessing.storage;

import com.anssa8bu.ssafy_gpu.preprocessing.config.UmiPreprocessorProperties;
import com.anssa8bu.ssafy_gpu.training.storage.EpisodeArchiveDownloader.LocalEpisode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Component;

@Component
public class SafeEpisodeArchiveExtractor {
  private static final Set<String> REQUIRED =
      Set.of(
          "manifest.json",
          "frames.csv",
          "encoded.csv",
          "accelerometer.csv",
          "gyroscope.csv",
          "video.mp4");
  private final UmiPreprocessorProperties properties;

  public SafeEpisodeArchiveExtractor(UmiPreprocessorProperties properties) {
    this.properties = properties;
  }

  public void extract(LocalEpisode episode, Path destination) {
    try {
      if (isComplete(destination)) return;
      Path parent = destination.getParent().toAbsolutePath().normalize();
      Files.createDirectories(parent);
      Path temporary = parent.resolve(destination.getFileName() + ".extracting").normalize();
      deleteTree(temporary);
      Files.createDirectories(temporary);
      Set<String> names = new HashSet<>();
      long total = 0;
      int count = 0;
      try (ZipInputStream input = new ZipInputStream(Files.newInputStream(episode.path()))) {
        ZipEntry entry;
        byte[] buffer = new byte[64 * 1024];
        while ((entry = input.getNextEntry()) != null) {
          if (++count > properties.maxArchiveEntries())
            throw new IllegalStateException("ZIP 항목 수가 제한을 초과했습니다.");
          String name = entry.getName();
          if (entry.isDirectory()
              || name.contains("/")
              || name.contains("\\")
              || !REQUIRED.contains(name)
              || !names.add(name)) throw new IllegalStateException("허용되지 않은 ZIP 항목입니다: " + name);
          Path output = temporary.resolve(name).normalize();
          if (!output.getParent().equals(temporary))
            throw new IllegalStateException("ZIP 경로 이탈이 감지되었습니다.");
          try (OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
              if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Episode ZIP 해제가 취소되었습니다.");
              total += read;
              if (total > properties.maxExtractedBytes())
                throw new IllegalStateException("ZIP 해제 크기가 제한을 초과했습니다.");
              out.write(buffer, 0, read);
            }
          }
        }
      }
      if (!names.equals(REQUIRED))
        throw new IllegalStateException("Episode ZIP 필수 파일 구성이 일치하지 않습니다.");
      deleteTree(destination);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, destination);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Episode ZIP 안전 압축 해제에 실패했습니다.", e);
    }
  }

  private boolean isComplete(Path path) {
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
      return false;
    return REQUIRED.stream()
        .allMatch(name -> Files.isRegularFile(path.resolve(name), LinkOption.NOFOLLOW_LINKS));
  }

  private void deleteTree(Path root) throws IOException {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }
}
