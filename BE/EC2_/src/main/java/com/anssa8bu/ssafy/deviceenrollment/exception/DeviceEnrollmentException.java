package com.anssa8bu.ssafy.deviceenrollment.exception;

import org.springframework.http.HttpStatus;

public class DeviceEnrollmentException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public DeviceEnrollmentException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() { return status; }
  public String getCode() { return code; }
}
