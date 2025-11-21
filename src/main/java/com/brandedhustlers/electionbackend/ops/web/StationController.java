package com.brandedhustlers.electionbackend.ops.web;

import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.ops.domain.Station;
import com.brandedhustlers.electionbackend.ops.dto.UpsertStation;
import com.brandedhustlers.electionbackend.ops.service.StationService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {
    private final StationService svc;
    private final GetUser getUser;

    public StationController(StationService svc,
                             GetUser getUser) {
        this.svc = svc;
        this.getUser = getUser;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody UpsertStation createStation) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return ResponseEntity.ok(svc.createStation(orgId, createStation));
    }

    @GetMapping("/get/{stationId}")
    public ResponseEntity<Station> getStation(@PathVariable UUID stationId) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return svc.getStation(orgId, stationId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/get-by-launch")
    public ResponseEntity<?> listByLaunch(@RequestParam UUID launchId, Pageable pageable) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return ResponseEntity.ok(svc.listStationByLaunch(orgId, launchId, pageable));
    }

    @PutMapping("/update/{stationId}")
    public ResponseEntity<?> update(@PathVariable UUID stationId,
                                    @RequestBody UpsertStation upsertStation) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return ResponseEntity.ok(svc.updateStation(orgId, stationId, upsertStation));
    }
}
