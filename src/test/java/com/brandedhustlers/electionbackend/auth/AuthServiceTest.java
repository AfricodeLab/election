package com.brandedhustlers.electionbackend.auth;

import com.brandedhustlers.electionbackend.auth.controller.AuthController;
import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.dto.*;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserAlreadyExists;
import com.brandedhustlers.electionbackend.auth.mapper.AppUserMapper;
import com.brandedhustlers.electionbackend.auth.mapper.OrganizationMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import com.brandedhustlers.electionbackend.auth.service.AuthService;
import com.brandedhustlers.electionbackend.auth.service.OrganizationService;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService - registration, login, and user management.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private JWTService jwtService;
        @Mock
        private AppUserRepository appUserRepository;
        @Mock
        private PasswordEncoder passwordEncoder;
        @Mock
        private AuthenticationManager authenticationManager;
        @Mock
        private CloudinaryService cloudinaryService;
        @Mock
        private HttpServletRequest request;
        @Mock
        private RoleRepository roleRepository;
        @Mock
        private GetUser getUser;
        @Mock
        private OrganizationService organizationService;
        @Mock
        private OrganizationMapper organizationMapper;
        @Mock
        private AppUserMapper appUserMapper;
        @Mock
        private OrganizationRepository organizationRepository;
        @Mock
        private HttpServletResponse response;

        @InjectMocks
        private AuthService authService;

        private UUID userId;
        private UUID orgId;
        private AppUser testUser;
        private Organization testOrg;

        @BeforeEach
        void setUp() {
                userId = UUID.randomUUID();
                orgId = UUID.randomUUID();

                testOrg = new Organization();
                testOrg.setOrganizationId(orgId);
                testOrg.setOrganizationName("Test Org");

                testUser = AppUser.builder()
                                .appUserId(userId)
                                .firstName("John")
                                .lastName("Doe")
                                .appUserEmail("john@test.com")
                                .username("johndoe")
                                .password("encodedPassword123")
                                .userRole(UserRole.ORG_ADMIN)
                                .appUserOrganization(testOrg)
                                .build();
        }

        @Test
        @DisplayName("Register user successfully - new user and org created")
        void testRegister_Success() {
                // Arrange
                CreateOrganizationRequest orgRequest = new CreateOrganizationRequest("New Org", "Description");
                RegistrationRequest request = new RegistrationRequest(
                                "John", "Doe", "john@test.com", "johndoe",
                                "New Org", "password123", orgRequest);

                OrganizationDTO orgDto = new OrganizationDTO(orgId, "New Org", "Description", true, null, null, null);

                when(appUserRepository.findByAppUserEmail("john@test.com")).thenReturn(Optional.empty());
                when(appUserRepository.existsByUsername("johndoe")).thenReturn(false);
                when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
                when(appUserRepository.save(any(AppUser.class))).thenAnswer(i -> {
                        AppUser u = i.getArgument(0);
                        u.setAppUserId(userId);
                        return u;
                });
                when(organizationService.createOrganization(any(), any())).thenReturn(orgDto);
                when(organizationMapper.dtoToEntity(orgDto)).thenReturn(testOrg);

                // Act
                RegistrationResponse result = authService.register(request);

                // Assert
                assertNotNull(result);
                assertTrue(result.success());
                assertEquals("ORG_ADMIN", result.userType());

                verify(appUserRepository, times(2)).save(any(AppUser.class));
                verify(organizationRepository, times(1)).save(testOrg);
        }

        @Test
        @DisplayName("Register user fails - email already exists")
        void testRegister_DuplicateEmail() {
                // Arrange
                CreateOrganizationRequest orgRequest = new CreateOrganizationRequest("New Org", "Description");
                RegistrationRequest request = new RegistrationRequest(
                                "John", "Doe", "john@test.com", "johndoe",
                                "New Org", "password123", orgRequest);

                when(appUserRepository.findByAppUserEmail("john@test.com")).thenReturn(Optional.of(testUser));

                // Act & Assert
                UserAlreadyExists exception = assertThrows(UserAlreadyExists.class,
                                () -> authService.register(request));

                assertTrue(exception.getMessage().contains("email already exists"));
                verify(appUserRepository, never()).save(any());
        }

        @Test
        @DisplayName("Register user fails - username already taken")
        void testRegister_DuplicateUsername() {
                // Arrange
                CreateOrganizationRequest orgRequest = new CreateOrganizationRequest("New Org", "Description");
                RegistrationRequest request = new RegistrationRequest(
                                "John", "Doe", "newemail@test.com", "johndoe",
                                "New Org", "password123", orgRequest);

                when(appUserRepository.findByAppUserEmail("newemail@test.com")).thenReturn(Optional.empty());
                when(appUserRepository.existsByUsername("johndoe")).thenReturn(true);

                // Act & Assert
                UserAlreadyExists exception = assertThrows(UserAlreadyExists.class,
                                () -> authService.register(request));

                assertTrue(exception.getMessage().contains("Username already taken"));
                verify(appUserRepository, never()).save(any());
        }

        @Test
        @DisplayName("Register user fails - null request")
        void testRegister_NullRequest() {
                // Act & Assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> authService.register(null));

                assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("Login successfully - returns token")
        void testLogin_Success() {
                // Arrange
                LoginRequest loginRequest = new LoginRequest(null, "john@test.com", "password123");

                UserPrincipal principal = mock(UserPrincipal.class);
                when(principal.getAppUserId()).thenReturn(userId);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(principal,
                                null);

                when(authenticationManager.authenticate(any())).thenReturn(authToken);
                when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(jwtService.generateToken(anyString(), anyLong(), any(), anyString()))
                                .thenReturn("jwt-token-123");

                // Act
                LoginResponse result = authService.login(loginRequest, response);

                // Assert
                assertNotNull(result);
                assertTrue(result.success());
                assertEquals("jwt-token-123", result.accessToken());

                verify(response).setHeader(eq("Set-Cookie"), contains("JWTAccess_token"));
        }

        @Test
        @DisplayName("Login fails - invalid credentials")
        void testLogin_InvalidCredentials() {
                // Arrange
                LoginRequest loginRequest = new LoginRequest(null, "john@test.com", "wrongpassword");

                when(authenticationManager.authenticate(any()))
                                .thenThrow(new BadCredentialsException("Invalid credentials"));

                // Act & Assert
                assertThrows(BadCredentialsException.class,
                                () -> authService.login(loginRequest, response));
        }

        @Test
        @DisplayName("Change password successfully")
        void testChangePassword_Success() {
                // Arrange
                AuthController.ChangePasswordCmd cmd = new AuthController.ChangePasswordCmd(
                                "oldPassword", "newPassword123");

                when(getUser.getCurrentAuthenticatedUser()).thenReturn(testUser);
                when(passwordEncoder.matches("oldPassword", testUser.getPassword())).thenReturn(true);
                when(passwordEncoder.matches("newPassword123", testUser.getPassword())).thenReturn(false);
                when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");

                // Act
                authService.changePassword(cmd);

                // Assert
                verify(appUserRepository, times(1)).save(testUser);
                verify(passwordEncoder).encode("newPassword123");
        }

        @Test
        @DisplayName("Change password fails - wrong old password")
        void testChangePassword_WrongOldPassword() {
                // Arrange
                AuthController.ChangePasswordCmd cmd = new AuthController.ChangePasswordCmd(
                                "wrongOldPassword", "newPassword123");

                when(getUser.getCurrentAuthenticatedUser()).thenReturn(testUser);
                when(passwordEncoder.matches("wrongOldPassword", testUser.getPassword())).thenReturn(false);

                // Act & Assert
                BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                                () -> authService.changePassword(cmd));

                assertTrue(exception.getMessage().contains("wrong password"));
                verify(appUserRepository, never()).save(any());
        }

        @Test
        @DisplayName("Change password fails - same as old password")
        void testChangePassword_SameAsOld() {
                // Arrange
                AuthController.ChangePasswordCmd cmd = new AuthController.ChangePasswordCmd(
                                "oldPassword", "oldPassword");

                when(getUser.getCurrentAuthenticatedUser()).thenReturn(testUser);
                when(passwordEncoder.matches("oldPassword", testUser.getPassword())).thenReturn(true);
                when(passwordEncoder.matches("oldPassword", testUser.getPassword())).thenReturn(true);

                // Act & Assert
                BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                                () -> authService.changePassword(cmd));

                assertTrue(exception.getMessage().contains("same password"));
                verify(appUserRepository, never()).save(any());
        }
}
