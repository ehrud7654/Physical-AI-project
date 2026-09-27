package com.anssa8bu.ssafy.episode.dto;

import com.anssa8bu.ssafy.episode.entity.ArchiveCompressionMethod;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchiveStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeLabelSource;
import com.anssa8bu.ssafy.episode.entity.EpisodeLabelStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeQualityStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeSource;
import com.anssa8bu.ssafy.episode.entity.EpisodeStatus;
import com.anssa8bu.ssafy.episode.entity.TimestampTimebase;
import com.anssa8bu.ssafy.episode.vo.CaptureMetadata;
import com.anssa8bu.ssafy.episode.vo.QualitySummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API 명세 1.2.0 EPISODE-01/02/06 응답 모델. */
public final class EpisodeApiModels {
  private EpisodeApiModels() {}

  /** 명세 2.4 RecoveryAction 허용값 전체. */
  public enum RecoveryAction {
    WAIT,
    RETRY_UPLOAD,
    RECORD_AGAIN,
    SET_LABEL,
    EXTEND_RETENTION,
    CREATE_DEPLOYMENT,
    RETRY,
    CONTACT_SUPPORT,
    NONE
  }

  public record BlockingReason(String code, String message, RecoveryAction recoveryAction) {}

  public record ResourceError(String code, String message, RecoveryAction recoveryAction) {}

  /** 목록 표시 및 학습 선택에 필요한 필드만 반환한다. 업로드 시각은 완료 접수 시각이다. */
  public record EpisodeListResponse(
      UUID id, String skillKey, String fileName, Instant uploadedAt, long durationMs,
      UUID umiDeviceId, String umiDeviceHandleNumber, EpisodeStatus status,
      Instant retentionExpiresAt, boolean trainable,
      List<BlockingReason> blockingReasons, RecoveryAction recoveryAction) {}

  /** EPISODE-01 쿼리. 모두 선택이며 지정한 조건만 AND로 적용한다. */
  public record ListQuery(
      UUID umiDeviceId,
      UUID skillId,
      String skillKey,
      EpisodeSource source,
      EpisodeLabelStatus labelStatus,
      Boolean success,
      String contractVersion,
      EpisodeQualityStatus qualityStatus,
      EpisodeStatus status,
      Boolean availableForTraining,
      Instant startedFrom,
      Instant startedTo) {}

  /** 다운로드 URL·로컬 경로는 포함하지 않는다 (EPISODE-02). */
  public record ArchiveResponse(
      UUID id,
      String contentType,
      String archiveFormatVersion,
      ArchiveCompressionMethod compressionMethod,
      long sizeBytes,
      long uncompressedSizeBytes,
      int entryCount,
      String sha256,
      EpisodeArchiveStatus status,
      Instant storedAt,
      Instant validatedAt,
      Instant deletedAt) {}

  public record EpisodeResponse(
      UUID id,
      UUID ownerId,
      UUID umiDeviceId,
      /** FE 데이터 탭이 장치를 handle number(예: UMI-024)로 표시하므로 함께 내려준다. 명세 외 보조 필드. */
      String umiDeviceHandleNumber,
      UUID skillId,
      String skillKey,
      /** FE 표시용 보조 필드. */
      String skillDisplayName,
      String sessionId,
      EpisodeSource source,
      Boolean success,
      EpisodeLabelStatus labelStatus,
      EpisodeLabelSource labelSource,
      Instant labeledAt,
      String contractVersion,
      String metaSchemaVersion,
      String summarySchemaVersion,
      EpisodeStatus status,
      Instant startedAt,
      Instant endedAt,
      long startMonotonicNs,
      TimestampTimebase timestampTimebase,
      long firstTimestampNs,
      long lastTimestampNs,
      long durationNs,
      int frameCount,
      int poseSampleCount,
      int imuSampleCount,
      int gripperSampleCount,
      int missingFrameCount,
      int trackingGapCount,
      long trackingLostDurationNs,
      EpisodeQualityStatus qualityStatus,
      Instant validatedAt,
      Instant lastUsedAt,
      Instant retentionExpiresAt,
      Instant deletedAt,
      CaptureMetadata captureMetadata,
      QualitySummary qualitySummary,
      ArchiveResponse archive,
      boolean trainable,
      List<BlockingReason> blockingReasons,
      RecoveryAction recoveryAction,
      ResourceError error,
      Instant createdAt) {}

  /** EPISODE-06. */
  public record RetentionPolicyResponse(
      String name,
      int defaultRetentionDays,
      int postTrainingGraceDays,
      int maxRetentionDays,
      boolean allowManualExtension,
      boolean allowManualPurge) {}
}
