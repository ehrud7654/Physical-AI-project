package com.anssa8bu.ssafy.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** A random login id and rotating counter authenticated with a domain-separated MAC. */
@Component
public class RefreshTokenProvider {
  private final byte[] key;

  public RefreshTokenProvider(JwtProperties properties) {
    key = properties.secret().getBytes(StandardCharsets.UTF_8);
  }

  public String issue(UUID sessionId, long version) {
    String prefix = sessionId + "." + version;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] signature = mac.doFinal(("ssafy-refresh-v1:" + prefix).getBytes(StandardCharsets.UTF_8));
      return prefix + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public Parsed parse(String token) {
    try {
      if (token == null || token.length() > 200) throw new IllegalArgumentException();
      String[] parts = token.split("\\.", -1);
      if (parts.length != 3) throw new IllegalArgumentException();
      UUID id = UUID.fromString(parts[0]);
      long version = Long.parseLong(parts[1]);
      if (version < 0 || !MessageDigest.isEqual(issue(id, version).getBytes(StandardCharsets.UTF_8),
          token.getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
      return new Parsed(id, version);
    } catch (IllegalArgumentException e) {
      throw new LoginSessionException("AUTH_SESSION_EXPIRED", "로그인 세션이 만료되었습니다.");
    }
  }

  public String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Parsed(UUID sessionId, long version) {}
}
