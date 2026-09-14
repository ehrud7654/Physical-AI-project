package com.anssa8bu.ssafy.episodeupload.exception;

import org.springframework.http.HttpStatus;

public class EpisodeUploadException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public EpisodeUploadException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() { return status; }
  public String getCode() { return code; }
}
