package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.AddUserToOrg;
import com.brandedhustlers.electionbackend.auth.dto.CreateOrganizationRequest;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.auth.service.OrganizationService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final GetUser getUser;

    public OrganizationController(OrganizationService organizationService,
                                  GetUser getUser) {
        this.organizationService = organizationService;
        this.getUser = getUser;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createOrganization(
            @RequestBody CreateOrganizationRequest request
    ) {
        return new ResponseEntity<>(organizationService.createOrganization(request, getUser.getCurrentAuthenticatedUserId()), HttpStatus.CREATED);
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
        return new ResponseEntity<>("Deactivated.", HttpStatus.OK);
    }

    @PutMapping("/switch/{orgId}")
    public ResponseEntity<?> switchActiveOrg(@PathVariable UUID orgId) {
        organizationService.switchActiveOrg(getUser.getCurrentAuthenticatedUserId(), orgId);
        return new ResponseEntity<>("Active organization switched", HttpStatus.OK);
    }

    @GetMapping("/my-orgs")
    public ResponseEntity<?> myOrganizations() {
        return new ResponseEntity<>(organizationService.getUserOrganizations(getUser.getCurrentAuthenticatedUserId()), HttpStatus.OK);
    }
}
