package com.brandedhustlers.electionbackend.common.config.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JWTAuthFilter jwtFilter;
    private final AllRequestsLoggingFilter loggingFilter;

    public SecurityConfig(JWTAuthFilter jwtFilter,
                          AllRequestsLoggingFilter loggingFilter) {
        this.jwtFilter = jwtFilter;
        this.loggingFilter = loggingFilter;
    }

    // Disable automatic registration of JWTAuthFilter
    @Bean
    public FilterRegistrationBean<JWTAuthFilter> jwtFilterRegistration(JWTAuthFilter filter) {
        FilterRegistrationBean<JWTAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // Disable automatic registration of AllRequestsLoggingFilter
    @Bean
    public FilterRegistrationBean<AllRequestsLoggingFilter> loggingFilterRegistration(AllRequestsLoggingFilter filter) {
        FilterRegistrationBean<AllRequestsLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // Shorthand arrays for role groups
        String[] ADMINS       = {"ADMIN", "SUPER_ADMIN", "ORG_ADMIN"};
        String[] ADMINS_AUDIT = {"ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR"};
        String[] READERS      = {"ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR", "CONTRIBUTOR"};
        String[] ALL_INTERNAL = {"ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR", "CONTRIBUTOR", "OBSERVER"};
        // DOERS: roles that can perform write operations via delegated permissions.
        // Gate 1 (HTTP layer) lets these roles through; Gate 2 (service layer)
        // enforces the specific PermissionType. OBSERVER/AUDITOR are read-only so
        // they are excluded from write endpoints.
        String[] DOERS = {"ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "CONTRIBUTOR"};

        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .cacheControl(Customizer.withDefaults())
                )
                .authorizeHttpRequests(auth -> auth

                        // ── Public (no token required) ──────────────────────────
                        .requestMatchers(
                                "/api/v1/auth/register-user",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/**")
                        .permitAll()
                        .requestMatchers("/ws/**", "/ws-raw/**").permitAll()

                        // ── Voter-only ──────────────────────────────────────────
                        .requestMatchers("/api/v1/verification/send").permitAll()
                        .requestMatchers("/api/v1/verification/confirm").permitAll()
                        .requestMatchers("/api/v1/votes/cast","/api/v1/election/live").hasRole("VOTER")
                        .requestMatchers("/api/v1/election/live").hasRole("VOTER")

                        // ── SUPER_ADMIN-only ────────────────────────────────────
                        .requestMatchers("/api/v1/organization/all").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/organization/deactivate/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/elections/*/release").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/chain/validate").hasAnyRole("SUPER_ADMIN", "AUDITOR")
                        .requestMatchers("/api/debug/**").hasRole("SUPER_ADMIN")

                        // ── SUPER_ADMIN + AUDITOR (audit / integrity) ───────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/validate")
                        .hasAnyRole("SUPER_ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/validate-stream")
                        .hasAnyRole("SUPER_ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/audit")
                        .hasAnyRole("SUPER_ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/integrity-summary")
                        .hasAnyRole("SUPER_ADMIN", "AUDITOR")
                        .requestMatchers("/api/v1/audit/**").hasAnyRole(ADMINS_AUDIT)

                        // ── Auth: user management (write) ───────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/create-user").hasAnyRole(ADMINS)
                        // Role update: ADMIN/SUPER_ADMIN can update any user; ORG_ADMIN can update
                        // users within their own org (service layer enforces the org boundary)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/users/*/role").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/deactivate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/activate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Auth: user list (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/get-users").hasAnyRole(ADMINS_AUDIT)

                        // ── Organization (write) ────────────────────────────────
                        // Only BH platform team creates / configures organizations
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/create").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/update/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // ORG_ADMIN may add users to their own org
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/add-user").hasAnyRole(ADMINS)

                        // ── Election (write) ────────────────────────────────────
                        // DOERS: service layer enforces specific PermissionType
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/create").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update-status/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/candidate-add/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update-candidate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/live/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Election (read) — includes OBSERVER
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/get-all").hasAnyRole(ALL_INTERNAL)
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/live").hasAnyRole(ALL_INTERNAL)

                        // ── Election types (write) ──────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/election-types").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/election-types/presets").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/election-types/**").hasAnyRole(DOERS)
                        // Election types (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/election-types").hasAnyRole(READERS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/election-types/**").hasAnyRole(READERS)

                        // ── Voters (write) ──────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/voters").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/voters/assign").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/voters/import").hasAnyRole(DOERS)
                        // Voters (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/voters").hasAnyRole(READERS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/voters/**").hasAnyRole(READERS)

                        // ── Verification: attribute coverage (read) ─────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/verification/voters/**").hasAnyRole(ADMINS_AUDIT)

                        // ── Roles (write) — only admins can manage roles ────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles/users/**").hasAnyRole(ADMINS)
                        // Roles (read) — all internal roles can view
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/**").hasAnyRole(ALL_INTERNAL)
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles").hasAnyRole(ALL_INTERNAL)

                        // ── Stations (write) ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stations/**").hasAnyRole(DOERS)
                        // Stations (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/stations/**").hasAnyRole(READERS)

                        // ── Devices (write) ─────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/devices/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/devices/**").hasAnyRole(DOERS)
                        // Devices (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/devices/**").hasAnyRole(READERS)

                        // ── Launches (write) ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/launches/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/launches/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/launches/**").hasAnyRole(DOERS)
                        // Launches (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/launches/**").hasAnyRole(READERS)

                        // ── Notifications ───────────────────────────────────────
                        .requestMatchers("/api/v1/notif/**").hasAnyRole(ADMINS)

                        // ── Results (read) — all internal roles + OBSERVER ──────
                        .requestMatchers(HttpMethod.GET, "/api/v1/results/**").hasAnyRole(ALL_INTERNAL)

                        // ── Test WS push endpoints ──────────────────────────────
                        .requestMatchers("/test/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── Common authenticated (any logged-in user) ───────────
                        .requestMatchers("/api/v1/auth/password/change").authenticated()
                        .requestMatchers("/api/v1/organization/switch/**").authenticated()
                        .requestMatchers("/api/v1/organization/my-orgs").authenticated()

                        // ── Default: deny unauthenticated ───────────────────────
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(
                        session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(loggingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

}
