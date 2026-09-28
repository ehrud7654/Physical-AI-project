package com.anssa8bu.ssafy.checkpoint.application;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class CheckpointValidationDispatcher {
  private final CheckpointValidationService service;
  public CheckpointValidationDispatcher(CheckpointValidationService service){this.service=service;}
  @Async("episodeValidationExecutor")
  @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
  public void dispatch(CheckpointValidationRequested event){service.validate(event.skillVersionId());}
}
