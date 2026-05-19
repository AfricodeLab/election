package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JWTService {

    @Value("${JWT_SECRET}")
    private String secretKey;

    private final AppUserRepository appUserRepository;
    private final VoterRepository voterRepository;

    public JWTService(AppUserRepository appUserRepository, VoterRepository voterRepository) {
        this.appUserRepository = appUserRepository;
        this.voterRepository = voterRepository;
    }

    public String generateRefreshToken(String username, UUID userID, String appUserEmail) {
        long refreshTokenExp = 604800000L; // 7 days
        return generateToken(username, refreshTokenExp, userID, appUserEmail);
    }

    /** No-DB-call overload: pass the already-loaded AppUser. */
    public String generateRefreshToken(AppUser user) {
        UUID activeOrgId = user.getActiveOrganization() == null
                ? null : user.getActiveOrganization().getOrganizationId();
        String role = user.getUserRole() == null ? null : user.getUserRole().name();
        return generateToken(user.getUsername(), 604800000L,
                user.getAppUserId(), user.getAppUserEmail(), activeOrgId, role);
    }

    /**
     * Generates a short-lived (5 min) MFA-pending token.
     * This token intentionally carries NO role claim so the JWTAuthFilter
     * cannot use it to grant access to protected endpoints.
     * It can only be exchanged for a full access token at /auth/mfa/verify.
     */
    public String generateMfaPendingToken(UUID userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("userEmail", email);
        claims.put("mfaPending", true);
        claims.put("iss", "brandedhustlers_backend");
        claims.put("aud", "election_client");

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 300_000L)) // 5 min
                .and()
                .signWith(getKey())
                .compact();
    }

    public UUID extractMfaPendingUserId(String token) {
        Claims claims = extractAllClaim(token);
        String id = claims.get("userId", String.class);
        return id == null ? null : UUID.fromString(id);
    }

    public boolean isMfaPendingToken(String token) {
        try {
            Claims claims = extractAllClaim(token);
            Object flag = claims.get("mfaPending");
            return Boolean.TRUE.equals(flag);
        } catch (Exception e) {
            return false;
        }
    }

    public String generateAccessToken(String username, UUID userId, String appUserEmail) {
        long accessTokenExpirationTime = 900000L; // 15 minutes
        return generateToken(username, accessTokenExpirationTime, userId, appUserEmail);
    }

    /** No-DB-call overload: pass the already-loaded AppUser. */
    public String generateAccessToken(AppUser user) {
        UUID activeOrgId = user.getActiveOrganization() == null
                ? null : user.getActiveOrganization().getOrganizationId();
        String role = user.getUserRole() == null ? null : user.getUserRole().name();
        return generateToken(user.getUsername(), 900000L,
                user.getAppUserId(), user.getAppUserEmail(), activeOrgId, role);
    }

    public String generateVoterToken(String voterEmail) {
        Voter voter = voterRepository.findByEmailIgnoreCase(voterEmail)
                .orElseThrow(() -> new UserNotFound("Voter with email: " + voterEmail + " not found"));

        Map<String, Object> claims = new HashMap<>();
        claims.put("voterOrgId",
                voter.getOrganization() == null ? null : voter.getOrganization().getOrganizationId().toString());
        claims.put("voterId", voter.getVoterId().toString());
        claims.put("role", UserRole.VOTER.getName());
        claims.put("stepUpVerifiedAt", Instant.now().toEpochMilli());
        claims.put("iss", "brandedhustlers_backend");
        claims.put("aud", "election_client");

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(voter.getVoterStatus().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 150 * (60 * 1000)))
                .and()
                .signWith(getKey())
                .compact();
    }

    public Long extractStepUp(String token) {
        Object value = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("stepUpVerifiedAt");

        if (value instanceof Number num) {
            return num.longValue();
        }

        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    public String extractVoterId(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("voterId", String.class);
    }

    public String extractVoterOrgId(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("voterOrgId", String.class);
    }

    /**
     * Generates a signed JWT. The caller must supply {@code activeOrgId} directly —
     * this avoids the DB round-trip that the old implementation had
     * ({@code appUserRepository.findById(userId)}) on every token generation.
     * The AppUser is already in memory at login time; pass its activeOrganization id.
     */
    public String generateToken(String username, long expirationTime, UUID userId,
                                String appUserEmail, UUID activeOrgId, String userRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userID", userId);
        claims.put("username", username);
        claims.put("userEmail", appUserEmail);
        claims.put("activeOrgId", activeOrgId);
        claims.put("role", userRole);
        claims.put("iss", "brandedhustlers_backend");
        claims.put("aud", "election_client");

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .and()
                .signWith(getKey())
                .compact();
    }

    /**
     * @deprecated Use {@link #generateToken(String, long, UUID, String, UUID, String)} instead.
     * This overload performs a DB lookup on every call; kept for any callers that
     * haven't migrated yet but will be removed once all call sites are updated.
     */
    @Deprecated
    public String generateToken(String username, long expirationTime, UUID userId, String appUserEmail) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User with ID: " + userId + " not found."));
        UUID activeOrgId = user.getActiveOrganization() == null
                ? null : user.getActiveOrganization().getOrganizationId();
        return generateToken(username, expirationTime, userId, appUserEmail,
                activeOrgId, user.getUserRole() == null ? null : user.getUserRole().name());
    }

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaim(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaim(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public boolean validateToken(String token) {
        return !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractRole(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public String extractUserEmail(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userEmail", String.class);
    }

    public String extractUserType(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("appUserType", String.class);
    }

    public UUID extractAppUserId(String token) {
        Claims claims = extractAllClaim(token);
        if (claims.containsKey("userID")) {
            return UUID.fromString(claims.get("userID", String.class));
        }
        return null;
    }

    public UUID extractActiveOrgId(String token) {
        Claims claims = extractAllClaim(token);
        if (claims.containsKey("activeOrgId")) {
            return UUID.fromString(claims.get("activeOrgId", String.class));
        }
        return null;
    }

    public String extractAppUserRole(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public UUID extractAppUserId(HttpServletRequest request) {
        String token;
        token = getTokenFromCookie(request.getCookies());
        if (token == null) {
            token = getTokenFromAuthorizationHeader(request);
        }
        return extractAppUserId(token);
    }

    private String getTokenFromAuthorizationHeader(HttpServletRequest request) {
        final String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }

    private String getTokenFromCookie(Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JWTAccess_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
