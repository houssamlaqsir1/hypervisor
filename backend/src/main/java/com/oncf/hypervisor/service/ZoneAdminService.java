package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.dto.ZoneDto;
import com.oncf.hypervisor.dto.ZoneRequest;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.SigEventRepository;
import com.oncf.hypervisor.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only CRUD for surveillance zones (report: "Administrateur technique
 * — configure les zones de surveillance"). Zones created here take effect
 * immediately: the correlation engine loads them on the next incoming
 * event, so a newly-drawn TRACK or RESTRICTED zone starts generating alerts
 * right away.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneAdminService {

    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;
    private final SigEventRepository sigEventRepository;
    private final HypervisorMapper mapper;

    @Transactional
    public ZoneDto create(ZoneRequest req) {
        String name = req.name().trim();
        if (zoneRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Zone name '" + name + "' already exists");
        }
        Zone zone = Zone.builder()
                .name(name)
                .type(req.type())
                .description(trimOrNull(req.description()))
                .centerLat(req.centerLat())
                .centerLon(req.centerLon())
                .radiusM(req.radiusM())
                .elevationM(req.elevationM())
                .heightM(req.heightM())
                .isTunnel(req.isTunnel())
                .isBridge(req.isBridge())
                .build();
        return mapper.toDto(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneDto update(Long id, ZoneRequest req) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Zone " + id + " not found"));
        String name = req.name().trim();
        if (!name.equalsIgnoreCase(zone.getName())) {
            zoneRepository.findByName(name).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Zone name '" + name + "' already exists");
                }
            });
        }
        zone.setName(name);
        zone.setType(req.type());
        zone.setDescription(trimOrNull(req.description()));
        zone.setCenterLat(req.centerLat());
        zone.setCenterLon(req.centerLon());
        zone.setRadiusM(req.radiusM());
        zone.setElevationM(req.elevationM());
        zone.setHeightM(req.heightM());
        zone.setIsTunnel(req.isTunnel());
        zone.setIsBridge(req.isBridge());
        return mapper.toDto(zoneRepository.save(zone));
    }

    /**
     * Removes a zone from the configuration, keeping everything that happened
     * in it.
     *
     * <p>Alerts and SIG events both hold a foreign key to their zone, so a
     * plain delete fails the moment a zone has ever produced an alert —
     * which is every zone that has done its job. Cascading the delete would
     * be worse: it would erase the incident history along with the
     * configuration entry, and that history is the record of what the system
     * detected.
     *
     * <p>So the references are dropped instead. The events survive, and the
     * zone's name is already embedded in each alert's message ("Person in
     * station 'X'"), so the timeline still reads correctly afterwards.
     */
    @Transactional
    public void delete(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Zone " + id + " not found"));

        int alerts = alertRepository.detachZone(id);
        int sigEvents = sigEventRepository.detachZone(id);

        zoneRepository.delete(zone);
        log.info("Zone {} ('{}') deleted — detached from {} alert(s) and {} SIG event(s)",
                id, zone.getName(), alerts, sigEvents);
    }

    private static String trimOrNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
