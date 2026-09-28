package com.anssa8bu.ssafy.deviceauth.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(
    @NotBlank(message = "장치 ID는 필수입니다.") String deviceId,
    @NotBlank(message = "장치 secret은 필수입니다.") String deviceSecret) {}
