package com.anssa8bu.ssafy.episodevalidation.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EpisodeValidationDispatcher {
  private final EpisodeValidationService service;

  public EpisodeValidationDispatcher(EpisodeValidationService service) {
    this.service = service;
  }

  @Async("episodeValidationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void dispatch(EpisodeValidationRequested event) {
    service.validate(event.uploadSessionId());
  }
}
