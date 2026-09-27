package com.anssa8bu.ssafy.deviceauth.exception;

public class InvalidDeviceCredentialsException extends RuntimeException {

  public InvalidDeviceCredentialsException() {
    super("장치 ID 또는 secret이 올바르지 않습니다.");
  }
}
