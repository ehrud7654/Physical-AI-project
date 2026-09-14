package com.anssa8bu.ssafy.episodeupload.dto;

import java.util.UUID;

public record EpisodeUploadCompleteResponse(
    UUID episodeId, UUID sessionId, String status, boolean duplicate) {}
