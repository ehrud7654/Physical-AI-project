package com.anssa8bu.ssafy.auth.security;

public class LoginSessionException extends RuntimeException {
  private final String code;
  public LoginSessionException(String code, String message) {
    super(message);
    this.code = code;
  }
  public String getCode() { return code; }
}
