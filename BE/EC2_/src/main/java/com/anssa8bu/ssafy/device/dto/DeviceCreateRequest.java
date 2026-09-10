package com.anssa8bu.ssafy.device.dto;

import com.anssa8bu.ssafy.device.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceCreateRequest(
    @NotNull DeviceType deviceType,
    @NotBlank @Size(max = 100) String handleNumber,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 200) String location) {}
