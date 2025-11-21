//package com.brandedhustlers.electionbackend.chain.service;
//
//import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.sql.Timestamp;
//import java.time.Instant;
//
//@Slf4j
//@Service
//public class AnchorScheduler {
//
//    private final ElectionRepository elections;
//    private final AnchorService anchorService;
//
//    public AnchorScheduler(ElectionRepository elections, AnchorService anchorService) {
//        this.elections = elections;
//        this.anchorService = anchorService;
//    }
//
////    @Scheduled(fixedDelay = 10000)
//    public void tick() {
//        Timestamp nowTs = Timestamp.from(Instant.now());
//        elections.findLive(nowTs).forEach(e ->
//                anchorService.anchorElectionWindow(e.getElectionId(), nowTs.toInstant()));
//        log.info("pushing anchor to chain....");
//    }
//
//}