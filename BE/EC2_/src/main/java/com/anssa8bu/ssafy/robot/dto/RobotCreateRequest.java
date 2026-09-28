package com.anssa8bu.ssafy.robot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RobotCreateRequest(
    @NotBlank @Size(max = 100) String handleNumber,
    @NotBlank @Size(max = 150) String name,
    @Size(max = 200) String location,
    UUID robotProfileId) {}
