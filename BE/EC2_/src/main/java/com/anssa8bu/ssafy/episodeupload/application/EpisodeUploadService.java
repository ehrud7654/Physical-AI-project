package com.anssa8bu.ssafy.episodeupload.application;

import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.episode.entity.Episode;
import com.anssa8bu.ssafy.episode.repository.EpisodeRepository;
import com.anssa8bu.ssafy.episodeupload.dto.*;
import com.anssa8bu.ssafy.episodeupload.entity.*;
import com.anssa8bu.ssafy.episodeupload.exception.EpisodeUploadException;
import com.anssa8bu.ssafy.episodeupload.repository.EpisodeUploadSessionRepository;
import com.anssa8bu.ssafy.episodeupload.storage.*;
import com.anssa8bu.ssafy.episodevalidation.application.EpisodeValidationRequested;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EpisodeUploadService {
  private static final DateTimeFormatter KEY_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private final EpisodeUploadSessionRepository sessions;
  private final DeviceRepository devices;
  private final EpisodeRepository episodes;
  private final SkillRepository skills;
  private final EpisodeObjectStorage storage;
  private final EpisodeStorageProperties properties;
  private final IdempotencyGuard idempotency;
  private final ApplicationEventPublisher events;

  public EpisodeUploadService(EpisodeUploadSessionRepository sessions,
      DeviceRepository devices, EpisodeRepository episodes, SkillRepository skills, EpisodeObjectStorage storage,
      EpisodeStorageProperties properties, IdempotencyGuard idempotency, ApplicationEventPublisher events) {
    this.sessions = sessions; this.devices = devices; this.episodes = episodes;
    this.skills = skills;
    this.storage = storage; this.properties = properties; this.idempotency = idempotency;
    this.events = events;
  }

  @Transactional
  public EpisodeUploadSessionResponse create(UUID ownerId, UUID deviceId,
      String key, EpisodeUploadSessionRequest request) {
    validateKey(key);
    String path = "/api/v1/episode/data-upload-request";
    IdempotencyGuard.Claim claim = idempotency.claim("DEVICE", deviceId, "POST", path, key, request);
    if (claim.existingResourceId() != null) return signedResponse(sessions.findById(claim.existingResourceId())
        .orElseThrow(() -> notFound("업로드 세션을 찾을 수 없습니다.")));
    validateRequest(ownerId, deviceId, request);
    Optional<EpisodeUploadSession> prior = sessions.findByOwnerIdAndUmiDeviceIdAndSessionId(
        ownerId, deviceId, request.sessionId());
    if (prior.isPresent()) {
      validateReservation(prior.get(), request);
      if (prior.get().getStatus() != EpisodeUploadStatus.UPLOAD_PENDING) {
        throw new EpisodeUploadException(
            HttpStatus.CONFLICT,
            "STATE_CONFLICT",
            "종료된 sessionId로 업로드 세션을 다시 발급할 수 없습니다.");
      }
      prior.get().renew(Instant.now().plus(properties.presignTtl()));
      idempotency.complete("DEVICE", deviceId, "POST", path, claim, 201,
          "EPISODE_UPLOAD_SESSION", prior.get().getId());
      return signedResponse(prior.get());
    }
    UUID uploadId = UUID.randomUUID();
    EpisodeUploadSession session = new EpisodeUploadSession(uploadId, UUID.randomUUID(), null,
        ownerId, deviceId, request.sessionId(), request.sizeBytes(), request.sha256(),
        metadataMap(request.metadata()), summaryMap(request.summary()), properties.bucket(),
        objectKey(ownerId, request.metadata().endedAt(), request.sessionId()),
        Instant.now().plus(properties.presignTtl()));
    sessions.save(session);
    idempotency.complete("DEVICE", deviceId, "POST", path, claim, 201,
        "EPISODE_UPLOAD_SESSION", uploadId);
    return signedResponse(session);
  }

  @Transactional
  public EpisodeUploadCompleteResponse complete(UUID ownerId, UUID deviceId,
      UUID uploadId, String key, EpisodeUploadCompleteRequest request) {
    validateKey(key);
    String path = "/api/v1/episode/data-upload-request/" + uploadId + "/complete";
    IdempotencyGuard.Claim claim = idempotency.claim("DEVICE", deviceId, "POST", path, key, request);
    EpisodeUploadSession session = sessions.findForUpdateById(uploadId)
        .filter(s -> s.getOwnerId().equals(ownerId) && s.getUmiDeviceId().equals(deviceId))
        .orElseThrow(() -> notFound("업로드 세션을 찾을 수 없습니다."));
    validateCompletion(session, request);
    if (session.getStatus() == EpisodeUploadStatus.FAILED
        && "VALIDATION_PROCESSING_FAILED".equals(session.getValidationErrorCode())) {
      // Reuse the completed S3 upload; ownership, request identity and row lock were checked above.
      session.retryValidation();
      events.publishEvent(new EpisodeValidationRequested(session.getId()));
      return result(session, true);
    }
    if (session.getStatus() == EpisodeUploadStatus.VALIDATING) {
      events.publishEvent(new EpisodeValidationRequested(session.getId()));
      return result(session, true);
    }
    if (session.getStatus() == EpisodeUploadStatus.READY
        || session.getStatus() == EpisodeUploadStatus.INVALID
        || session.getStatus() == EpisodeUploadStatus.FAILED)
      return result(session, true);
    if (session.getStatus() != EpisodeUploadStatus.UPLOAD_PENDING)
      throw new EpisodeUploadException(
          HttpStatus.CONFLICT, "STATE_CONFLICT", "업로드 대기 상태에서만 완료할 수 있습니다.");
    if (session.getExpiresAt().isBefore(Instant.now()))
      throw new EpisodeUploadException(HttpStatus.GONE, "UPLOAD_SESSION_EXPIRED", "업로드 세션이 만료되었습니다.");
    EpisodeObjectStorage.StoredObject object = storage.inspect(session.getBucketName(), session.getObjectKey());
    String expected = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(session.getSha256()));
    if (object.sizeBytes() != session.getSizeBytes() || !expected.equals(object.checksumBase64()))
      throw new EpisodeUploadException(HttpStatus.UNPROCESSABLE_CONTENT, "CHECKSUM_MISMATCH",
          "S3 객체의 크기 또는 checksum이 예약값과 일치하지 않습니다.");
    session.complete(Instant.now());
    idempotency.complete("DEVICE", deviceId, "POST", path, claim, 202,
        "EPISODE_UPLOAD_SESSION", session.getId());
    events.publishEvent(new EpisodeValidationRequested(session.getId()));
    return result(session, false);
  }

  @Transactional(readOnly = true)
  public EpisodeProcessingStatusResponse status(
      UUID ownerId, UUID deviceId, UUID sessionId) {
    EpisodeUploadSession session =
        sessions
            .findByOwnerIdAndUmiDeviceIdAndSessionId(ownerId, deviceId, sessionId)
            .orElseThrow(() -> notFound("업로드 세션을 찾을 수 없습니다."));
    Optional<Episode> episode = episodes.findByIdAndOwnerId(session.getEpisodeId(), ownerId);
    if (episode.isPresent()) {
      Episode value = episode.get();
      return new EpisodeProcessingStatusResponse(
          value.getId(),
          sessionId,
          value.getStatus().name(),
          value.getQualityStatus().name(),
          processingError(value.getErrorCode(), value.getErrorMessage()));
    }

    String processingStatus = statusName(session);
    return new EpisodeProcessingStatusResponse(
        session.getEpisodeId(),
        sessionId,
        processingStatus,
        session.getQualityStatus(),
        processingError(session.getValidationErrorCode(), session.getValidationErrorMessage()));
  }

  @Transactional
  public void cancel(UUID ownerId, UUID deviceId, UUID uploadId) {
    EpisodeUploadSession session = requireOwnedSessionForUpdate(ownerId, deviceId, uploadId);
    if (session.getStatus() == EpisodeUploadStatus.CANCELLED) {
      return;
    }
    if (session.getStatus() != EpisodeUploadStatus.UPLOAD_PENDING) {
      throw new EpisodeUploadException(
          HttpStatus.CONFLICT, "STATE_CONFLICT", "완료 처리된 업로드 세션은 취소할 수 없습니다.");
    }
    storage.delete(session.getBucketName(), session.getObjectKey());
    session.cancel(Instant.now());
  }

  @Transactional
  public EpisodeUploadSessionResponse renew(
      UUID ownerId, UUID deviceId, UUID uploadId) {
    EpisodeUploadSession session = requireOwnedSessionForUpdate(ownerId, deviceId, uploadId);
    if (session.getStatus() != EpisodeUploadStatus.UPLOAD_PENDING) {
      throw new EpisodeUploadException(
          HttpStatus.CONFLICT, "STATE_CONFLICT", "업로드 대기 상태에서만 URL을 재발급할 수 있습니다.");
    }
    session.renew(Instant.now().plus(properties.presignTtl()));
    return signedResponse(session);
  }

  private String statusName(EpisodeUploadSession session) {
    return switch (session.getStatus()) {
      case UPLOAD_PENDING ->
          storage.inspectIfExists(session.getBucketName(), session.getObjectKey()).isPresent()
              ? "UPLOADED"
              : "UPLOAD_PENDING";
      case VALIDATING -> "VALIDATING";
      case READY -> "READY";
      case INVALID -> "INVALID";
      case FAILED, EXPIRED, CANCELLED -> "FAILED";
    };
  }

  private EpisodeProcessingStatusResponse.ProcessingError processingError(
      String code, String message) {
    if (code == null && message == null) return null;
    return new EpisodeProcessingStatusResponse.ProcessingError(code, message, "RETRY_OR_RECAPTURE");
  }

  private EpisodeUploadSession requireOwnedSessionForUpdate(
      UUID ownerId, UUID deviceId, UUID uploadId) {
    return sessions
        .findForUpdateById(uploadId)
        .filter(
            session ->
                session.getOwnerId().equals(ownerId)
                    && session.getUmiDeviceId().equals(deviceId))
        .orElseThrow(() -> notFound("업로드 세션을 찾을 수 없습니다."));
  }

  private void validateRequest(UUID ownerId, UUID deviceId, EpisodeUploadSessionRequest request) {
    if (request.sizeBytes() > properties.maxUploadBytes())
      throw new EpisodeUploadException(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "업로드 최대 크기를 초과했습니다.");
    devices.findById(deviceId)
        .filter(d -> d.getOwnerId().equals(ownerId))
        .filter(d -> d.getDeviceType() == DeviceType.UMI_CAMERA)
        .orElseThrow(() -> new EpisodeUploadException(HttpStatus.FORBIDDEN, "FORBIDDEN", "UMI 장치만 업로드할 수 있습니다."));
    skills.findBySkillKeyAndVisibleToOwner(request.metadata().skillKey(), ownerId)
        .orElseThrow(() -> notFound("등록된 동작을 찾을 수 없습니다."));
    if (request.metadata().endedAt().isBefore(request.metadata().startedAt())
        || request.summary().firstTimestampNs() < request.metadata().startMonotonicNs()
        || request.summary().lastTimestampNs() < request.summary().firstTimestampNs())
      throw new EpisodeUploadException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "수집 시간 범위가 올바르지 않습니다.");
  }

  private void validateReservation(EpisodeUploadSession session, EpisodeUploadSessionRequest request) {
    Instant endedAt = Instant.parse(session.getMetadata().get("endedAt").toString());
    if (session.getSizeBytes() != request.sizeBytes() || !session.getSha256().equals(request.sha256())
        || !endedAt.equals(request.metadata().endedAt()))
      throw new EpisodeUploadException(HttpStatus.CONFLICT, "EPISODE_ID_REUSED", "같은 sessionId에 다른 파일을 예약할 수 없습니다.");
  }
  private void validateCompletion(EpisodeUploadSession s, EpisodeUploadCompleteRequest r) {
    if (!s.getSessionId().equals(r.sessionId()) || s.getSizeBytes() != r.sizeBytes()
        || !s.getSha256().equals(r.sha256()))
      throw new EpisodeUploadException(HttpStatus.CONFLICT, "EPISODE_ID_REUSED", "완료 정보가 예약값과 다릅니다.");
  }
  private EpisodeUploadSessionResponse signedResponse(EpisodeUploadSession session) {
    EpisodeObjectStorage.PresignedUpload signed = storage.createUpload(session.getBucketName(),
        session.getObjectKey(), session.getSizeBytes(), session.getSha256(), properties.presignTtl());
    return new EpisodeUploadSessionResponse(session.getId(), session.getSessionId(), "PUT", signed.url(),
        Map.of("Content-Type", "application/zip", "x-amz-checksum-sha256", signed.checksumBase64(),
            "If-None-Match", "*"),
        signed.expiresAt(), properties.maxUploadBytes());
  }
  private EpisodeUploadCompleteResponse result(EpisodeUploadSession s, boolean duplicate) {
    String status =
        switch (s.getStatus()) {
          case READY -> "READY";
          case INVALID -> "INVALID";
          case FAILED -> "FAILED";
          default -> "VALIDATING";
        };
    return new EpisodeUploadCompleteResponse(s.getEpisodeId(), s.getSessionId(), status, duplicate);
  }
  private String objectKey(UUID owner, Instant ended, UUID session) {
    return "episodes/" + owner.toString().toLowerCase(Locale.ROOT) + "/" + KEY_TIME.format(ended)
        + "/" + session.toString().toLowerCase(Locale.ROOT) + ".zip";
  }
  private Map<String, Object> metadataMap(EpisodeUploadSessionRequest.Metadata value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("contractVersion", value.contractVersion()); result.put("schemaVersion", value.schemaVersion());
    result.put("skillKey", value.skillKey()); result.put("source", value.source());
    if (value.success() != null) result.put("success", value.success());
    result.put("labelStatus", value.labelStatus()); result.put("startedAt", value.startedAt().toString());
    result.put("endedAt", value.endedAt().toString()); result.put("startMonotonicNs", value.startMonotonicNs());
    result.put("timestampTimebase", value.timestampTimebase()); result.put("device", value.device());
    result.put("camera", value.camera());
    if (value.coordinateSystem() != null) result.put("coordinateSystem", value.coordinateSystem());
    if (value.units() != null) result.put("units", value.units());
    return Map.copyOf(result);
  }
  private Map<String, Object> summaryMap(EpisodeUploadSessionRequest.Summary value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemaVersion", value.schemaVersion()); result.put("firstTimestampNs", value.firstTimestampNs());
    result.put("lastTimestampNs", value.lastTimestampNs()); result.put("frameCount", value.frameCount());
    result.put("poseSampleCount", value.poseSampleCount()); result.put("imuSampleCount", value.imuSampleCount());
    result.put("gripperSampleCount", value.gripperSampleCount());
    result.put("missingFrameCount", value.missingFrameCount()); result.put("trackingGapCount", value.trackingGapCount());
    result.put("trackingLostDurationNs", value.trackingLostDurationNs());
    return Map.copyOf(result);
  }
  private void validateKey(String key) {
    try { UUID.fromString(key); } catch (RuntimeException exception) {
      throw new EpisodeUploadException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Idempotency-Key는 UUID여야 합니다.");
    }
  }
  private EpisodeUploadException notFound(String message) {
    return new EpisodeUploadException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
  }
}
