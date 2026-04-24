package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;
import com.brandedhustlers.electionbackend.voter.dto.UpdateVoterContactCmd;
import com.brandedhustlers.electionbackend.voter.service.VoterImportService;
import com.brandedhustlers.electionbackend.voter.service.VoterService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voters")
public class VoterController {
    private final VoterService voterService;
    private final VoterImportService importService;
    private final GetUser getUser;

    private static final String UPLOAD_DIR = "uploads/voter-imports/";

    public VoterController(VoterService voterService,
                           VoterImportService importService,
                           GetUser getUser) {
        this.voterService = voterService;
        this.importService = importService;
        this.getUser = getUser;
    }

/*    private Optional<AppUser> getAppUser(HttpServletRequest httpServletRequest) {
        return appUserRepository
                .findById(jwtService.extractAppUserId(httpServletRequest));
    }*/

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateVoterCmd cmd) {
        UUID actorOrgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (actorOrgId != null) {
            return ResponseEntity.ok(voterService.create(actorOrgId, cmd));
        }
        return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
    }



    @PostMapping("/assign")
    public ResponseEntity<?> assign(@Valid @RequestBody AssignVotersCmd cmd) {
        UUID actorOrgId = getUser.getCurrentAuthenticatedUserOrgId();
        voterService.assignToElection(actorOrgId, cmd);
        return new ResponseEntity<>("Voters assigned successfully", HttpStatus.OK);
    }

    @PostMapping("/import")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file) {

        AppUser creator = getUser.getCurrentAuthenticatedUser();
        if (creator != null) {
            try {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "File is empty"));
                }

                String fileName = file.getOriginalFilename();
                if (fileName == null || (!fileName.endsWith(".csv") &&
                        !fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Only CSV and Excel files are supported"));
                }

                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String uniqueFileName = UUID.
                        randomUUID() + "_" + fileName;
                Path filePath = uploadPath.resolve(uniqueFileName);
                Files.copy(file.getInputStream(),
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING);

                UUID jobId = importService.startImport(filePath.toString());

                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobId);
                response.put("message", "Import started successfully");

                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
            }
        } else {
            return new ResponseEntity<>(Map.of(
                    "error", "User not found"
            ), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getStatus(@PathVariable UUID jobId) {
        UUID actorOrgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (actorOrgId != null) {
            try {
                ImportStatusDTO status = importService.status(jobId);
                return ResponseEntity.ok(status);
            } catch (RuntimeException e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } else {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/org/voters")
    public ResponseEntity<?> getOrgVoters(Pageable pageable){
        return new ResponseEntity<>(voterService.getOrgVoters(pageable), HttpStatus.OK);
    }

    @GetMapping("/{voterId}")
    public ResponseEntity<?> getById(@PathVariable UUID voterId) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (orgId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "No active organization."));
        }
        return ResponseEntity.ok(voterService.getById(orgId, voterId));
    }

    @PatchMapping("/{voterId}/contact")
    public ResponseEntity<?> updateContact(
            @PathVariable UUID voterId,
            @Valid @RequestBody UpdateVoterContactCmd cmd) {
        UUID actorOrgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (actorOrgId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "forbidden", "message", "No active organization. Please join or switch to an organization first."));
        }
        return ResponseEntity.ok(voterService.updateContact(actorOrgId, voterId, cmd));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (orgId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "forbidden", "message", "No active organization. Please join or switch to an organization first."));
        }
        return ResponseEntity.ok(voterService.search(orgId, q, limit));
    }
}
