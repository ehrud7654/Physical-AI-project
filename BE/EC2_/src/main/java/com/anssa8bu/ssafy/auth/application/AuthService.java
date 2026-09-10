package com.anssa8bu.ssafy.auth.application;

import com.anssa8bu.ssafy.auth.dto.AuthenticatedUserResponse;
import com.anssa8bu.ssafy.auth.dto.LoginRequest;
import com.anssa8bu.ssafy.auth.dto.LoginResponse;
import com.anssa8bu.ssafy.auth.security.AccessTokenProvider;
import com.anssa8bu.ssafy.common.exception.InvalidCredentialsException;
import com.anssa8bu.ssafy.user.entity.User;
import com.anssa8bu.ssafy.user.entity.UserStatus;
import com.anssa8bu.ssafy.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenProvider accessTokenProvider;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AccessTokenProvider accessTokenProvider) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.accessTokenProvider = accessTokenProvider;
  }

  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    User user =
        userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);

    if (user.getStatus() != UserStatus.ACTIVE
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    return new LoginResponse(
        accessTokenProvider.issue(user.getId()),
        "Bearer",
        accessTokenProvider.expiresInSeconds(),
        AuthenticatedUserResponse.from(user));
  }
}
