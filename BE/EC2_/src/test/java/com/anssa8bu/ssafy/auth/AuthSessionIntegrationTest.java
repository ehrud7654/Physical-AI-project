package com.anssa8bu.ssafy.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.anssa8bu.ssafy.auth.security.*;
import com.anssa8bu.ssafy.common.redis.RedisKeyGenerator;
import com.anssa8bu.ssafy.common.redis.dto.SessionCache;
import com.anssa8bu.ssafy.common.redis.repository.SessionCacheRepository;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.user.entity.*;
import com.anssa8bu.ssafy.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Transactional
class AuthSessionIntegrationTest extends IntegrationTestBase {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper json;
  @Autowired JwtEncoder encoder;
  @Autowired JwtDecoder decoder;
  @Autowired JwtProperties properties;
  @Autowired SessionProperties sessionProperties;
  @Autowired StringRedisTemplate redis;
  @Autowired RedisKeyGenerator keys;
  @MockitoSpyBean SessionCacheRepository sessions;
  UUID userId;
  String email;
  Set<UUID> sessionIds = new HashSet<>();

  @BeforeEach
  void createUser() {
    userId = UUID.randomUUID();
    email = userId + "@auth.test";
    users.saveAndFlush(
        new User(userId, email, passwords.encode("test-password"), "test", UserStatus.ACTIVE));
  }

  @AfterEach
  void cleanup() {
    sessionIds.forEach(id -> redis.delete(keys.session(id)));
  }

  String login() throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("email", email, "password", "test-password"))))
            .andExpect(status().isOk())
            .andReturn();
    String token =
        json.readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("accessToken")
            .asText();
    sessionIds.add(UUID.fromString(decoder.decode(token).getClaimAsString("sid")));
    return token;
  }

  @Test
  void logoutRevokesOnlyCurrentSessionAndRedisUsesIdleTimeout() throws Exception {
    String first = login();
    String second = login();
    Jwt jwt = decoder.decode(first);
    UUID sid = UUID.fromString(jwt.getClaimAsString("sid"));
    SessionCache session = sessions.find(sid).orElseThrow();
    assertThat(session.userId()).isEqualTo(userId);
    assertThat(session.expiresAt()).isAfter(jwt.getExpiresAt());
    assertThat(redis.getExpire(keys.session(sid)))
        .isBetween(1L, sessionProperties.idleTimeout().toSeconds());
    mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + first))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/logout").header("X-Session-Request", "1").header("Authorization", "Bearer " + first))
        .andExpect(status().isNoContent());
    assertThat(sessions.find(sid)).isEmpty();
    mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + first))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + second))
        .andExpect(status().isOk());
  }

  @Test
  void invalidSessionsAreRejectedAndDeviceTokensDoNotNeedRedis() throws Exception {
    String token = login();
    UUID sid = UUID.fromString(decoder.decode(token).getClaimAsString("sid"));
    for (SessionCache invalid :
        List.of(
            new SessionCache(1, sid, UUID.randomUUID(), Instant.now().plusSeconds(60), false),
            new SessionCache(1, sid, userId, Instant.now().plusSeconds(60), true),
            new SessionCache(1, sid, userId, Instant.now().minusSeconds(1), false))) {
      sessions.save(sid, invalid, Duration.ofMinutes(1));
      mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + signed("USER", "user")))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
    doThrow(new RedisConnectionFailureException("offline")).when(sessions).find(any());
    String device = signed("DEVICE", "device");
    assertThat(decoder.decode(device).getClaimAsString("actorType")).isEqualTo("DEVICE");
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + device))
        .andExpect(status().isForbidden());
  }

  @Test
  void redisFailureIs503ForLoginRequestsAndLogout() throws Exception {
    String token = login();
    doThrow(new RedisConnectionFailureException("offline")).when(sessions).delete(any());
    mvc.perform(post("/api/v1/auth/logout").header("X-Session-Request", "1").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTH_SESSION_UNAVAILABLE"));
    doThrow(new RedisConnectionFailureException("offline")).when(sessions).find(any());
    mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTH_SESSION_UNAVAILABLE"));
    doThrow(new RedisConnectionFailureException("offline"))
        .when(sessions)
        .save(any(), any(), any());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", email, "password", "test-password"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTH_SESSION_UNAVAILABLE"));
  }

  String signed(String actor, String scope) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("actorType", actor)
            .claim("scope", scope)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
