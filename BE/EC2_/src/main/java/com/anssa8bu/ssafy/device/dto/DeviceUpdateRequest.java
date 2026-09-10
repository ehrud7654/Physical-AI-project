package com.anssa8bu.ssafy.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceUpdateRequest(
    @NotBlank @Size(max = 100) String name, @Size(max = 200) String location) {}
