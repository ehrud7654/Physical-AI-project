package com.anssa8bu.ssafy.deviceauth.dto;

import com.anssa8bu.ssafy.device.entity.DeviceType;

public record DeviceTokenResponse(
    String accessToken, String tokenType, long expiresIn, DeviceType deviceType) {}
