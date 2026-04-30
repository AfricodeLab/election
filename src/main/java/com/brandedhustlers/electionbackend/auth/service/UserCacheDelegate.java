package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.dto.AppUserDTO;
import com.brandedhustlers.electionbackend.auth.mapper.AppUserMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only cache delegate for user lists.
 * Cache key: orgId — org-scoped for tenant isolation.
 * Evict: whenever a user is created or removed from the org.
 */
@Component
public class UserCacheDelegate {

    private final AppUserRepository appUserRepository;
    private final AppUserMapper     appUserMapper;

    public UserCacheDelegate(AppUserRepository appUserRepository,
                             AppUserMapper appUserMapper) {
        this.appUserRepository = appUserRepository;
        this.appUserMapper     = appUserMapper;
    }

    @Cacheable(value = CacheNames.ORG_USERS, key = "#orgId")
    @Transactional(readOnly = true)
    public List<AppUserDTO> getUserList(UUID orgId) {
        return appUserRepository.getUsersInOrg(orgId).stream()
                .map(appUserMapper::entityToDTO)
                .collect(java.util.stream.Collectors.toList());
    }
}
