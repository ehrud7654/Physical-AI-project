package com.anssa8bu.ssafy.common.redis.runtime;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.redis.RedisKeyGenerator;
import com.anssa8bu.ssafy.common.redis.dto.DevicePresence;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

@Service
public class RuntimeStateService {
  private final RuntimeStateRepository repository;
  private final RedisKeyGenerator keys;
  private final Clock clock;
  private final Duration deviceTtl;
  private final Duration storageTtl;

  public RuntimeStateService(
      RuntimeStateRepository repository,
      RedisKeyGenerator keys,
      Clock clock,
      @Value("${app.runtime-state.device-ttl:45s}") Duration deviceTtl,
      @Value("${app.runtime-state.storage-ttl:45s}") Duration storageTtl) {
    if (deviceTtl.isNegative()
        || deviceTtl.isZero()
        || storageTtl.isNegative()
        || storageTtl.isZero())
      throw new IllegalArgumentException("Runtime state TTL must be positive");
    this.repository = repository;
    this.keys = keys;
    this.clock = clock;
    this.deviceTtl = deviceTtl;
    this.storageTtl = storageTtl;
  }

  public void deviceHeartbeat(UUID deviceId, Instant now) {
    afterCommit(
        () ->
            repository.save(
                keys.deviceOnline(deviceId), new DevicePresence(1, deviceId, now), now, deviceTtl));
  }

  public DeviceView device(UUID deviceId, Instant databaseLastSeen) {
    try {
      var live =
          repository
              .find(keys.deviceOnline(deviceId), DevicePresence.class)
              .filter(value -> value.lastSeenAt().plus(deviceTtl).isAfter(clock.instant()));
      return live.map(value -> new DeviceView(LiveConnectionStatus.ONLINE, value.lastSeenAt()))
          .orElseGet(() -> new DeviceView(LiveConnectionStatus.OFFLINE, databaseLastSeen));
    } catch (RuntimeStateUnavailableException error) {
      return new DeviceView(LiveConnectionStatus.UNKNOWN, databaseLastSeen);
    }
  }

  public void requireOnline(UUID deviceId) {
    var view = device(deviceId, null);
    if (view.status() == LiveConnectionStatus.UNKNOWN)
      throw new RuntimeStateUnavailableException(null);
    if (view.status() != LiveConnectionStatus.ONLINE)
      throw new BusinessConflictException("ROBOT_OFFLINE", "로봇이 오프라인입니다.");
  }

  public void storageHeartbeat(StorageNodeState state) {
    afterCommit(
        () ->
            repository.save(
                keys.storageNode(state.nodeId()), state, state.lastSeenAt(), storageTtl));
  }

  public Optional<StorageNodeState> storage(UUID id) {
    return repository
        .find(keys.storageNode(id), StorageNodeState.class)
        .filter(value -> value.lastSeenAt().plus(storageTtl).isAfter(clock.instant()));
  }

  private void afterCommit(Runnable operation) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      operation.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            operation.run();
          }
        });
  }

  public record DeviceView(LiveConnectionStatus status, Instant lastSeenAt) {}
}
