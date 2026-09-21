package com.anssa8bu.ssafy.common.redis.runtime;

public class RuntimeStateUnavailableException extends RuntimeException {
  public RuntimeStateUnavailableException(Throwable cause) {
    super("실시간 장치/스토리지 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.", cause);
  }
}
