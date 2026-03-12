package com.brandedhustlers.electionbackend.ops.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.ops.dto.EnrolDevice;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;
import com.brandedhustlers.electionbackend.ops.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService svc;
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;
    private final HttpServletRequest httpServletRequest;

    public DeviceController(DeviceService svc,
                            JWTService jwtService,
                            AppUserRepository appUserRepository,
                            HttpServletRequest httpServletRequest) {
        this.svc = svc;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.httpServletRequest = httpServletRequest;
    }

    private Organization requireActiveOrg(AppUser user) {
        Organization org = user.getActiveOrganization();
        if (org == null) {
            throw new AccessDeniedException("No active organization. Please join or switch to an organization first.");
        }
        return org;
    }

    private UUID getAppUserId(HttpServletRequest request) {
        return jwtService.extractAppUserId(request);
    }

    @PostMapping("/enroll")
    public ResponseEntity<?> enroll(@RequestBody EnrolDevice enrolDevice) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = requireActiveOrg(launchCreator);

        return new ResponseEntity<>(svc.enrollDevice(creatorOrg.getOrganizationId(),enrolDevice), HttpStatus.OK);
    }

    @GetMapping("/get/{deviceId}")
    public ResponseEntity<?> get(@PathVariable UUID deviceId) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = requireActiveOrg(launchCreator);

        return new ResponseEntity<>(svc.get(creatorOrg.getOrganizationId(), deviceId), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<?> listDevices(@RequestParam(required = false) UUID stationId,
                                         @RequestParam(required = false) DeviceStatus status, Pageable pageable) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = requireActiveOrg(launchCreator);

        return new ResponseEntity<>(svc.listDevices(creatorOrg.getOrganizationId(), stationId, status, pageable), HttpStatus.OK);
    }

    @PutMapping("/{deviceId}/rebind")
    public ResponseEntity<?> rebind(@PathVariable UUID deviceId,
                                    @RequestParam UUID newStationId) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = requireActiveOrg(launchCreator);

        return new ResponseEntity<>(svc.rebind(creatorOrg.getOrganizationId(), deviceId, newStationId), HttpStatus.OK);
    }

    @PostMapping("/{deviceId}/heartbeat")
    public ResponseEntity<?> heartbeat(@PathVariable UUID deviceId,
                                       @RequestParam(required = false) Long latencyMs,
                                       @RequestParam(required = false) String appVersion,
                                       @RequestParam(required = false) Instant deviceTime) {

        AppUser launchCreator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found."));
        Organization creatorOrg = requireActiveOrg(launchCreator);

        // Note: in production this endpoint must authenticate device credentials, not user JWTs. Header kept for org scoping.
        return new ResponseEntity<>(svc.recordHeartbeat(creatorOrg.getOrganizationId(), deviceId, latencyMs, appVersion, deviceTime), HttpStatus.OK);
    }
}