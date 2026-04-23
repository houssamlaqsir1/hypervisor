package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.SigEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigEventRepository extends JpaRepository<SigEvent, Long> {
}
