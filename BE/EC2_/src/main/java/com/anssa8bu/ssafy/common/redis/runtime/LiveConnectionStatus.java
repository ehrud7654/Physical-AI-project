package com.anssa8bu.ssafy.common.redis.runtime;

/** API-only status; UNKNOWN must never be persisted as a device/robot DB status. */
public enum LiveConnectionStatus {
  ONLINE,
  OFFLINE,
  UNKNOWN
}
