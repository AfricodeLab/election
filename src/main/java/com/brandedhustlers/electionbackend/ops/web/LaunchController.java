package com.brandedhustlers.electionbackend.ops.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import com.brandedhustlers.electionbackend.ops.service.LaunchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LaunchController: thin HTTP façade; performs header/param extraction and delegates to LaunchService.
 * Justification: controllers stay free of business logic, easing testability and keeping invariants centralized.
 */
@RestController
@RequestMapping("/api/v1/launches")
public class LaunchController {
    private final LaunchService svc;
    private final JWTService jwtService;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final HttpServletRequest httpServletRequest;

    public LaunchController(LaunchService svc,
                            JWTService jwtService,
                            OrganizationRepository organizationRepository,
                            AppUserRepository appUserRepository, HttpServletRequest httpServletRequest) {
        this.svc = svc;
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.httpServletRequest = httpServletRequest;
    }

    private UUID getAppUserId(HttpServletRequest request) {
        return jwtService.extractAppUserId(request);
    }


    @PostMapping
    public ResponseEntity<?> create(@RequestParam String launchName,
                                    @RequestParam(required = false) String launchDescription,
                                    @RequestParam(required = false) Instant startAt,
                                    @RequestParam(required = false) Instant endAt) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return new ResponseEntity<>(svc.create(creatorOrg.getOrganizationId(), launchName, launchDescription, startAt, endAt, launchCreator), HttpStatus.CREATED);
    }

    @GetMapping("/get-launch/{launchId}")
    public ResponseEntity<?> getLaunch(@PathVariable UUID launchId) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return new ResponseEntity<>(svc.get(creatorOrg.getOrganizationId(), launchId), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) LaunchStatus status,
                                  @RequestParam(required = false) String q,
                                  Pageable pageable
    ) {
        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return new ResponseEntity<>(svc.list(creatorOrg.getOrganizationId(), status, q, pageable), HttpStatus.OK);
    }

    @PostMapping("/{launchId}/attach-elections")
    public ResponseEntity<?> attach(@PathVariable UUID launchId,
                                    @RequestBody List<UUID> electionIds) {
        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        svc.attachElections(creatorOrg.getOrganizationId(), launchId, electionIds, launchCreator);
        return new ResponseEntity<>("Elections attached successfully", HttpStatus.ACCEPTED);
    }

    @DeleteMapping("/{launchId}/detach-elections")
    public ResponseEntity<?> detach(@PathVariable UUID launchId,
                                    @RequestBody List<UUID> electionIds) {
        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        svc.detachElections(creatorOrg.getOrganizationId(), launchId, electionIds, launchCreator);
        return new ResponseEntity<>("Election(s) detached successfully", HttpStatus.ACCEPTED);
    }

    @PutMapping("/{launchId}/status")
    public ResponseEntity<?> transition(@PathVariable UUID launchId,
                                        @RequestParam LaunchStatus status) {
        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = launchCreator.getAppUserOrganization();

        return new ResponseEntity<>(svc.transitionStatus(creatorOrg.getOrganizationId(), launchId, status, launchCreator), HttpStatus.OK);
    }
}