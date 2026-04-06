package com.brandedhustlers.electionbackend.common.flags;

import java.lang.annotation.*;

/**
 * Gates a method behind a feature flag check.
 *
 * The org is resolved from the current authenticated user's active organization.
 * If there is no authenticated user (e.g. a scheduled task), the check is skipped —
 * scheduler tasks must handle per-org gating programmatically.
 *
 * Usage:
 *   {@code @RequiresFeature(Feature.BLOCKCHAIN_ANCHORING)}
 *   public void anchorPendingVotes() { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresFeature {
    Feature value();
}
