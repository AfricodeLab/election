package com.brandedhustlers.electionbackend.results;

import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.realtime.gateway.RealtimeGateway;
import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.domain.TallyId;
import com.brandedhustlers.electionbackend.results.repository.TallyRepository;
import com.brandedhustlers.electionbackend.results.service.TallyProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TallyProjectorTest {

    @Mock
    TallyRepository repo;
    @Mock
    RealtimeGateway realtimeGateway;
    @Mock
    ElectionRepository electionRepository;
    // Real ObjectMapper — no external deps
    private final ObjectMapper om = new ObjectMapper();

    @InjectMocks
    TallyProjector projector;

    private UUID electionId;
    private UUID candidateId;
    private String payload;

    private static final UUID TEST_ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        electionId = UUID.randomUUID();
        candidateId = UUID.randomUUID();
        payload = om.writeValueAsString(
                java.util.Map.of("electionId", electionId.toString(), "candidateId", candidateId.toString()));

        // Inject real ObjectMapper into the projector
        java.lang.reflect.Field omField = TallyProjector.class.getDeclaredField("om");
        omField.setAccessible(true);
        omField.set(projector, om);

        // Stub org lookup so RealtimeGateway gets a valid orgId on every test
        when(electionRepository.findOrgId(any())).thenReturn(TEST_ORG_ID);
    }

    @Test
    void incrementsExistingTallyCount() throws Exception {
        TallyId id = new TallyId(electionId, candidateId);
        Tally existing = new Tally(id, 5, Timestamp.from(Instant.now()));

        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        projector.onVoteCast(payload);

        ArgumentCaptor<Tally> captor = ArgumentCaptor.forClass(Tally.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getCount()).isEqualTo(6);
    }

    @Test
    void createsNewTallyWithCountOneWhenNoneExists() throws Exception {
        TallyId id = new TallyId(electionId, candidateId);

        when(repo.findById(id)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        projector.onVoteCast(payload);

        ArgumentCaptor<Tally> captor = ArgumentCaptor.forClass(Tally.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getCount()).isEqualTo(1);
    }

    @Test
    void parsesElectionIdAndCandidateIdFromJsonPayload() throws Exception {
        when(repo.findById(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        projector.onVoteCast(payload);

        // Verify via the saved tally
        ArgumentCaptor<Tally> tallyCaptor = ArgumentCaptor.forClass(Tally.class);
        verify(repo).save(tallyCaptor.capture());
        assertThat(tallyCaptor.getValue().getId().getElectionId()).isEqualTo(electionId);
        assertThat(tallyCaptor.getValue().getId().getCandidateId()).isEqualTo(candidateId);
    }
}
