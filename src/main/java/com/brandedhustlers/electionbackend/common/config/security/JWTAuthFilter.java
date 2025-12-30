package com.brandedhustlers.electionbackend.common.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@Slf4j
public class JWTAuthFilter extends OncePerRequestFilter {

    private final JWTService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    public JWTAuthFilter(JWTService jwtService,
                         CustomUserDetailsService customUserDetailsService) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String token = getTokenFromAuthorizationHeader(request);
        if (token == null) {
            token = getTokenFromCookie(request.getCookies());
        }

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Authentication existing = SecurityContextHolder.getContext().getAuthentication();
            if (existing != null) {
                chain.doFilter(request, response);
                return;
            }

            final String role = jwtService.extractRole(token);
            if (role == null || role.isBlank()) {
                chain.doFilter(request, response);
                return;
            }


            if ("VOTER".equalsIgnoreCase(role)) {
                if (!jwtService.validateToken(token)) {
                    chain.doFilter(request, response);
                    return;
                }

                UUID voterId = UUID.fromString(jwtService.extractVoterId(token));
                log.info("voter ID {}", voterId);

                UUID orgId = UUID.fromString(jwtService.extractVoterOrgId(token));
                log.info("voter org ID {}", orgId);

                Long stepUp = jwtService.extractStepUp(token);
                if (stepUp == null) {
                    log.warn("Step-up claim missing or invalid in token");
                    chain.doFilter(request, response);
                    return;
                }

                log.info("setting auth context now.....");

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_VOTER"));
                var voterAuth = new VoterAuthToken(authorities, voterId, "VOTER", orgId, stepUp);
                voterAuth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(voterAuth);

                log.debug("voter auth token: {}", voterAuth);

                chain.doFilter(request, response);
                return;
            }

            final String userEmail = jwtService.extractUserEmail(token);
            if (userEmail == null || userEmail.isBlank()) {
                chain.doFilter(request, response);
                return;
            }

            UserDetails userDetails = customUserDetailsService.loadUserByUsername(userEmail);
            if (!jwtService.validateToken(token, userDetails)) {
                chain.doFilter(request, response);
                return;
            }

            var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            chain.doFilter(request, response);

        } catch (Exception e) {
            e.printStackTrace();
            log.error("error occurred in authentication ", e);
            chain.doFilter(request, response);
        }
    }

    private String getTokenFromAuthorizationHeader(HttpServletRequest request) {
        final String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String getTokenFromCookie(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if ("JWTAccess_token".equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static UUID safeUuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
