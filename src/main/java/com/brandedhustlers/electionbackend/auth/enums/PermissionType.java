package com.brandedhustlers.electionbackend.auth.enums;

/**
 * Granular permissions for the Sovereign Ledger electoral platform.
 *
 * <p>These are assigned to {@link com.brandedhustlers.electionbackend.auth.domain.Role} objects,
 * which org admins create and assign to users within their organization.
 * Platform roles (SUPER_ADMIN, ADMIN, ORG_ADMIN) bypass permission checks entirely —
 * these permissions govern delegation to lower roles (CONTRIBUTOR, AUDITOR, OBSERVER, etc.).</p>
 */
public enum PermissionType {

    // ── Elections ────────────────────────────────────────────────────────────
    /** Create a new election draft within the org. */
    CREATE_ELECTION,
    /** Edit an existing election while it is in DRAFT status. */
    UPDATE_ELECTION,
    /** Advance an election to UPCOMING or LIVE status (publish / go-live). */
    PUBLISH_ELECTION,
    /** Manually close a LIVE election (move to ENDED). */
    END_ELECTION,
    /** Release results for an ended election (move to RESULTS_RELEASED). */
    RELEASE_RESULTS,
    /** Read-only view of elections within the org. */
    VIEW_ELECTIONS,

    EDIT_ELECTIONS,

    // ── Voters ───────────────────────────────────────────────────────────────
    /** Register a new voter within the org. */
    CREATE_VOTER,
    /** Update contact information for an existing voter. */
    UPDATE_VOTER,
    /** Remove a voter record from the org. */
    DELETE_VOTER,
    /** Bulk-import voters via CSV/XLSX upload. */
    IMPORT_VOTERS,
    /** Assign voters to an election's eligible voter list. */
    ASSIGN_VOTERS,
    /** Read-only view of voters within the org. */
    VIEW_VOTERS,

    // ── Candidates ───────────────────────────────────────────────────────────
    /** Add, edit, or remove candidates on an election. */
    MANAGE_CANDIDATES,

    // ── Users & Roles ────────────────────────────────────────────────────────
    /** Create a new internal user account within the org. */
    CREATE_USER,
    /** Edit an existing user's profile or role. */
    UPDATE_USER,
    /** Deactivate (soft-delete) a user account. */
    DEACTIVATE_USER,
    /** Read-only view of user accounts within the org. */
    VIEW_USERS,
    /** Create a custom role within the org. */
    CREATE_ROLE,
    /** Edit an existing custom role's permission set. */
    UPDATE_ROLE,
    /** Delete a custom role from the org. */
    DELETE_ROLE,
    /** Read-only view of roles and their permissions. */
    VIEW_ROLES,
    /** Assign or revoke a role from a user. */
    ASSIGN_ROLE,

    // ── Operations ───────────────────────────────────────────────────────────
    /** Enroll, configure, and manage polling stations. */
    MANAGE_STATIONS,
    /** Enroll, rebind, and monitor polling devices. */
    MANAGE_DEVICES,

    // ── Reporting & Audit ────────────────────────────────────────────────────
    /** View election results and tally data. */
    VIEW_RESULTS,
    /** Access the audit log for the org. */
    VIEW_AUDIT_LOG,

    // ── Eligibility ──────────────────────────────────────────────────────────
    /** Run a dry-run eligibility check for a voter against an election. */
    CHECK_ELIGIBILITY
}
