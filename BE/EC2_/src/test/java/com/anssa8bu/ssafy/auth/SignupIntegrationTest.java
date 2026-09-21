package com.anssa8bu.ssafy.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.user.repository.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Transactional
class SignupIntegrationTest extends IntegrationTestBase {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper json;

  String body(String email, String password) throws Exception {
    return json.writeValueAsString(
        Map.of("name", " New User ", "email", email, "password", password));
  }

  @Test
  void signupStoresNormalizedEmailAndHashedPasswordAndChecksDuplicates() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    mvc.perform(get("/api/v1/auth/email-availability").param("email", email))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.available").value(true));
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(body(" " + email.toUpperCase(Locale.ROOT) + " ", "test-password")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.name").value("New User"))
        .andExpect(jsonPath("$.data.password").doesNotExist());
    var user = users.findByEmailIgnoreCase(email.toUpperCase(Locale.ROOT)).orElseThrow();
    assertThat(user.getPasswordHash()).isNotEqualTo("test-password");
    assertThat(passwords.matches("test-password", user.getPasswordHash())).isTrue();
    mvc.perform(
            get("/api/v1/auth/email-availability").param("email", email.toUpperCase(Locale.ROOT)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.available").value(false));
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(body(email, "test-password")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void rejectsInvalidEmailAndPasswordsBeforeSaving() throws Exception {
    for (String email : List.of("invalid", "user@localhost", "a b@example.com", "")) {
      mvc.perform(get("/api/v1/auth/email-availability").param("email", email))
          .andExpect(status().isUnprocessableEntity());
      mvc.perform(
              post("/api/v1/auth/signup")
                  .contentType("application/json")
                  .content(body(email, "test-password")))
          .andExpect(status().isUnprocessableEntity());
    }
    for (String password : List.of("short", "가".repeat(25))) {
      mvc.perform(
              post("/api/v1/auth/signup")
                  .contentType("application/json")
                  .content(body(UUID.randomUUID() + "@example.com", password)))
          .andExpect(status().isUnprocessableEntity());
    }
  }
}
