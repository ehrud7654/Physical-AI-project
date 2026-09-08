package com.anssa8bu.ssafy.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedDate;

@MappedSuperclass
public abstract class AuditableEntity extends CreatedEntity {

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  protected Instant updatedAt;

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
