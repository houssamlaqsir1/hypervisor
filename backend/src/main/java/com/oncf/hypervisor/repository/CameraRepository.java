package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.Camera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    Optional<Camera> findByCameraId(String cameraId);
}
