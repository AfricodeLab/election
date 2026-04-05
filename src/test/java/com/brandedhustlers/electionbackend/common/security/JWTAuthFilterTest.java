package com.brandedhustlers.electionbackend.common.security;

import com.brandedhustlers.electionbackend.common.config.security.CustomUserDetailsService;
import com.brandedhustlers.electionbackend.common.config.security.JWTAuthFilter;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JWTAuthFilter")
class JWTAuthFilterTest {

    @Mock
    private JWTService jwtService;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    private UserDetails userDetails;

    private JWTAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JWTAuthFilter(jwtService, customUserDetailsService);
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("OPTIONS pass-through")
    class OptionsMethod {

        @Test
        @DisplayName("Passes OPTIONS request to chain without any token processing")
        void optionsPassesThrough() throws Exception {
            when(request.getMethod()).thenReturn("OPTIONS");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtService, customUserDetailsService);
        }
    }

    @Nested
    @DisplayName("No token")
    class NoToken {

        @Test
        @DisplayName("Continues chain when no Authorization header and no cookie")
        void continuesWhenNoToken() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getCookies()).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Extracts token from JWTAccess_token cookie when no Authorization header")
        void extractsTokenFromCookie() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn(null);
            Cookie cookie = new Cookie("JWTAccess_token", "cookie-token");
            when(request.getCookies()).thenReturn(new Cookie[]{cookie});
            when(jwtService.extractRole("cookie-token")).thenReturn("APP_USER");
            when(jwtService.extractUserEmail("cookie-token")).thenReturn("user@test.com");
            when(customUserDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
            when(jwtService.validateToken("cookie-token", userDetails)).thenReturn(true);
            when(userDetails.getUsername()).thenReturn("user@test.com");

            filter.doFilter(request, response, chain);

            verify(jwtService).extractRole("cookie-token");
        }
    }

    @Nested
    @DisplayName("VOTER token path")
    class VoterPath {

        @Test
        @DisplayName("Sets VoterAuthToken in SecurityContext for valid VOTER token")
        void setsVoterAuth() throws Exception {
            String token = "voter-jwt";
            UUID voterId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();

            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getCookies()).thenReturn(null);
            when(jwtService.extractRole(token)).thenReturn("VOTER");
            when(jwtService.validateToken(token)).thenReturn(true);
            when(jwtService.extractVoterId(token)).thenReturn(voterId.toString());
            when(jwtService.extractVoterOrgId(token)).thenReturn(orgId.toString());
            when(jwtService.extractStepUp(token)).thenReturn(1L);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assert auth != null;
            assert auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_VOTER"));
            verifyNoInteractions(customUserDetailsService);
        }

        @Test
        @DisplayName("Continues chain (no auth) when VOTER token is invalid")
        void continuesWhenVoterTokenInvalid() throws Exception {
            String token = "bad-voter-jwt";

            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getCookies()).thenReturn(null);
            when(jwtService.extractRole(token)).thenReturn("VOTER");
            when(jwtService.validateToken(token)).thenReturn(false);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assert SecurityContextHolder.getContext().getAuthentication() == null;
        }

        @Test
        @DisplayName("Continues chain when step-up claim is missing from VOTER token")
        void continuesWhenStepUpMissing() throws Exception {
            String token = "voter-jwt-no-stepup";
            UUID voterId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();

            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getCookies()).thenReturn(null);
            when(jwtService.extractRole(token)).thenReturn("VOTER");
            when(jwtService.validateToken(token)).thenReturn(true);
            when(jwtService.extractVoterId(token)).thenReturn(voterId.toString());
            when(jwtService.extractVoterOrgId(token)).thenReturn(orgId.toString());
            when(jwtService.extractStepUp(token)).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assert SecurityContextHolder.getContext().getAuthentication() == null;
        }
    }

    @Nested
    @DisplayName("APP_USER token path")
    class AppUserPath {

        @Test
        @DisplayName("Sets UsernamePasswordAuthenticationToken for valid APP_USER token")
        void setsAppUserAuth() throws Exception {
            String token = "app-user-jwt";

            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getCookies()).thenReturn(null);
            when(jwtService.extractRole(token)).thenReturn("ADMIN");
            when(jwtService.extractUserEmail(token)).thenReturn("admin@test.com");
            when(customUserDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
            when(jwtService.validateToken(token, userDetails)).thenReturn(true);
            when(userDetails.getUsername()).thenReturn("admin@test.com");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assert auth != null;
            assert auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("Continues chain (no auth) when APP_USER token fails validation")
        void continuesWhenTokenInvalid() throws Exception {
            String token = "bad-app-jwt";

            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getCookies()).thenReturn(null);
            when(jwtService.extractRole(token)).thenReturn("ADMIN");
            when(jwtService.extractUserEmail(token)).thenReturn("admin@test.com");
            when(customUserDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
            when(jwtService.validateToken(token, userDetails)).thenReturn(false);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assert SecurityContextHolder.getContext().getAuthentication() == null;
        }
    }

    @Nested
    @DisplayName("Exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("Returns 401 JSON when token processing throws an exception")
        void returns401OnException() throws Exception {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);

            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
            when(request.getCookies()).thenReturn(null);
            when(jwtService.extractRole(anyString())).thenThrow(new RuntimeException("Invalid JWT"));
            when(response.getWriter()).thenReturn(pw);

            filter.doFilter(request, response, chain);

            verify(response).setStatus(401);
            verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
            verify(chain, never()).doFilter(any(), any());
        }
    }
}
