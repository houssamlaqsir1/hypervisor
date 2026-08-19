package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.SigEventRepository;
import com.oncf.hypervisor.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a zone used to fail with a 500 as soon as the zone had ever
 * produced an alert — which is every zone that has done its job. Alerts and
 * SIG events hold a foreign key to their zone, so the database refused the
 * delete.
 *
 * <p>The resolution is neither to cascade (that would erase incident history
 * along with a configuration entry) nor to refuse (an admin must be able to
 * retire a zone). The references are dropped and the events kept — the
 * zone's name already lives in each alert's message text, so the timeline
 * still reads correctly once the zone is gone.
 */
class ZoneDeletionTest {

    private ZoneRepository zoneRepository;
    private AlertRepository alertRepository;
    private SigEventRepository sigEventRepository;
    private ZoneAdminService service;

    @BeforeEach
    void setUp() {
        zoneRepository = mock(ZoneRepository.class);
        alertRepository = mock(AlertRepository.class);
        sigEventRepository = mock(SigEventRepository.class);
        service = new ZoneAdminService(
                zoneRepository, alertRepository, sigEventRepository, mock(HypervisorMapper.class));
    }

    private Zone existingZone(long id) {
        Zone zone = Zone.builder()
                .id(id)
                .name("Rabat Agdal Platform")
                .type(ZoneType.STATION)
                .centerLat(34.0075)
                .centerLon(-6.8533)
                .radiusM(120.0)
                .build();
        when(zoneRepository.findById(id)).thenReturn(Optional.of(zone));
        return zone;
    }

    @Test
    void detachesTheZoneFromItsHistoryBeforeDeletingIt() {
        Zone zone = existingZone(7L);
        when(alertRepository.detachZone(7L)).thenReturn(93);
        when(sigEventRepository.detachZone(7L)).thenReturn(4);

        service.delete(7L);

        // Order matters: the foreign keys must be gone before the row is
        // removed, otherwise the database rejects the delete — which is
        // exactly the 500 this replaced.
        InOrder order = inOrder(alertRepository, sigEventRepository, zoneRepository);
        order.verify(alertRepository).detachZone(7L);
        order.verify(sigEventRepository).detachZone(7L);
        order.verify(zoneRepository).delete(zone);
    }

    @Test
    void keepsTheAlertsThemselves() {
        existingZone(7L);

        service.delete(7L);

        // The incident record survives the zone being retired. Deleting the
        // alerts would destroy evidence to tidy up a config entry.
        verify(alertRepository, never()).deleteAll();
        verify(alertRepository, never()).deleteByStatus(any());
    }

    @Test
    void deletingAnUnknownZoneIsNotFoundAndTouchesNothing() {
        when(zoneRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("404");

        verify(alertRepository, never()).detachZone(any());
        verify(zoneRepository, never()).delete(any());
    }

    @Test
    void aZoneWithNoHistoryDeletesCleanly() {
        Zone zone = existingZone(9L);
        when(alertRepository.detachZone(9L)).thenReturn(0);
        when(sigEventRepository.detachZone(9L)).thenReturn(0);

        service.delete(9L);

        verify(zoneRepository).delete(zone);
    }
}
