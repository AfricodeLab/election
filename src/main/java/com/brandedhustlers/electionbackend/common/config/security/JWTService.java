package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
    Dotenv dotenv = Dotenv.configure().load();
    private final String secretKey = dotenv.get("JWT_SECRET");
    private final AppUserRepository appUserRepository;
    private final VoterRepository voterRepository;

    public JWTService(AppUserRepository appUserRepository, VoterRepository voterRepository) {
        this.appUserRepository = appUserRepository;
        this.voterRepository = voterRepository;
    }


    public String generateRefreshToken(String username, UUID userID, String appUserEmail) {
        long refreshTokenExp = 15552000000L;
        return generateToken(username, refreshTokenExp, userID, appUserEmail);
    }

    public String generateAccessToken(String username, UUID userId, String appUserEmail) {
        long accessTokenExpirationTime = 15552000000L;
        return generateToken(username, accessTokenExpirationTime, userId, appUserEmail );
    }

    public String generateVoterToken(String voterEmail){
        Voter voter = voterRepository.findByEmailIgnoreCase(voterEmail)
                .orElseThrow(() -> new UserNotFound("Voter with email: " + voterEmail + " not found"));

        Map<String, Object> claims = new HashMap<>();
        claims.put("voterOrgId",voter.getOrganization() == null ? null : voter.getOrganization().getOrganizationId().toString());
        claims.put("voterId", voter.getVoterId().toString());
        claims.put("role", UserRole.VOTER.getName());
        claims.put("stepUpVerifiedAt", Instant.now().toEpochMilli());

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

    public String extractVoterId(String token){
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("voterId", String.class);
    }

    public String extractVoterOrgId(String token){
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("voterOrgId", String.class);
    }



    public String generateToken(String username, long expirationTime, UUID userId, String appUserEmail) {

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User with ID: " + userId + " not found."));

        Map<String, Object> claims = new HashMap<>();
        claims.put("userID",userId);
        claims.put("username", username);
        claims.put("userEmail", appUserEmail);
        claims.put("userOrgId", user.getAppUserOrganization() == null ? null : user.getAppUserOrganization().getOrganizationId());
        claims.put("role", user.getUserRole());

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

    public boolean validateToken(String token){
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


    public String extractUserEmail(String token){
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userEmail", String.class);
    }

    public String extractUserType(String token){
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

    public UUID extractOrgId(String token) {
        Claims claims = extractAllClaim(token);
        if (claims.containsKey("userOrgId")) {
            return UUID.fromString(claims.get("userOrgId", String.class));
        }
        return null;
    }

    public String extractAppUserRole(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("appUserId", String.class);
    }

    public UUID extractAppUserId(HttpServletRequest request){
        String token;
        token = getTokenFromCookie(request.getCookies());
        if(token == null){
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

