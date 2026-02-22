package com.brandedhustlers.electionbackend.auth;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JWTService - token generation and validation.
 */
@ExtendWith(MockitoExtension.class)
class JWTServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private VoterRepository voterRepository;

    private JWTService jwtService;

    // Test secret key - base64 encoded 256-bit key
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tZ2VuZXJhdGlvbi0yNTZiaXQ=";

    private UUID userId;
    private UUID orgId;
    private AppUser testUser;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        // Create service and inject test secret
        jwtService = new JWTService(appUserRepository, voterRepository);
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);

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
                .userRole(UserRole.ORG_ADMIN)
                .appUserOrganization(testOrg)
                .build();
    }

    @Test
    @DisplayName("Generate token contains expected claims")
    void testGenerateToken_ContainsExpectedClaims() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Verify claims
        Claims claims = extractClaims(token);
        assertEquals("johndoe", claims.getSubject());
        assertEquals(userId.toString(), claims.get("userID"));
        assertEquals("john@test.com", claims.get("userEmail"));
        assertEquals(orgId.toString(), claims.get("userOrgId").toString());
        assertEquals("ORG_ADMIN", claims.get("role").toString());
        assertEquals("brandedhuslters_backend", claims.get("iss"));
        assertEquals("election_client", claims.getAudience().iterator().next());
    }

    @Test
    @DisplayName("Validate token returns true for valid non-expired token")
    void testValidateToken_ValidToken() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        UserDetails userDetails = new User("johndoe", "password", Collections.emptyList());

        // Act
        boolean isValid = jwtService.validateToken(token, userDetails);

        // Assert
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Validate token returns false when username doesn't match")
    void testValidateToken_UsernameMismatch() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Different username
        UserDetails userDetails = new User("differentuser", "password", Collections.emptyList());

        // Act
        boolean isValid = jwtService.validateToken(token, userDetails);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Extract username returns correct value")
    void testExtractUsername_ReturnsCorrectValue() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Act
        String username = jwtService.extractUsername(token);

        // Assert
        assertEquals("johndoe", username);
    }

    @Test
    @DisplayName("Extract user email returns correct value")
    void testExtractUserEmail_ReturnsCorrectValue() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Act
        String email = jwtService.extractUserEmail(token);

        // Assert
        assertEquals("john@test.com", email);
    }

    @Test
    @DisplayName("Extract role returns correct value")
    void testExtractRole_ReturnsCorrectValue() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Act
        String role = jwtService.extractRole(token);

        // Assert
        assertEquals("ORG_ADMIN", role);
    }

    @Test
    @DisplayName("Extract app user ID returns correct value")
    void testExtractAppUserId_ReturnsCorrectValue() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Act
        UUID extractedId = jwtService.extractAppUserId(token);

        // Assert
        assertEquals(userId, extractedId);
    }

    @Test
    @DisplayName("Extract org ID returns correct value")
    void testExtractOrgId_ReturnsCorrectValue() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com");

        // Act
        UUID extractedOrgId = jwtService.extractOrgId(token);

        // Assert
        assertEquals(orgId, extractedOrgId);
    }

    @Test
    @DisplayName("Is token expired returns false for valid token")
    void testIsTokenExpired_ValidToken() {
        // Arrange
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        String token = jwtService.generateToken("johndoe", 3600000L, userId, "john@test.com"); // 1 hour

        // Act
        boolean isExpired = jwtService.isTokenExpired(token);

        // Assert
        assertFalse(isExpired);
    }

    @Test
    @DisplayName("Voter token contains step-up claim")
    void testVoterToken_ContainsStepUpClaim() {
        // Arrange
        UUID voterId = UUID.randomUUID();
        Voter testVoter = Voter.builder()
                .voterId(voterId)
                .email("voter@test.com")
                .name("Test Voter")
                .voterStatus(VoterStatus.ACTIVE)
                .organization(testOrg)
                .build();

        when(voterRepository.findByEmailIgnoreCase("voter@test.com")).thenReturn(Optional.of(testVoter));

        // Act
        String token = jwtService.generateVoterToken("voter@test.com");

        // Assert
        assertNotNull(token);

        Long stepUp = jwtService.extractStepUp(token);
        assertNotNull(stepUp);
        assertTrue(stepUp > 0);

        String extractedVoterId = jwtService.extractVoterId(token);
        assertEquals(voterId.toString(), extractedVoterId);

        String role = jwtService.extractRole(token);
        assertEquals("VOTER", role);
    }

    @Test
    @DisplayName("Extract voter org ID returns correct value")
    void testExtractVoterOrgId_ReturnsCorrectValue() {
        // Arrange
        UUID voterId = UUID.randomUUID();
        Voter testVoter = Voter.builder()
                .voterId(voterId)
                .email("voter@test.com")
                .name("Test Voter")
                .voterStatus(VoterStatus.ACTIVE)
                .organization(testOrg)
                .build();

        when(voterRepository.findByEmailIgnoreCase("voter@test.com")).thenReturn(Optional.of(testVoter));

        // Act
        String token = jwtService.generateVoterToken("voter@test.com");
        String extractedOrgId = jwtService.extractVoterOrgId(token);

        // Assert
        assertEquals(orgId.toString(), extractedOrgId);
    }

    /**
     * Helper method to extract claims using test secret key
     */
    private Claims extractClaims(String token) {
        byte[] keyBytes = Decoders.BASE64.decode(TEST_SECRET);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
