package com.anssa8bu.ssafy.episodevalidation.application;

import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadStatus;
import com.anssa8bu.ssafy.episodeupload.repository.EpisodeUploadSessionRepository;
import com.anssa8bu.ssafy.episodeupload.storage.EpisodeObjectStorage;
import com.anssa8bu.ssafy.episodeupload.storage.EpisodeStorageProperties;
import com.anssa8bu.ssafy.episodevalidation.dto.EpisodeValidationResult;
import com.anssa8bu.ssafy.episodevalidation.exception.EpisodeArchiveValidationException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EpisodeValidationService {
  private static final Logger log = LoggerFactory.getLogger(EpisodeValidationService.class);
  private final EpisodeUploadSessionRepository sessions;
  private final EpisodeObjectStorage storage;
  private final EpisodeStorageProperties properties;
  private final EpisodeArchiveValidator validator;
  private final EpisodeValidationResultWriter writer;

  public EpisodeValidationService(
      EpisodeUploadSessionRepository sessions,
      EpisodeObjectStorage storage,
      EpisodeStorageProperties properties,
      EpisodeArchiveValidator validator,
      EpisodeValidationResultWriter writer) {
    this.sessions = sessions;
    this.storage = storage;
    this.properties = properties;
    this.validator = validator;
    this.writer = writer;
  }

  public void validate(UUID uploadSessionId) {
    EpisodeUploadSession session = sessions.findById(uploadSessionId).orElse(null);
    if (session == null || session.getStatus() != EpisodeUploadStatus.VALIDATING) return;
    try (InputStream source = storage.open(session.getBucketName(), session.getObjectKey())) {
      EpisodeValidationResult result =
          validator.validate(source, session, properties.maxUploadBytes());
      writer.ready(uploadSessionId, result);
    } catch (EpisodeArchiveValidationException exception) {
      writer.invalid(uploadSessionId, exception.getCode(), exception.getMessage());
    } catch (Exception exception) {
      log.error("Episode validation processing failed. uploadSessionId={}", uploadSessionId, exception);
      writer.failed(uploadSessionId, "VALIDATION_PROCESSING_FAILED", "Episode 검증 처리 중 오류가 발생했습니다.");
    }
  }
}
