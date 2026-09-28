package com.anssa8bu.ssafy.auth.security;

import org.springframework.security.oauth2.jwt.JwtException;

public class SessionStoreUnavailableException extends JwtException {
  public SessionStoreUnavailableException(Throwable cause) {
    super("로그인 세션 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", cause);
  }
}
