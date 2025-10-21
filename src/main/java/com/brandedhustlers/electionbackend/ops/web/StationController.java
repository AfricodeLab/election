package com.brandedhustlers.electionbackend.ops.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.ops.domain.Station;
import com.brandedhustlers.electionbackend.ops.dto.UpsertStation;
import com.brandedhustlers.electionbackend.ops.service.StationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {
    private final StationService svc;
    private final HttpServletRequest httpServletRequest;
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;

    public StationController(StationService svc,
                             HttpServletRequest httpServletRequest,
                             JWTService jwtService,
                             AppUserRepository appUserRepository) {
        this.svc = svc;
        this.httpServletRequest = httpServletRequest;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
    }

    private UUID getAppUserId(HttpServletRequest request) {
        return jwtService.extractAppUserId(request);
    }

    private Optional<AppUser> getUser(UUID userId) {
        return appUserRepository.findById(userId);
    }

    private Organization getUserOrg(AppUser appUser) {
        return appUser.getAppUserOrganization();
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody UpsertStation createStation) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return ResponseEntity.ok(svc.createStation(creatorOrg.getOrganizationId(), createStation));
    }

    @GetMapping("/get/{stationId}")
    public ResponseEntity<Station> getStation(@PathVariable UUID stationId) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return svc.getStation(creatorOrg.getOrganizationId(), stationId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/get-by-launch")
    public ResponseEntity<?> listByLaunch(@RequestParam UUID launchId, Pageable pageable) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();


        return ResponseEntity.ok(svc.listStationByLaunch(creatorOrg.getOrganizationId(), launchId, pageable));
    }

    @PutMapping("/update/{stationId}")
    public ResponseEntity<?> update(@PathVariable UUID stationId,
                                    @RequestBody UpsertStation upsertStation) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return ResponseEntity.ok(svc.updateStation(creatorOrg.getOrganizationId(), stationId, upsertStation));
    }
}
