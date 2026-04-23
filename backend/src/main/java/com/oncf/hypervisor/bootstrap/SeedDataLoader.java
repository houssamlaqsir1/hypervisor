package com.oncf.hypervisor.bootstrap;

import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inserts a small catalog of railway zones on first startup so the correlation
 * engine has something meaningful to match against. Safe to run repeatedly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader implements CommandLineRunner {

    private final ZoneRepository zoneRepository;

    @Override
    public void run(String... args) {
        List<Zone> seeds = List.of(
                Zone.builder()
                        .name("Casa-Voyageurs Platform")
                        .type(ZoneType.STATION)
                        .description("Main passenger platform - public area")
                        .centerLat(33.5971).centerLon(-7.5811).radiusM(120.0)
                        .build(),
                Zone.builder()
                        .name("Casa-Voyageurs Tracks North")
                        .type(ZoneType.TRACK)
                        .description("Active tracks just north of the station")
                        .centerLat(33.5983).centerLon(-7.5805).radiusM(80.0)
                        .build(),
                Zone.builder()
                        .name("Technical Depot")
                        .type(ZoneType.RESTRICTED)
                        .description("Restricted maintenance depot - staff only")
                        .centerLat(33.5905).centerLon(-7.6023).radiusM(150.0)
                        .build(),
                Zone.builder()
                        .name("Signal Box A")
                        .type(ZoneType.RESTRICTED)
                        .description("Signalling equipment room")
                        .centerLat(33.6010).centerLon(-7.5750).radiusM(40.0)
                        .build(),
                Zone.builder()
                        .name("Suburb Crossing")
                        .type(ZoneType.TRACK)
                        .description("Level crossing in residential area")
                        .centerLat(33.5730).centerLon(-7.6200).radiusM(60.0)
                        .build()
        );

        int created = 0;
        for (Zone z : seeds) {
            if (zoneRepository.findByName(z.getName()).isEmpty()) {
                zoneRepository.save(z);
                created++;
            }
        }
        log.info("Seed data: {} new zone(s) created, {} already present",
                created, seeds.size() - created);
    }
}
