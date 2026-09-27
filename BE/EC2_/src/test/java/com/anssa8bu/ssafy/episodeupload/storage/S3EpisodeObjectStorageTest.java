package com.anssa8bu.ssafy.episodeupload.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3EpisodeObjectStorageTest {

  @Test
  void createsPresignedPutUrlWithRequiredSignatureAndChecksum() {
    var credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    try (S3Presigner presigner =
            S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .build();
        S3Client client =
            S3Client.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .build()) {
      var storage = new S3EpisodeObjectStorage(presigner, client);
      String sha256 = "a".repeat(64);

      EpisodeObjectStorage.PresignedUpload result =
          storage.createUpload(
              "umi-episode-test",
              "episodes/550e8400-e29b-41d4-a716-446655440000/20260914T120000Z/"
                  + "4c7f2cc4-31de-4bd5-a6a9-199c1fdabf5f.zip",
              10L * 1024 * 1024,
              sha256,
              Duration.ofMinutes(15));

      String decodedUrl = URLDecoder.decode(result.url(), StandardCharsets.UTF_8);
      assertThat(result.url()).startsWith("https://umi-episode-test.s3.ap-northeast-2.amazonaws.com/");
      assertThat(decodedUrl)
          .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
          .contains("X-Amz-Credential=test-access-key/")
          .contains("X-Amz-Expires=900")
          .contains("X-Amz-Signature=")
          .contains("X-Amz-SignedHeaders=")
          .contains("content-length")
          .contains("content-type")
          .contains("if-none-match")
          .contains("x-amz-checksum-sha256");
      assertThat(result.checksumBase64())
          .isEqualTo(
              Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256)));
    }
  }
}
