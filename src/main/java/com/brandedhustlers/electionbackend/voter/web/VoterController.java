package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;
import com.brandedhustlers.electionbackend.voter.service.VoterImportService;
import com.brandedhustlers.electionbackend.voter.service.VoterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voters")
public class VoterController {
    private final VoterService voterService;
    private final VoterImportService importService;
    private final HttpServletRequest httpServletRequest;
    private final AppUserRepository appUserRepository;
    private final JWTService jwtService;

    private static final String UPLOAD_DIR = "uploads/voter-imports/";

    public VoterController(VoterService voterService,
                           VoterImportService importService,
                           HttpServletRequest httpServletRequest,
                           AppUserRepository appUserRepository,
                           JWTService jwtService) {
        this.voterService = voterService;
        this.importService = importService;
        this.httpServletRequest = httpServletRequest;
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
    }

    private Optional<AppUser> getAppUser(HttpServletRequest httpServletRequest) {
        return appUserRepository
                .findById(jwtService.extractAppUserId(httpServletRequest));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateVoterCmd cmd) {
        Optional<AppUser> creator = getAppUser(httpServletRequest);
        if (creator.isPresent()) {
            AppUser actor = creator.get();
            Organization actorOrg = actor.getAppUserOrganization();
            return ResponseEntity.ok(voterService.create(actorOrg.getOrganizationId(), cmd, actor));
        }
        return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
    }

    @GetMapping
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(defaultValue = "20") int limit) {
        Optional<AppUser> creator = getAppUser(httpServletRequest);
        if (creator.isPresent()) {
            AppUser actor = creator.get();
            Organization actorOrg = actor.getAppUserOrganization();

            return ResponseEntity.ok(voterService.search(actorOrg.getOrganizationId(), q, limit));
        }
        return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
    }

    @PostMapping("/assign")
    public ResponseEntity<?> assign(@Valid @RequestBody AssignVotersCmd cmd) {
        Optional<AppUser> creator = getAppUser(httpServletRequest);
        if (creator.isPresent()) {
            AppUser actor = creator.get();
            Organization actorOrg = actor.getAppUserOrganization();

            voterService.assignToElection(actorOrg.getOrganizationId(), cmd, actor);
            return new ResponseEntity<>("Voter Assigned Successfully", HttpStatus.OK);
        }
        return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file) {

        Optional<AppUser> creator = getAppUser(httpServletRequest);
        if (creator.isPresent()) {
            AppUser actor = creator.get();
            Organization actorOrg = actor.getAppUserOrganization();
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

                String uniqueFileName = UUID.randomUUID() + "_" + fileName;
                Path filePath = uploadPath.resolve(uniqueFileName);
                Files.copy(file.getInputStream(),
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING);

                UUID jobId = importService.startImport(actorOrg.getOrganizationId(), filePath.toString());

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
    public ResponseEntity<?> getStatus(
            @PathVariable UUID jobId) {
        Optional<AppUser> creator = getAppUser(httpServletRequest);
        if (creator.isPresent()) {
            AppUser actor = creator.get();
            Organization actorOrg = actor.getAppUserOrganization();
            try {
                ImportStatusDTO status = importService.status(actorOrg.getOrganizationId(), jobId);
                return ResponseEntity.ok(status);
            } catch (RuntimeException e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } else {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }
    }
}
