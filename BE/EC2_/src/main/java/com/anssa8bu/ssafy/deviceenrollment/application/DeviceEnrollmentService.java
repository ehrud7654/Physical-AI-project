package com.anssa8bu.ssafy.deviceenrollment.application;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceCredential;
import com.anssa8bu.ssafy.device.entity.DeviceEnrollment;
import com.anssa8bu.ssafy.device.entity.DeviceEnrollmentStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.repository.DeviceCredentialRepository;
import com.anssa8bu.ssafy.device.repository.DeviceEnrollmentRepository;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.deviceenrollment.dto.DeviceEnrollmentCompleteRequest;
import com.anssa8bu.ssafy.deviceenrollment.dto.DeviceEnrollmentCompleteResponse;
import com.anssa8bu.ssafy.deviceenrollment.dto.DeviceEnrollmentStartResponse;
import com.anssa8bu.ssafy.deviceenrollment.exception.DeviceEnrollmentException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceEnrollmentService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final DeviceRepository devices;
  private final DeviceEnrollmentRepository enrollments;
  private final DeviceCredentialRepository credentials;
  private final PasswordEncoder passwordEncoder;
  private final Duration enrollmentTtl;
  private final int maxAttempts;

  public DeviceEnrollmentService(
      DeviceRepository devices,
      DeviceEnrollmentRepository enrollments,
      DeviceCredentialRepository credentials,
      PasswordEncoder passwordEncoder,
      @Value("${app.device-enrollment.ttl:10m}") Duration enrollmentTtl,
      @Value("${app.device-enrollment.max-attempts:5}") int maxAttempts) {
    this.devices = devices;
    this.enrollments = enrollments;
    this.credentials = credentials;
    this.passwordEncoder = passwordEncoder;
    this.enrollmentTtl = enrollmentTtl;
    this.maxAttempts = maxAttempts;
  }

  @Transactional
  public DeviceEnrollmentStartResponse start(UUID ownerId, UUID deviceId) {
    Device device =
        devices
            .findByIdAndOwnerId(deviceId, ownerId)
            .orElseThrow(
                () ->
                    new DeviceEnrollmentException(
                        HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "장치를 찾을 수 없습니다."));
    if (device.getDeviceType() != DeviceType.UMI_CAMERA) {
      throw new DeviceEnrollmentException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "UNSUPPORTED_DEVICE_TYPE",
          "UMI_CAMERA 장치만 QR 등록할 수 있습니다.");
    }
    if (enrollmentTtl.isZero() || enrollmentTtl.isNegative() || maxAttempts < 1) {
      throw new IllegalStateException("device enrollment configuration is invalid");
    }

    Instant now = Instant.now();
    enrollments
        .findByDeviceIdAndStatusForUpdate(deviceId, DeviceEnrollmentStatus.ACTIVE)
        .ifPresent(
            active -> {
              if (active.isExpiredAt(now)) {
                active.expire();
              } else {
                active.revoke(now);
              }
              enrollments.flush();
            });

    UUID enrollmentId = UUID.randomUUID();
    String registrationToken = randomToken(32);
    Instant expiresAt = now.plus(enrollmentTtl);
    enrollments.save(
        new DeviceEnrollment(
            enrollmentId,
            deviceId,
            sha256(registrationToken),
            expiresAt,
            maxAttempts,
            ownerId));

    String qrPayload =
        "umi://enroll?enrollmentId=" + enrollmentId + "&registrationToken=" + registrationToken;
    return new DeviceEnrollmentStartResponse(enrollmentId, deviceId, qrPayload, expiresAt);
  }

  @Transactional(noRollbackFor = DeviceEnrollmentException.class)
  public DeviceEnrollmentCompleteResponse complete(DeviceEnrollmentCompleteRequest request) {
    DeviceEnrollment enrollment =
        enrollments
            .findByIdForUpdate(request.enrollmentId())
            .orElseThrow(this::invalidToken);
    Instant now = Instant.now();

    if (enrollment.getStatus() != DeviceEnrollmentStatus.ACTIVE) {
      throw new DeviceEnrollmentException(
          HttpStatus.CONFLICT,
          "ENROLLMENT_NOT_ACTIVE",
          "이미 사용되었거나 폐기된 장치 등록 요청입니다.");
    }
    if (enrollment.isExpiredAt(now)) {
      enrollment.expire();
      throw new DeviceEnrollmentException(
          HttpStatus.GONE, "ENROLLMENT_EXPIRED", "장치 등록 요청이 만료되었습니다.");
    }

    String requestHash = sha256(request.registrationToken());
    if (!MessageDigest.isEqual(
        enrollment.getRegistrationTokenHash().getBytes(), requestHash.getBytes())) {
      if (enrollment.recordFailedAttempt()) {
        enrollment.revoke(now);
      }
      throw invalidToken();
    }

    String deviceSecret = randomToken(48);
    String secretHash = passwordEncoder.encode(deviceSecret);
    credentials
        .findById(enrollment.getDeviceId())
        .ifPresentOrElse(
            credential -> credential.enroll(secretHash, now),
            () ->
                credentials.save(
                    new DeviceCredential(enrollment.getDeviceId(), secretHash, now)));
    devices
        .findById(enrollment.getDeviceId())
        .orElseThrow(this::invalidToken)
        .completeRegistration();
    enrollment.markUsed(now);

    return new DeviceEnrollmentCompleteResponse(enrollment.getDeviceId(), deviceSecret, now);
  }

  private DeviceEnrollmentException invalidToken() {
    return new DeviceEnrollmentException(
        HttpStatus.UNAUTHORIZED, "INVALID_ENROLLMENT_TOKEN", "유효하지 않은 장치 등록 토큰입니다.");
  }

  private static String randomToken(int byteLength) {
    byte[] bytes = new byte[byteLength];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
