package com.anssa8bu.ssafy_gpu.common.exception;

public class Ec2ApiException extends RuntimeException {
  private final int status;
  private final String code;

  public Ec2ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
