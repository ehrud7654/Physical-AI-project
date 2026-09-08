package com.anssa8bu.ssafy.event.publisher;

import com.anssa8bu.ssafy.event.entity.OutboxEvent;

public interface EventDispatcher {
  void dispatch(OutboxEvent event);
}
