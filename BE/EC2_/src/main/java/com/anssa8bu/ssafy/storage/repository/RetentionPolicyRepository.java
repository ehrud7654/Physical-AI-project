package com.anssa8bu.ssafy.storage.repository;

import com.anssa8bu.ssafy.storage.entity.RetentionPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

  /** 사용자에게 적용되는 현재 정책. uq_retention_policy_default 인덱스로 최대 1건이다. */
  Optional<RetentionPolicy> findFirstByDefaultPolicyTrueAndActiveTrue();
}
