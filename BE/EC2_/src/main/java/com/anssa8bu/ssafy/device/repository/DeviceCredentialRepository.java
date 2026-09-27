package com.anssa8bu.ssafy.device.repository;

import com.anssa8bu.ssafy.device.entity.DeviceCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, UUID> {}
