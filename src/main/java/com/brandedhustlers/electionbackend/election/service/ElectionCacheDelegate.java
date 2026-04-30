package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.common.config.cache.CacheNames;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only cache delegate for elections.
 *
 * Why a separate bean?
 * Spring's @Cacheable is implemented via AOP proxy. If ElectionService called
 * its own @Cacheable methods directly (self-invocation), the call would bypass
 * the proxy and skip the cache entirely. Extracting to a dedicated Component
 * forces the call through the proxy, making caching work correctly.
 *
 * Cache keys are org-scoped (prefixed with orgId) so a cache entry created for
 * org A can NEVER be served to org B, even if they share an electionId.
 */
@Component
public class ElectionCacheDelegate {

    private final ElectionRepository electionRepository;
    private final ElectionMapper     electionMapper;

    public ElectionCacheDelegate(ElectionRepository electionRepository,
                                 ElectionMapper electionMapper) {
        this.electionRepository = electionRepository;
        this.electionMapper     = electionMapper;
    }

    /**
     * Returns the full list of (non-deleted) elections for an org, with candidates
     * pre-loaded via JOIN FETCH.
     *
     * Cache key: orgId
     * Evict: whenever any election in the org is created, updated, or status-changed.
     */
    @Cacheable(value = CacheNames.ELECTIONS, key = "#organizationId")
    @Transactional(readOnly = true)
    public List<ElectionDTO> getElectionList(UUID organizationId) {
        // Collectors.toList() returns ArrayList (non-final).
        // Stream.toList() returns ImmutableCollections$ListN which is final — NON_FINAL
        // Jackson typing skips final types, so no WRAPPER_ARRAY prefix is written and
        // deserialization fails with MismatchedInputException on the next request.
        return electionRepository.findByOrganizationId(organizationId)
                .stream()
                .map(electionMapper::entityToDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns a single election with its candidates.
     *
     * Cache key: orgId::electionId — the orgId prefix is the isolation guarantee.
     * Org isolation is also enforced at load time (before the value is stored in
     * Redis) so a mis-passed orgId never populates the cache with another org's data.
     *
     * Evict: whenever that specific election changes.
     */
    @Cacheable(value = CacheNames.ELECTION, key = "#orgId + '::' + #electionId")
    @Transactional(readOnly = true)
    public ElectionDTO getElectionById(UUID orgId, UUID electionId) {
        var election = electionRepository.findByElectionIdWithCandidates(electionId)
                .orElseThrow(() -> new ElectionNotFound("Election not found: " + electionId));

        // Org isolation: verify before caching — prevents storing org B's data under org A's key
        if (!election.getOrganization().getOrganizationId().equals(orgId)) {
            throw new AccessDeniedException("Election does not belong to your organization");
        }

        return electionMapper.entityToDto(election);
    }
}
