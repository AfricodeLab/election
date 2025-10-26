package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.AddUserToOrg;
import com.brandedhustlers.electionbackend.auth.dto.CreateOrganizationRequest;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.auth.service.IOrganizationService;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {
    private final IOrganizationService organizationService;
    private final HttpServletRequest httpServletRequest;
    private final JWTService jwtService;

    public OrganizationController(IOrganizationService organizationService,
                                  HttpServletRequest httpServletRequest,
                                  JWTService jwtService) {
        this.organizationService = organizationService;
        this.httpServletRequest = httpServletRequest;
        this.jwtService = jwtService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createOrganization(
            @RequestBody CreateOrganizationRequest request
    ) {
        return new ResponseEntity<>(organizationService.createOrganization(request), HttpStatus.CREATED);
    }

    @PutMapping("/update/{orgId}")
    public ResponseEntity<?> updateOrganization(
            @PathVariable UUID orgId,
            @RequestBody OrganizationDTO organizationDTO
    ) {
        return new ResponseEntity<>(organizationService.updateOrganization(orgId, organizationDTO), HttpStatus.OK);
    }

    @GetMapping("/all")
    public ResponseEntity<?> listOrganizations(Pageable pageable) {
        return new ResponseEntity<>(organizationService.listOrganizations(pageable), HttpStatus.OK);
    }

    @PutMapping("/add-user")
    public ResponseEntity<?> addUserToOrganization(
            @RequestBody AddUserToOrg addUserToOrg
    ) {
        organizationService.addUserToOrganization(addUserToOrg);
        return new ResponseEntity<>("User added to org successfully", HttpStatus.OK);
    }

    @PutMapping("/deactivate/{orgId}")
    public ResponseEntity<?> deactivateOrg(
            @PathVariable UUID orgId
    ) {
        organizationService.deactivateOrganization(orgId);
        return new ResponseEntity<>("Deactivated.",HttpStatus.OK);
    }
}
