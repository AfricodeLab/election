package com.brandedhustlers.electionbackend.common.config.cache;

/**
 * Central registry of Redis cache names.
 * Use these constants in @Cacheable / @CacheEvict rather than raw strings
 * so a typo is a compile error, not a silent cache miss.
 */
public final class CacheNames {

    private CacheNames() {}

    /** Full list of elections for an org.  Key: orgId */
    public static final String ELECTIONS = "elections";

    /** Single election detail (with candidates). Key: orgId::electionId */
    public static final String ELECTION  = "election";

    /** Full list of launches for an org.  Key: orgId */
    public static final String LAUNCHES  = "launches";

    /** Single launch detail (with linked elections). Key: orgId::launchId */
    public static final String LAUNCH    = "launch";

    /** Users belonging to an org. Key: orgId */
    public static final String ORG_USERS = "org-users";
}
