package com.brandedhustlers.electionbackend.ops.web;

import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.ops.dto.CreateLaunchCmd;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import com.brandedhustlers.electionbackend.ops.service.LaunchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * LaunchController: thin HTTP façade; performs header/param extraction and
 * delegates to LaunchService.
 * Justification: controllers stay free of business logic, easing testability
 * and keeping invariants centralized.
 */
@RestController
@RequestMapping("/api/v1/launches")
public class LaunchController {
    private final LaunchService svc;
    private final GetUser getUser;

    public LaunchController(LaunchService svc,
            GetUser getUser) {
        this.svc = svc;
        this.getUser = getUser;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateLaunchCmd createLaunchCmd) {
        return new ResponseEntity<>(
                svc.create(getUser.getCurrentAuthenticatedUserOrgId(), createLaunchCmd.name(),
                        createLaunchCmd.description(), createLaunchCmd.startAt(), createLaunchCmd.endAt()),
                HttpStatus.CREATED);
    }

    @GetMapping("/get-launch/{launchId}")
    public ResponseEntity<?> getLaunch(@PathVariable UUID launchId) {
        return new ResponseEntity<>(svc.get(getUser.getCurrentAuthenticatedUserOrgId(), launchId), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) LaunchStatus status,
            Pageable pageable) {
        return new ResponseEntity<>(svc.list(getUser.getCurrentAuthenticatedUserOrgId(), status, pageable),
                HttpStatus.OK);
    }

    @PostMapping("/{launchId}/attach-elections")
    public ResponseEntity<?> attach(@PathVariable UUID launchId,
            @RequestBody List<UUID> electionIds) {
        svc.attachElections(getUser.getCurrentAuthenticatedUserOrgId(), launchId, electionIds);
        return new ResponseEntity<>("Elections attached successfully", HttpStatus.ACCEPTED);
    }

    @DeleteMapping("/{launchId}/detach-elections")
    public ResponseEntity<?> detach(@PathVariable UUID launchId,
            @RequestBody List<UUID> electionIds) {
        svc.detachElections(getUser.getCurrentAuthenticatedUserOrgId(), launchId, electionIds);
        return new ResponseEntity<>("Election(s) detached successfully", HttpStatus.ACCEPTED);
    }

    @PutMapping("/{launchId}/status")
    public ResponseEntity<?> transition(@PathVariable UUID launchId,
            @RequestParam LaunchStatus status) {
        return new ResponseEntity<>(svc.transitionStatus(getUser.getCurrentAuthenticatedUserOrgId(), launchId, status),
                HttpStatus.OK);
    }
}