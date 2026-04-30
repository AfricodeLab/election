package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.config.cache.CacheNames;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.ops.domain.LaunchElection;
import com.brandedhustlers.electionbackend.ops.domain.LaunchElectionId;
import com.brandedhustlers.electionbackend.ops.dto.GetLaunch;
import com.brandedhustlers.electionbackend.ops.dto.LaunchDto;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.exception.OrgMismatch;
import com.brandedhustlers.electionbackend.ops.mappers.LaunchMapper;
import com.brandedhustlers.electionbackend.ops.repository.LaunchElectionRepository;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only cache delegate for launches.
 * Extracted into its own Spring bean for the same reason as ElectionCacheDelegate:
 * self-invocation bypasses the AOP proxy and silently skips the cache.
 *
 * All cache keys are org-scoped (orgId prefix) to guarantee tenant isolation.
 */
@Component
public class LaunchCacheDelegate {

    private final LaunchRepository        launchRepository;
    private final LaunchElectionRepository links;
    private final LaunchMapper            launchMapper;
    private final ElectionRepository      electionRepository;
    private final ElectionMapper          electionMapper;

    public LaunchCacheDelegate(LaunchRepository launchRepository,
                               LaunchElectionRepository links,
                               LaunchMapper launchMapper,
                               ElectionRepository electionRepository,
                               ElectionMapper electionMapper) {
        this.launchRepository  = launchRepository;
        this.links             = links;
        this.launchMapper      = launchMapper;
        this.electionRepository = electionRepository;
        this.electionMapper    = electionMapper;
    }

    /**
     * Returns all non-deleted launches for an org.
     * Status filtering (if requested) is applied in-memory by the caller after
     * fetching the cached full list — keeps a single cache entry per org rather
     * than one per (org, status) combination and simplifies eviction.
     *
     * Cache key: orgId
     */
    @Cacheable(value = CacheNames.LAUNCHES, key = "#orgId")
    @Transactional(readOnly = true)
    public List<LaunchDto> getLaunchList(UUID orgId) {
        // Must use Collectors.toList() — Stream.toList() returns ImmutableCollections$ListN
        // (a final class). NON_FINAL Jackson typing skips final types, omitting the
        // WRAPPER_ARRAY prefix, which then causes MismatchedInputException on cache reads.
        return launchRepository.findByOrgId(orgId).stream()
                .filter(l -> l.getDeleteYn().equals(DeleteYn.N))
                .map(launchMapper::entityToDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns a single launch with its linked elections.
     * Cache key: orgId::launchId — org-scoped for tenant isolation.
     * Org isolation is also enforced at load time before the value is cached.
     */
    @Cacheable(value = CacheNames.LAUNCH, key = "#orgId + '::' + #launchId")
    @Transactional(readOnly = true)
    public GetLaunch getLaunchById(UUID orgId, UUID launchId) {
        var launch = launchRepository.findByLaunchId(launchId)
                .orElseThrow(() -> new LaunchNotfound("Launch not found: " + launchId));

        // Org isolation: verify before caching
        if (!launch.getOrgId().equals(orgId)) {
            throw new OrgMismatch("Launch does not belong to your organization");
        }

        List<UUID> electionIds = links.findByIdLaunchId(launchId).stream()
                .map(LaunchElection::getId)
                .map(LaunchElectionId::getElectionId)
                .collect(java.util.stream.Collectors.toList());

        // ArrayList (non-final) is required — this list is embedded inside GetLaunch
        // which is cached. ImmutableCollections$ListN would break Jackson's WRAPPER_ARRAY
        // serialization for the nested list field.
        List<ElectionDTO> elections = electionRepository.findAllById(electionIds).stream()
                .map(electionMapper::entityToDto)
                .collect(java.util.stream.Collectors.toList());

        return new GetLaunch(
                launch.getId(),
                launch.getOrgId(),
                launch.getName(),
                launch.getDescription(),
                launch.getStatus(),
                launch.getStartAt(),
                launch.getEndAt(),
                launch.getCreatedAt(),
                elections
        );
    }
}
