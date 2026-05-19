package com.brandedhustlers.electionbackend.ops;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.domain.Station;
import com.brandedhustlers.electionbackend.ops.dto.StationDto;
import com.brandedhustlers.electionbackend.ops.dto.UpsertStation;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.exception.OrgMismatch;
import com.brandedhustlers.electionbackend.ops.mappers.StationMapper;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import com.brandedhustlers.electionbackend.ops.repository.StationRepository;
import com.brandedhustlers.electionbackend.ops.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

    @Mock
    StationRepository stationRepository;
    @Mock
    LaunchRepository launchRepository;
    @Mock
    StationMapper stationMapper;
    @InjectMocks
    StationService stationService;

    private UUID orgId;
    private UUID launchId;
    private Launch launch;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        launchId = UUID.randomUUID();

        launch = Launch.builder()
                .id(launchId)
                .orgId(orgId)
                .name("Test Launch")
                .build();
    }

    // ─── createStation ────────────────────────────────────────────────────────

    @Nested
    class CreateStation {

        @Test
        void createsStationSuccessfully() {
            UpsertStation cmd = new UpsertStation(launchId, "HQ Station", "Accra", 200);

            when(launchRepository.findById(launchId)).thenReturn(Optional.of(launch));

            Station saved = Station.builder()
                    .id(UUID.randomUUID())
                    .orgId(orgId)
                    .launchId(launchId)
                    .name("HQ Station")
                    .location("Accra")
                    .capacity(200)
                    .createdAt(Instant.now())
                    .build();

            when(stationRepository.save(any(Station.class))).thenReturn(saved);

            StationDto dto = new StationDto(saved.getId(), orgId, launchId, "HQ Station", "Accra", 200, null, null);
            when(stationMapper.entityToDto(saved)).thenReturn(dto);

            StationDto result = stationService.createStation(orgId, cmd);

            assertThat(result.name()).isEqualTo("HQ Station");
            verify(stationRepository).save(any(Station.class));
        }

        @Test
        void throwsWhenLaunchNotFound() {
            UpsertStation cmd = new UpsertStation(launchId, "Station X", "Kumasi", 100);
            when(launchRepository.findById(launchId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stationService.createStation(orgId, cmd))
                    .isInstanceOf(LaunchNotfound.class);
        }

        @Test
        void throwsOrgMismatchWhenLaunchBelongsToDifferentOrg() {
            UUID differentOrgId = UUID.randomUUID();
            launch.setOrgId(differentOrgId);
            UpsertStation cmd = new UpsertStation(launchId, "Station X", "Kumasi", 100);
            when(launchRepository.findById(launchId)).thenReturn(Optional.of(launch));

            assertThatThrownBy(() -> stationService.createStation(orgId, cmd))
                    .isInstanceOf(OrgMismatch.class);
        }

        @Test
        void throwsWhenStationNameIsBlank() {
            UpsertStation cmd = new UpsertStation(launchId, "  ", "Kumasi", 100);
            when(launchRepository.findById(launchId)).thenReturn(Optional.of(launch));

            assertThatThrownBy(() -> stationService.createStation(orgId, cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }
    }

    // ─── getStation ───────────────────────────────────────────────────────────

    @Nested
    class GetStation {

        @Test
        void returnsStationWhenOrgMatches() {
            UUID stationId = UUID.randomUUID();
            Station station = Station.builder().id(stationId).orgId(orgId).build();
            when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

            Optional<Station> result = stationService.getStation(orgId, stationId);

            assertThat(result).isPresent();
        }

        @Test
        void returnsEmptyWhenOrgMismatch() {
            UUID stationId = UUID.randomUUID();
            Station station = Station.builder().id(stationId).orgId(UUID.randomUUID()).build();
            when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

            Optional<Station> result = stationService.getStation(orgId, stationId);

            assertThat(result).isEmpty();
        }
    }

    // ─── updateStation ────────────────────────────────────────────────────────

    @Nested
    class UpdateStation {

        @Test
        void updatesNameAndLocation() {
            UUID stationId = UUID.randomUUID();
            Station existing = Station.builder().id(stationId).orgId(orgId).name("Old").location("Old City").build();
            when(stationRepository.findById(stationId)).thenReturn(Optional.of(existing));
            when(stationRepository.save(any())).thenReturn(existing);

            StationDto dto = new StationDto(stationId, orgId, launchId, "New Name", "New City", null, null, null);
            when(stationMapper.entityToDto(any())).thenReturn(dto);

            UpsertStation cmd = new UpsertStation(launchId, "New Name", "New City", null);
            StationDto result = stationService.updateStation(orgId, stationId, cmd);

            assertThat(result.name()).isEqualTo("New Name");
        }

        @Test
        void throwsWhenStationNotFound() {
            UUID stationId = UUID.randomUUID();
            when(stationRepository.findById(stationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stationService.updateStation(orgId, stationId,
                    new UpsertStation(launchId, "X", null, null)))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void throwsSecurityExceptionForOrgMismatch() {
            UUID stationId = UUID.randomUUID();
            Station existing = Station.builder().id(stationId).orgId(UUID.randomUUID()).build();
            when(stationRepository.findById(stationId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> stationService.updateStation(orgId, stationId,
                    new UpsertStation(launchId, "X", null, null)))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // ─── listStationByLaunch ──────────────────────────────────────────────────

    @Test
    void listStationsByLaunchDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Station s = Station.builder().orgId(orgId).launchId(launchId).name("S1").build();
        StationDto dto = new StationDto(UUID.randomUUID(), orgId, launchId, "S1", null, null, null, null);

        when(stationRepository.findByOrgIdAndLaunchId(orgId, launchId, pageable).getContent())
                .thenReturn(List.of(s));
        when(stationMapper.entityToDto(s)).thenReturn(dto);

        var page = stationService.listStationByLaunch(orgId, launchId, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).name()).isEqualTo("S1");
    }
}
