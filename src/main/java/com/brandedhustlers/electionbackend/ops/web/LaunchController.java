package com.brandedhustlers.electionbackend.ops.web;

import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.ops.dto.CreateLaunchCmd;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import com.brandedhustlers.electionbackend.ops.service.LaunchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final AppUserRepository appUserRepository;
    private final HttpServletRequest httpServletRequest;
    private final GetUser getUser;

    public LaunchController(LaunchService svc,
                            JWTService jwtService,
                            AppUserRepository appUserRepository,
                            HttpServletRequest httpServletRequest,
                            GetUser getUser) {
        this.svc = svc;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.httpServletRequest = httpServletRequest;
        this.getUser = getUser;
    }

    private UUID getAppUserId(HttpServletRequest request) {
        return jwtService.extractAppUserId(request);
    }


    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLaunchCmd createLaunchCmd) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return new ResponseEntity<>(svc.create(orgId, createLaunchCmd.name(), createLaunchCmd.description(), createLaunchCmd.startAt(), createLaunchCmd.endAt()), HttpStatus.CREATED);
    }

    @GetMapping("/get-launch/{launchId}")
    public ResponseEntity<?> getLaunch(@PathVariable UUID launchId) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return new ResponseEntity<>(svc.get(orgId, launchId), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) LaunchStatus status,
                                  Pageable pageable
    ) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return new ResponseEntity<>(svc.list(orgId, status, pageable), HttpStatus.OK);
    }

    @PostMapping("/{launchId}/attach-elections")
    public ResponseEntity<?> attach(@PathVariable UUID launchId,
                                    @RequestBody List<UUID> electionIds) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        svc.attachElections(orgId, launchId, electionIds);
        return new ResponseEntity<>("Elections attached successfully", HttpStatus.ACCEPTED);
    }

    @DeleteMapping("/{launchId}/detach-elections")
    public ResponseEntity<?> detach(@PathVariable UUID launchId,
                                    @RequestBody List<UUID> electionIds) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        svc.detachElections(orgId, launchId, electionIds);
        return new ResponseEntity<>("Election(s) detached successfully", HttpStatus.ACCEPTED);
    }

    @PutMapping("/{launchId}/status")
    public ResponseEntity<?> transition(@PathVariable UUID launchId,
                                        @RequestParam LaunchStatus status) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return new ResponseEntity<>(svc.transitionStatus(orgId, launchId, status), HttpStatus.OK);
    }
}