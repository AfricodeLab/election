package com.brandedhustlers.electionbackend;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.dto.LaunchDto;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import com.brandedhustlers.electionbackend.ops.mappers.LaunchMapper;
import com.brandedhustlers.electionbackend.ops.repository.LaunchElectionRepository;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import com.brandedhustlers.electionbackend.ops.service.LaunchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LaunchServiceTest {
    @Mock
    LaunchRepository launchRepository;
    @Mock
    LaunchElectionRepository links;
    @Mock
    LaunchMapper launchMapper;
    @Mock
    RabbitTemplate rabbitTemplate;
    @Mock
    AuditEmitter auditEmitter;
    @Mock
    ElectionRepository electionRepository;
    @Mock
    ElectionMapper electionMapper;
    @Mock
    GetUser getUser;

    @InjectMocks
    LaunchService launchService;

    @Test
    void createLaunch(){
        UUID orgId = UUID.randomUUID();
        String name = "Presidential Launch";
        String description = "Test launch";
        Instant startAt = Instant.now();
        Instant endAt = startAt.plusSeconds(3600);

        //fake user
        AppUser mock = new AppUser();
        mock.setAppUserId(UUID.randomUUID());
        mock.setFirstName("Reynolds");
        mock.setUserRole(UserRole.SUPER_ADMIN);

        when(getUser.getCurrentAuthenticatedUser()).thenReturn(mock);

        Launch savedLaunch = Launch.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .name(name)
                .description(description)
                .status(LaunchStatus.PLANNED)
                .deleteYn(DeleteYn.N)
                .build();

        when(launchRepository.save(any(Launch.class))).thenReturn(savedLaunch);

        LaunchDto mappedDto = new LaunchDto(
                savedLaunch.getId(),
                savedLaunch.getOrgId(),
                savedLaunch.getName(),
                savedLaunch.getDescription(),
                savedLaunch.getStatus(),
                savedLaunch.getStartAt(),
                savedLaunch.getEndAt(),
                savedLaunch.getCreatedAt(),
                savedLaunch.getDeleteYn()
        );
        when(launchMapper.entityToDto(savedLaunch)).thenReturn(mappedDto);

        LaunchDto result = launchService.create(orgId, name, description, startAt, endAt);

        assertNotNull(result);
        assertEquals(name, result.name());
        verify(launchRepository, times(1)).save(any(Launch.class));
        verify(auditEmitter, times(1)).emit(
                any(), eq("ops.launch.created"), any(), eq(orgId),
                eq("Launch created"),
                any(), any(), any(), any(),
                eq("Launch"), eq(savedLaunch.getId()), eq(savedLaunch.getName()),
                any(), any(), any(), any(), eq("INFO"), eq(1)
        );
    }

}
