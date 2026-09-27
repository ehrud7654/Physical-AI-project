package com.anssa8bu.ssafy.device.exception;

public class DeviceAlreadyRegisteredException extends RuntimeException {

  public DeviceAlreadyRegisteredException() {
    super("이미 등록된 장치 ID입니다.");
  }
}
