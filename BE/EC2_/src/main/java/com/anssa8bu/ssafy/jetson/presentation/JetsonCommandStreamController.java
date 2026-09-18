package com.anssa8bu.ssafy.jetson.presentation;

import com.anssa8bu.ssafy.jetson.application.JetsonCommandRecoveryService;
import com.anssa8bu.ssafy.jetson.application.JetsonCommandStreamService;
import com.anssa8bu.ssafy.jetson.application.JetsonIdentityService;
import com.anssa8bu.ssafy.robot.entity.Robot;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/robots/me")
public class JetsonCommandStreamController {
  private final JetsonIdentityService identity;
  private final JetsonCommandStreamService streams;
  private final JetsonCommandRecoveryService recovery;

  public JetsonCommandStreamController(
      JetsonIdentityService identity,
      JetsonCommandStreamService streams,
      JetsonCommandRecoveryService recovery) {
    this.identity = identity;
    this.streams = streams;
    this.recovery = recovery;
  }

  @GetMapping(path = "/command-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter connect(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    Robot robot = identity.requireRobot(jwt);
    return streams.connectAndReplay(
        robot.getId(), lastEventId, () -> recovery.replay(robot.getId()));
  }
}
