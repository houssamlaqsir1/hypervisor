package com.oncf.hypervisor.service;

import com.oncf.hypervisor.dto.ZoneDto;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository repository;
    private final HypervisorMapper mapper;

    @Transactional(readOnly = true)
    public List<ZoneDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }
}
