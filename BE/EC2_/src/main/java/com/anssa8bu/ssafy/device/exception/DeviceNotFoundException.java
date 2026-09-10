package com.anssa8bu.ssafy.device.exception;

public class DeviceNotFoundException extends RuntimeException {

  public DeviceNotFoundException() {
    super("장치를 찾을 수 없습니다.");
  }
}
