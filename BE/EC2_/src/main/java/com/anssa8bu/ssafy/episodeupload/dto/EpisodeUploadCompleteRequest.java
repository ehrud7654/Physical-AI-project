package com.anssa8bu.ssafy.episodeupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record EpisodeUploadCompleteRequest(
    @NotNull UUID sessionId,
    @Positive long sizeBytes,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sha256) {}
