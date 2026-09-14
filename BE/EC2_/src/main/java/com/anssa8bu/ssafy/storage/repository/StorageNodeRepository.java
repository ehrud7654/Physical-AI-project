package com.anssa8bu.ssafy.storage.repository;

import com.anssa8bu.ssafy.storage.entity.StorageNode;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageNodeRepository extends JpaRepository<StorageNode, UUID> {}
