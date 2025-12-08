package com.brandedhustlers.electionbackend.common.config.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.security.auth.Subject;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class VoterAuthToken extends AbstractAuthenticationToken {
    private final UUID voter;
    private final UUID voterOrganization;
    private final long stepUpVerifiedAt;

    /**
     * Creates a token with the supplied array of authorities.
     *
     * @param authorities the collection of <tt>GrantedAuthority</tt>s for the principal
     *                    represented by this authentication object.
     */
    public VoterAuthToken(Collection<? extends GrantedAuthority> authorities,
                          UUID voter,
                          String role,
                          UUID voterOrganization,
                          Long stepUpVerifiedAt) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.voter = voter;
        this.voterOrganization = voterOrganization;
        this.stepUpVerifiedAt = stepUpVerifiedAt;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    public UUID getVoterId() {
        return voter;
    }

    public UUID getVoterOrganizationId() {
        return voterOrganization;
    }

    public Long getStepUpVerifiedAt() {
        return stepUpVerifiedAt;
    }

    @Override
    public boolean implies(Subject subject) {
        return super.implies(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VoterAuthToken that)) return false;
        if (!super.equals(o)) return false;
        return getStepUpVerifiedAt() == that.getStepUpVerifiedAt() && Objects.equals(voter, that.voter) && Objects.equals(voterOrganization, that.voterOrganization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), voter, voterOrganization, getStepUpVerifiedAt());
    }

    @Override
    public String toString() {
        return "VoterAuthToken{" +
                "voterID=" + voter +
                ", voterOrganization=" + voterOrganization +
                ", stepUpVerifiedAt=" + stepUpVerifiedAt +
                '}';
    }
}
