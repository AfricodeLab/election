package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.controller.AuthController;
import com.brandedhustlers.electionbackend.auth.domain.*;
import com.brandedhustlers.electionbackend.auth.dto.*;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.InvalidRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserAlreadyExists;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.mapper.AppUserMapper;
import com.brandedhustlers.electionbackend.auth.mapper.OrganizationMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import com.brandedhustlers.electionbackend.auth.repository.UserOrganizationRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.exceptions.RefreshTokenException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class AuthService implements IAuthService {
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CloudinaryService cloudinaryService;
    private final HttpServletRequest request;
    private final RoleRepository roleRepository;
    private final GetUser getUser;
    private final OrganizationService organizationService;
    private final OrganizationMapper organizationMapper;
    private final AppUserMapper appUserMapper;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserOrganizationRepository userOrganizationRepository;
    private final MessagingService messagingService;
    private final MfaService mfaService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public AuthService(JWTService jwtService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            CloudinaryService cloudinaryService,
            HttpServletRequest request,
            RoleRepository roleRepository,
            GetUser getUser, OrganizationService organizationService, OrganizationMapper organizationMapper,
            OrganizationMapper organizationMapper1, AppUserMapper appUserMapper,
            OrganizationRepository organizationRepository, RefreshTokenService refreshTokenService,
            UserOrganizationRepository userOrganizationRepository,
            MessagingService messagingService,
            MfaService mfaService) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.cloudinaryService = cloudinaryService;
        this.request = request;
        this.roleRepository = roleRepository;
        this.getUser = getUser;
        this.organizationService = organizationService;
        this.organizationMapper = organizationMapper1;
        this.appUserMapper = appUserMapper;
        this.organizationRepository = organizationRepository;
        this.refreshTokenService = refreshTokenService;
        this.userOrganizationRepository = userOrganizationRepository;
        this.messagingService = messagingService;
        this.mfaService = mfaService;
    }

    @Override
    @Transactional
    public RegistrationResponse register(RegistrationRequest registrationRequest) {
        // Validate input
        if (registrationRequest == null || registrationRequest.email() == null) {
            throw new IllegalArgumentException("Registration request and email cannot be null");
        }

        // Check for existing user
        Optional<AppUser> potentialConflict = appUserRepository
                .findByAppUserEmail(registrationRequest.email());

        if (potentialConflict.isPresent()) {
            throw new UserAlreadyExists("An account with this email already exists");
        }

        // Derive a unique username from the email prefix (e.g. "alice" from "alice@example.com")
        String baseUsername = registrationRequest.email().split("@")[0].toLowerCase()
                .replaceAll("[^a-z0-9._-]", "");
        String username = baseUsername;
        if (appUserRepository.existsByUsername(username)) {
            username = baseUsername + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        }

        // Generate email verification token
        String verificationToken = UUID.randomUUID().toString();

        // Create user
        AppUser newUser = AppUser.builder()
                .firstName(registrationRequest.firstName())
                .lastName(registrationRequest.lastName())
                .appUserEmail(registrationRequest.email())
                .username(username)
                .password(passwordEncoder.encode(registrationRequest.password()))
                .userRole(UserRole.ORG_ADMIN)
                .emailVerified(false)
                .emailVerificationToken(verificationToken)
                .emailVerificationExpiry(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        appUserRepository.save(newUser);

        // Create organization — this also creates a UserOrganization row
        // linking the creator as ORG_ADMIN with activeOrg=true.
        // userOrg may be null if the frontend sends organizationName as a flat field.
        CreateOrganizationRequest orgRequest = registrationRequest.userOrg() != null
                ? registrationRequest.userOrg()
                : new CreateOrganizationRequest(registrationRequest.organizationName(), null);
        organizationService.createOrganization(orgRequest, newUser.getAppUserId());

        // Send verification email asynchronously (fire-and-forget; log on failure)
        String verificationUrl = appBaseUrl + "/api/v1/auth/verify-email?token=" + verificationToken;
        try {
            messagingService.sendVerificationEmail(
                    newUser.getAppUserEmail(),
                    newUser.getFirstName(),
                    verificationUrl);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", newUser.getAppUserEmail(), e.getMessage());
        }

        return new RegistrationResponse(
                true,
                newUser.getUserRole().name());
    }

    @Override
    public void verifyEmail(String token) {
        AppUser user = appUserRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        if (user.getEmailVerificationExpiry() == null
                || Instant.now().isAfter(user.getEmailVerificationExpiry())) {
            throw new IllegalArgumentException("Verification token has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiry(null);
        appUserRepository.save(user);
        log.info("Email verified for user {}", user.getAppUserId());
    }

    @Override
    public void resendVerificationEmail(String email) {
        AppUser user = appUserRepository.findByAppUserEmail(email)
                .orElseThrow(() -> new UserNotFound("No account found with that email address"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email address is already verified");
        }

        String verificationToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        appUserRepository.save(user);

        String verificationUrl = appBaseUrl + "/api/v1/auth/verify-email?token=" + verificationToken;
        try {
            messagingService.sendVerificationEmail(
                    user.getAppUserEmail(),
                    user.getFirstName(),
                    verificationUrl);
        } catch (MessagingException e) {
            log.error("Failed to resend verification email to {}: {}", user.getAppUserEmail(), e.getMessage());
            throw new RuntimeException("Could not send verification email. Please try again later.");
        }
        log.info("Verification email resent to {}", email);
    }

    @Override
    public void forgotPassword(String email) {
        // Deliberately vague: no exception thrown if email not found (prevents user enumeration)
        appUserRepository.findByAppUserEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            appUserRepository.save(user);

            // Point at the frontend reset page, not the API endpoint.
        // The frontend collects the new password and POSTs to /api/v1/auth/reset-password.
        String resetUrl = appBaseUrl + "/reset-password?token=" + token;
            messagingService.sendPasswordResetEmail(user.getAppUserEmail(), user.getFirstName(), resetUrl);
            log.info("Password reset email queued for user {}", user.getAppUserId());
        });
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        AppUser user = appUserRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (user.getPasswordResetExpiry() == null || Instant.now().isAfter(user.getPasswordResetExpiry())) {
            throw new IllegalArgumentException("Reset token has expired. Please request a new one.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must differ from the current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        appUserRepository.save(user);
        log.info("Password reset completed for user {}", user.getAppUserId());
    }

    @Override
    public void enableMfa() {
        AppUser user = getUser.getCurrentAuthenticatedUser();
        user.setMfaEnabled(true);
        appUserRepository.save(user);
        log.info("MFA enabled for user {}", user.getAppUserId());
    }

    @Override
    public void disableMfa() {
        AppUser user = getUser.getCurrentAuthenticatedUser();
        user.setMfaEnabled(false);
        appUserRepository.save(user);
        log.info("MFA disabled for user {}", user.getAppUserId());
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest, HttpServletResponse response) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(getIdentity(loginRequest), loginRequest.password()));
        UserPrincipal authenticationPrincipal = (UserPrincipal) authentication.getPrincipal();

        AppUser authenticatedUser = appUserRepository.findById(authenticationPrincipal.getAppUserId())
                .orElseThrow(() -> new UserNotFound("user not found."));

        // If MFA is enabled, issue a short-lived pending token and ask for OTP
        if (authenticatedUser.isMfaEnabled()) {
            String mfaToken = mfaService.initiateMfa(authenticatedUser.getAppUserId());
            log.info("MFA challenge initiated for user {}", authenticatedUser.getAppUserId());
            return LoginResponse.mfaChallenge(mfaToken);
        }

        String token = jwtService.generateAccessToken(
                authenticatedUser.getAppUserEmail(),
                authenticatedUser.getAppUserId(),
                authenticatedUser.getAppUserEmail());

        String refreshToken = refreshTokenService.createRefreshToken(authenticatedUser.getAppUserId()).getToken();

        ResponseCookie jwtCookie = ResponseCookie.from("JWTAccess_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(3600)
                .build();
        response.setHeader("Set-Cookie", jwtCookie.toString());

        return LoginResponse.standard(
                true,
                token,
                refreshToken,
                authenticatedUser.getUsername(),
                authenticatedUser.getAppUserEmail());
    }

    @Override
    public CreateUserResponse createUser(CreateUserRequest createUserRequest) {
        AppUser creator = getUser.getCurrentAuthenticatedUser();

        Role userRole = roleRepository.findByRoleName(createUserRequest.userRole())
                .orElseThrow(() -> new InvalidRole("Role not found"));

        AppUser newUser = AppUser.builder()
                .firstName(createUserRequest.firstName())
                .lastName(createUserRequest.lastName())
                .username(createUserRequest.username() == null ? null : createUserRequest.username())
                .appUserEmail(createUserRequest.companyEmail())
                .password(passwordEncoder.encode(createUserRequest.defaultPassword()))
                .userRole(UserRole.valueOf(createUserRequest.userRole()))
                .userPreferences(null)
                .roles(userRole == null ? new HashSet<>() : Set.of(userRole))
                .createdBy(creator)
                .deleteYn(DeleteYn.N)
                // Admin-provisioned accounts are pre-verified — the admin owns the email
                .emailVerified(true)
                .build();

        appUserRepository.save(newUser);

        // Add new user to creator's active org
        Organization creatorOrg = creator.getActiveOrganization();
        if (creatorOrg != null) {
            userOrganizationRepository.save(UserOrganization.builder()
                    .appUser(newUser)
                    .organization(creatorOrg)
                    .roleInOrg(newUser.getUserRole().name())
                    .activeOrg(true)
                    .build());
        }

        return new CreateUserResponse(
                true,
                newUser.getUserRole().name());
    }

    public Page<AppUserDTO> getUsers(UUID orgId, Pageable pageable) {
        List<AppUserDTO> appUsers = appUserRepository.getUsersInOrg(orgId)
                .stream()
                .map(appUserMapper::entityToDTO)
                .toList();

        return new PageImpl<>(appUsers, pageable, appUsers.size());
    }

    /**
     * Updates the base system role (userRole enum) for an existing user.
     *
     * <p>Rules:
     * <ul>
     *   <li>SUPER_ADMIN may promote to any role including ADMIN/SUPER_ADMIN.</li>
     *   <li>ADMIN may assign any role except ADMIN/SUPER_ADMIN (no self-promotion).</li>
     *   <li>ORG_ADMIN may only assign roles within their own org (ORG_ADMIN and below).</li>
     *   <li>Nobody can demote a SUPER_ADMIN except another SUPER_ADMIN.</li>
     * </ul>
     */
    @Transactional
    public void updateUserRole(UUID targetUserId, UserRole newRole) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        AppUser target = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFound("User not found: " + targetUserId));

        UserRole callerRole = caller.getUserRole();

        // Nobody can demote a SUPER_ADMIN unless they are also SUPER_ADMIN
        if (target.getUserRole() == UserRole.SUPER_ADMIN && callerRole != UserRole.SUPER_ADMIN) {
            throw new com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException(
                    "Only a SUPER_ADMIN can modify another SUPER_ADMIN's role");
        }

        // ADMIN cannot assign ADMIN or SUPER_ADMIN (prevents privilege escalation)
        if (callerRole == UserRole.ADMIN
                && (newRole == UserRole.ADMIN || newRole == UserRole.SUPER_ADMIN)) {
            throw new com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException(
                    "ADMIN cannot assign platform-level roles. Contact a SUPER_ADMIN.");
        }

        // ORG_ADMIN can only reassign within their own org
        if (callerRole == UserRole.ORG_ADMIN) {
            if (newRole == UserRole.ADMIN || newRole == UserRole.SUPER_ADMIN || newRole == UserRole.ORG_ADMIN) {
                throw new com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException(
                        "ORG_ADMIN cannot assign admin-level roles");
            }
            // Ensure target belongs to caller's org
            Organization callerOrg = caller.getActiveOrganization();
            Organization targetOrg = target.getActiveOrganization();
            if (callerOrg == null || targetOrg == null
                    || !callerOrg.getOrganizationId().equals(targetOrg.getOrganizationId())) {
                throw new com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException(
                        "You can only update roles for users in your organization");
            }
        }

        target.setUserRole(newRole);

        // Sync the system Role entity to match — ensures the roles Set stays consistent
        // with the new userRole enum value so both gates agree
        roleRepository.findByRoleName(newRole.name()).ifPresent(systemRole -> {
            // Remove any other system roles (orgId = null) from the user's roles set
            target.getRoles().removeIf(r -> r.getOrgId() == null);
            target.getRoles().add(systemRole);
        });

        appUserRepository.save(target);
        log.info("User {} role updated to {} by {}", targetUserId, newRole, caller.getAppUserId());
    }

    public void deactivateUser(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User with ID: " + userId + " not found."));
        user.setDeleteYn(DeleteYn.Y);
        appUserRepository.save(user);
        log.info("User {} deactivated", userId);
    }

    public void activateUser(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User with ID: " + userId + " not found."));
        user.setDeleteYn(DeleteYn.N);
        appUserRepository.save(user);
        log.info("User {} activated", userId);
    }

    public void changePassword(AuthController.ChangePasswordCmd changePasswordCmd) {
        var currUser = getUser.getCurrentAuthenticatedUser();

        if (!passwordEncoder.matches(changePasswordCmd.oldPassword(), currUser.getPassword())) {
            throw new BadCredentialsException("User provided the wrong password");
        }

        if (passwordEncoder.matches(changePasswordCmd.newPassword(), currUser.getPassword())) {
            throw new BadCredentialsException("User provided the same password");
        }
        currUser.setPassword(passwordEncoder.encode(changePasswordCmd.newPassword()));
        appUserRepository.save(currUser);
    }

    @Override
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        return refreshTokenService.findByToken(request.refreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtService.generateAccessToken(
                            user.getAppUserEmail(),
                            user.getAppUserId(),
                            user.getAppUserEmail());
                    return new TokenRefreshResponse(accessToken, request.refreshToken());
                })
                .orElseThrow(() -> new RefreshTokenException("Refresh token is not in database!"));
    }

    private String getIdentity(LoginRequest request) {
        if (request.email() != null && !request.email().isBlank()) {
            return request.email();
        }
        if (request.username() != null && !request.username().isBlank()) {
            return request.username();
        }
        throw new IllegalArgumentException("Login requires either an email or a username.");
    }
}
