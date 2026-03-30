package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.CreateUserRequest;
import com.brandedhustlers.electionbackend.auth.dto.LoginRequest;
import com.brandedhustlers.electionbackend.auth.dto.MfaVerifyRequest;
import com.brandedhustlers.electionbackend.auth.dto.RegistrationRequest;
import com.brandedhustlers.electionbackend.auth.dto.TokenRefreshRequest;
import com.brandedhustlers.electionbackend.auth.service.AuthService;
import com.brandedhustlers.electionbackend.auth.service.MfaService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final MfaService mfaService;
    private final HttpServletResponse servletResponse;
    private final GetUser getUser;

    public AuthController(AuthService authService,
            MfaService mfaService,
            HttpServletResponse response,
            GetUser getUser) {
        this.authService = authService;
        this.mfaService = mfaService;
        this.servletResponse = response;
        this.getUser = getUser;
    }

    @PostMapping("/register-user")
    @RateLimiter(name = "auth_paths")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegistrationRequest registrationRequest) {
        return new ResponseEntity<>(authService.register(registrationRequest), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @RateLimiter(name = "auth_paths")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {
        return new ResponseEntity<>(authService.login(request, servletResponse), HttpStatus.OK);
    }

    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return new ResponseEntity<>(authService.createUser(request), HttpStatus.CREATED);
    }

    @PostMapping("/refresh")
    @RateLimiter(name = "auth_paths")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return new ResponseEntity<>(authService.refreshToken(request), HttpStatus.OK);
    }

    @PostMapping("/mfa/verify")
    @RateLimiter(name = "auth_paths")
    public ResponseEntity<?> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return new ResponseEntity<>(mfaService.verify(request), HttpStatus.OK);
    }

    @GetMapping("/get-users")
    public ResponseEntity<?> getOrgUser(Pageable pageable) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        return new ResponseEntity<>(authService.getUsers(orgId, pageable), HttpStatus.OK);
    }

    @DeleteMapping("/deactivate/{userId}")
    public ResponseEntity<?> deactivateUser(@PathVariable UUID userId) {
        authService.deactivateUser(userId);

        return ResponseEntity.ok("User with ID: " + userId + " deactivated.");
    }

    @DeleteMapping("/activate/{userId}")
    public ResponseEntity<?> activateUser(@PathVariable UUID userId) {
        authService.activateUser(userId);

        return ResponseEntity.ok("User with ID: " + userId + " activated.");
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully. You can now log in.");
    }

    @PostMapping("/resend-verification")
    @RateLimiter(name = "auth_paths")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok("Verification email sent. Please check your inbox.");
    }

    @PatchMapping("/password/change")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordCmd changePasswordCmd) {
        authService.changePassword(changePasswordCmd);
        return new ResponseEntity<>(new ChangePasswordResponse(true, "Password changed successfully"), HttpStatus.OK);
    }

    /*
     * @PostMapping("/video")
     * public ResponseEntity<?> video(@RequestPart(name = "video") MultipartFile
     * video){
     * cloudinaryService.uploadFile(video);
     * return new ResponseEntity<>(HttpStatus.OK);
     * }
     */

    public record ChangePasswordCmd(String oldPassword, String newPassword) {
    }

    record ChangePasswordResponse(boolean success, String message) {
    } // for anyone going through this codebase, I put the records here because I
      // really didn't see why I should create a different file for them...since
      // they'd only ever be use here...although the first one was used in the
      // service...but yeah, you get me
}
