//package com.brandedhustlers.electionbackend.chain.repository;
//
//import com.brandedhustlers.electionbackend.chain.domain.AnchorCursor;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//
//import java.time.Instant;
//import java.util.Optional;
//import java.util.UUID;
//
//public interface ChainCursorRepository extends JpaRepository<AnchorCursor, UUID> {
//    @Query("select c.lastAnchoredAt from AnchorCursor c where c.electionId = :electionId")
//    Optional<Instant> lastAnchoredAt(UUID electionId);
//
//    @Modifying
//    @Query(value = """
//              insert into electionpresent.chain.anchor_cursor (election_id, last_anchored_at)
//                values (:electionId, :at)
//              on conflict (election_id) do update set last_anchored_at = excluded.last_anchored_at
//            """, nativeQuery = true)
//    void upsert(UUID electionId, Instant at);
//}
