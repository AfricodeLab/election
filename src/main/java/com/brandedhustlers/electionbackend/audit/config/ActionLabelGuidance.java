package com.brandedhustlers.electionbackend.audit.config;

/**
 * Producers should emit concise, consistent action labels.
 * Suggested mapping:
 *  - auth.user.registered            → "User signup"
 *  - auth.user.login_success         → "User login (success)"
 *  - auth.user.login_failure         → "User login (failure)"
 *  - ops.launch.created              → "Launch created"
 *  - ops.launch.status_changed       → "Launch status change"
 *  - election.election_created       → "Election created"
 *  - election.election_updated       → "Election updated"
 *  - election.election_status_changed→ "Election status change"
 *  - election.candidate_added        → "Candidate added"
 *  - election.candidate_updated      → "Candidate list update"
 *  - election.candidate_deleted      → "Candidate removed"
 *  - voter.voters_import_started     → "Voter import started"
 *  - voter.voters_import_completed   → "Voter import completed"
 *  - voter.assigned_to_election      → "Voter assigned to election"
 *  - voter.verification_sent         → "Verification sent"
 *  - voter.verification_confirmed    → "Voter verified"
 *  - voter.verification_failed       → "Verification failed"
 *  - voting.vote_cast                → "Vote cast"
 *  - notif.message_sent              → "Notification sent"
 *  - notif.message_failed            → "Notification failed"
 *  - chain.batch_submitted           → "Batch anchored (submitted)"
 *  - chain.batch_confirmed           → "Batch anchored (confirmed)"
 *  - chain.batch_failed              → "Batch anchor failed"
 */
public class ActionLabelGuidance {}