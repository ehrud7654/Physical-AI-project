package com.anssa8bu.ssafy.storage.repository;

import com.anssa8bu.ssafy.storage.entity.StorageNode;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageNodeRepository extends JpaRepository<StorageNode, UUID> {
  Optional<StorageNode> findByWorkerIdAndNodeKey(String workerId, String nodeKey);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from StorageNode n where n.id=:id")
  Optional<StorageNode> findByIdForUpdate(@Param("id") UUID id);
}
