package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    Optional<Zone> findByName(String name);
}
