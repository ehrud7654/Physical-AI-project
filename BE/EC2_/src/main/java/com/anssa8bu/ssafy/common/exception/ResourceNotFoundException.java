package com.anssa8bu.ssafy.common.exception;

public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String resource) {
    super(resource + "을(를) 찾을 수 없습니다.");
  }
}
