package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertStatus;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.service.external.AlertRadioClient;
import com.oncf.hypervisor.websocket.AlertBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deletion is the one alert operation that destroys a record, so its
 * boundaries matter more than its happy path.
 *
 * <p>Two distinctions are asserted here. First, deleting is not resolving:
 * resolving closes an incident and <em>keeps</em> it, deleting is for entries
 * that should never have been logged (a false positive, test traffic). Second,
 * clearing the log defaults to sparing anything still open — a closed incident
 * has served its purpose in the console, an open one is still someone's
 * problem.
 */
class AlertDeletionTest {

    private AlertRepository alertRepository;
    private AlertService service;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        service = new AlertService(
                alertRepository,
                mock(HypervisorMapper.class),
                mock(AlertRadioClient.class),
                mock(AlertBroadcaster.class));
    }

    private static Alert alert(long id, AlertStatus status) {
        return Alert.builder()
                .id(id)
                .severity(AlertSeverity.HIGH)
                .type(AlertType.OBJECT_ON_TRACK)
                .message("Car on track")
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void deletesTheAlertItWasGiven() {
        Alert doomed = alert(7L, AlertStatus.NEW);
        when(alertRepository.findById(7L)).thenReturn(Optional.of(doomed));

        service.delete(7L);

        verify(alertRepository).delete(doomed);
    }

    @Test
    void deletingAnUnknownAlertIsNotFoundRatherThanSilentSuccess() {
        // A caller that thinks it deleted something it didn't is worse than
        // an error — it would report success for a record still in the log.
        when(alertRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("404");
        verify(alertRepository, never()).delete(any(Alert.class));
    }

    @Test
    void clearingResolvedOnlyTouchesResolvedAlerts() {
        when(alertRepository.deleteByStatus(AlertStatus.RESOLVED)).thenReturn(12L);

        long removed = service.deleteAll(true);

        assertThat(removed).isEqualTo(12);
        verify(alertRepository).deleteByStatus(AlertStatus.RESOLVED);
        // Open incidents must survive routine housekeeping.
        verify(alertRepository, never()).deleteAll();
    }

    @Test
    void clearingEverythingWipesTheLogAndReportsTheCount() {
        when(alertRepository.count()).thenReturn(30L);

        long removed = service.deleteAll(false);

        assertThat(removed).isEqualTo(30);
        verify(alertRepository).deleteAll();
        verify(alertRepository, never()).deleteByStatus(any(AlertStatus.class));
    }

    @Test
    void clearingAnEmptyLogIsHarmless() {
        when(alertRepository.count()).thenReturn(0L);

        assertThat(service.deleteAll(false)).isZero();
    }
}
