package com.brandedhustlers.electionbackend.common.util;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import com.brandedhustlers.electionbackend.common.config.security.VoterAuthToken;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetUser {

    private final VoterRepository voterRepository;

    public GetUser(VoterRepository voterRepository) {
        this.voterRepository = voterRepository;
    }

    /**
     * Retrieve the currently authenticated AppUser.
     * Throws SecurityException if unauthenticated or invalid.
     */
    public AppUser getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        validateAuthentication(authentication, UserPrincipal.class);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        AppUser user = principal.getAppUser();

        if (user == null) {
            throw new SecurityException("Authenticated user not found or invalid principal");
        }

        return user;
    }

    public Organization getCurrentAuthenticatedUserOrg(){
        return getCurrentAuthenticatedUser().getAppUserOrganization();
    }

    public UUID getCurrentAuthenticatedUserOrgId(){
        return getCurrentAuthenticatedUserOrg().getOrganizationId();
    }

    /**
     * Retrieve the UUID of the current authenticated AppUser.
     */
    public UUID getCurrentAuthenticatedUserId() {
        return getCurrentAuthenticatedUser().getAppUserId();
    }

    /**
     * Retrieve the currently authenticated Voter from the repository.
     * Throws SecurityException if unauthenticated or invalid.
     */
    public Voter getCurrentAuthenticatedVoter() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        validateAuthentication(authentication, VoterAuthToken.class);

        VoterAuthToken token = (VoterAuthToken) authentication.getPrincipal();

        return voterRepository.findVoterById(token.getVoterId(), token.getVoterOrganizationId());
    }

    /**
     * Validate authentication presence and expected principal type.
     */
    private void validateAuthentication(Authentication authentication, Class<?> expectedPrincipalClass) {
        if (authentication == null || !authentication.isAuthenticated()) {
            SecurityContextHolder.clearContext();
            throw new SecurityException("No authenticated session found");
        }

        Object principal = authentication.getPrincipal();
        if (!expectedPrincipalClass.isInstance(principal)) {
            SecurityContextHolder.clearContext();
            throw new SecurityException("Unexpected authentication principal type: " + principal.getClass().getSimpleName());
        }
    }
}
