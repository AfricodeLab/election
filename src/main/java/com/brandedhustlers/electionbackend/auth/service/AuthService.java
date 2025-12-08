package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.dto.*;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.InvalidRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserAlreadyExists;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.mapper.OrganizationMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService implements IAuthService{
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

    public AuthService(JWTService jwtService,
                       AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       CloudinaryService cloudinaryService,
                       HttpServletRequest request,
                       RoleRepository roleRepository,
                       GetUser getUser, OrganizationService organizationService, OrganizationMapper organizationMapper, OrganizationMapper organizationMapper1) {
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

    // Check for duplicate username
        if (appUserRepository.existsByUsername(registrationRequest.username())) {
            throw new UserAlreadyExists("Username already taken");
        }

        // Create organization first
        OrganizationDTO newOrgDto = organizationService.createOrganization(
                registrationRequest.userOrg()
        );
        Organization organization = organizationMapper.dtoToEntity(newOrgDto);

        // Create user
        AppUser newUser = AppUser.builder()
                .firstName(registrationRequest.firstName())
                .lastName(registrationRequest.lastName())
                .appUserEmail(registrationRequest.email())
                .username(registrationRequest.username())
                .password(passwordEncoder.encode(registrationRequest.password()))
                .userRole(UserRole.ORG_ADMIN)
                .appUserOrganization(organization)
                .build();

        appUserRepository.save(newUser);

        return new RegistrationResponse(
                true,
                newUser.getUserRole().name()
        );
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest, HttpServletResponse response) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(getIdentity(loginRequest), loginRequest.password())
        );
        UserPrincipal authenticationPrincipal= (UserPrincipal) authentication.getPrincipal();

        AppUser authenticatedUser = appUserRepository.findById(authenticationPrincipal.getAppUserId())
                .orElseThrow(() -> new UserNotFound("user not found."));
        String token = jwtService
                .generateToken(authenticatedUser.getAppUserEmail(),
                        360000000000L,
                        authenticatedUser.getAppUserId(),
                        authenticatedUser.getAppUserEmail());

        ResponseCookie jwtCookie = ResponseCookie.from("JWTAccess_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(3600)
                .build();
        response.setHeader("Set-Cookie", jwtCookie.toString());

        return new LoginResponse(
                true,
                token
        );
    }

    @Override
    public CreateUserResponse createUser(CreateUserRequest createUserRequest, MultipartFile userImage) {
        AppUser creator = getUser.getCurrentAuthenticatedUser();

        Role userRole = roleRepository.findByRoleName(createUserRequest.userRole())
                .orElseThrow(() -> new InvalidRole("Role not found"));

        AppUser newUser = AppUser.builder()
                .firstName(createUserRequest.firstName())
                .lastName(createUserRequest.lastName())
                .username(createUserRequest.username() == null ? null : createUserRequest.username())
                .appUserEmail(createUserRequest.companyEmail())
                .organizationName(creator.getOrganizationName())
                .appUserOrganization(creator.getAppUserOrganization())
                .password(passwordEncoder.encode(createUserRequest.defaultPassword()))
                .userRole(UserRole.valueOf(createUserRequest.userRole()))
                .userPreferences(null)
                .roles(userRole == null ? new HashSet<>() : Set.of(userRole))
                .createdBy(creator)
                .deleteYn(DeleteYn.N)
                .userProfilePicture(userImage != null ? cloudinaryService.uploadFile(userImage) : null)
                .build();

        appUserRepository.save(newUser);
        return new CreateUserResponse(
                true,
                newUser.getUserRole().name()
        );
    }

    private String getIdentity(LoginRequest request){
        return request.email() == null ? request.username() : request.email();
    }
}
