package com.anssa8bu.ssafy.deviceauth.application;

import com.anssa8bu.ssafy.auth.security.DeviceAccessTokenProvider;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceCredential;
import com.anssa8bu.ssafy.device.entity.DeviceCredentialStatus;
import com.anssa8bu.ssafy.device.repository.DeviceCredentialRepository;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.deviceauth.dto.DeviceTokenRequest;
import com.anssa8bu.ssafy.deviceauth.dto.DeviceTokenResponse;
import com.anssa8bu.ssafy.deviceauth.exception.InvalidDeviceCredentialsException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceAuthenticationService {

  private final DeviceRepository deviceRepository;
  private final DeviceCredentialRepository credentialRepository;
  private final PasswordEncoder passwordEncoder;
  private final DeviceAccessTokenProvider tokenProvider;

  public DeviceAuthenticationService(
      DeviceRepository deviceRepository,
      DeviceCredentialRepository credentialRepository,
      PasswordEncoder passwordEncoder,
      DeviceAccessTokenProvider tokenProvider) {
    this.deviceRepository = deviceRepository;
    this.credentialRepository = credentialRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenProvider = tokenProvider;
  }

  @Transactional
  public DeviceTokenResponse issueToken(DeviceTokenRequest request) {
    UUID deviceId = parseDeviceId(request.deviceId());
    Device device =
        deviceRepository.findById(deviceId).orElseThrow(InvalidDeviceCredentialsException::new);
    DeviceCredential credential =
        credentialRepository
            .findById(deviceId)
            .orElseThrow(InvalidDeviceCredentialsException::new);

    if (credential.getStatus() != DeviceCredentialStatus.ACTIVE
        || !passwordEncoder.matches(request.deviceSecret(), credential.getDeviceSecretHash())) {
      throw new InvalidDeviceCredentialsException();
    }

    credential.recordAuthentication(Instant.now());

    return new DeviceTokenResponse(
        tokenProvider.issue(device),
        "Bearer",
        tokenProvider.expiresInSeconds(),
        device.getDeviceType());
  }

  private UUID parseDeviceId(String value) {
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new InvalidDeviceCredentialsException();
    }
  }
}
