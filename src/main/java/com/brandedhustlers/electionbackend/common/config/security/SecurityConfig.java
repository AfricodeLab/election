package com.brandedhustlers.electionbackend.common.config.security;

import jakarta.servlet.DispatcherType;
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
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity(debug = true)
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

        // Shorthand arrays for role groups (used with hasAnyRole — Spring auto-prepends ROLE_)
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
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .cacheControl(Customizer.withDefaults())
                )
                .authorizeHttpRequests(auth -> auth

                        // ── Async dispatches (SseEmitter, DeferredResult, etc.) ─
                        // Tomcat re-runs the filter chain on the async thread after
                        // the initial request has already been authorized. The JWT
                        // filter is OncePerRequestFilter so it skips re-auth, leaving
                        // the SecurityContext anonymous — which would 403 every SSE
                        // stream. Permitting ASYNC dispatches here is safe: the
                        // original FORWARD/REQUEST dispatch was already fully checked.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

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

                        // ── Voter portal (public — voters have no admin JWT) ────
                        .requestMatchers("/api/v1/verification/send").permitAll()
                        .requestMatchers("/api/v1/verification/confirm").permitAll()
                        // In-person verification — official must be authenticated
                        .requestMatchers("/api/v1/verification/in-person/**").hasAnyRole(ADMINS)
                        // After OTP, voter fetches their eligible live elections.
                        // MUST be listed before the broad GET /election/* permitAll below,
                        // and before the ALL_INTERNAL rule later, which excludes VOTER.
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/live").hasAnyRole("VOTER", "ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR", "CONTRIBUTOR", "OBSERVER")
                        // Admin list endpoint — keep restricted before the permitAll wildcard.
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/get-all").hasAnyRole(ALL_INTERNAL)
                        // GET /election/{id}: public so the voter portal can display election
                        // details before the voter authenticates via OTP.
                        // Specific rules above must precede this wildcard (first-match wins).
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/*").permitAll()
                        .requestMatchers("/api/v1/votes/cast").hasRole("VOTER")

                        // ── SUPER_ADMIN-only ────────────────────────────────────
                        .requestMatchers("/api/v1/organization/all").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/organization/deactivate/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/chain/validate").hasAnyRole("SUPER_ADMIN", "AUDITOR")
                        .requestMatchers("/api/debug/**").hasRole("SUPER_ADMIN")

                        // ── Release results — ADMINS or PERM_RELEASE_RESULTS ────
                        // Note: the old rule ".requestMatchers("/api/v1/elections/*/release")
                        // .hasRole("SUPER_ADMIN")" was too narrow (blocked ORG_ADMIN).
                        // The subsequent "/**" rule was dead — it required a sub-path
                        // that the actual endpoint never has.  Both are replaced here.
                        .requestMatchers(HttpMethod.POST, "/api/v1/elections/*/release")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "RELEASE_RESULTS"))

                        // ── SUPER_ADMIN + AUDITOR + ADMINS (audit / integrity) ──
                        // Validate is part of the release flow — ORG_ADMIN must be
                        // able to run it before releasing their own election.
                        // Also accepts PERM_VIEW_AUDIT_LOG for delegated auditor roles.
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/validate")
                        .hasAnyAuthority(roleOrPerm(ADMINS_AUDIT, "VIEW_AUDIT_LOG"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/validate-stream")
                        .hasAnyAuthority(roleOrPerm(ADMINS_AUDIT, "VIEW_AUDIT_LOG"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/audit")
                        .hasAnyAuthority(roleOrPerm(new String[]{"SUPER_ADMIN", "AUDITOR"}, "VIEW_AUDIT_LOG"))
                        // integrity-summary is shown on the Results tab to any admin
                        // managing the election — ORG_ADMIN must be able to see it.
                        .requestMatchers(HttpMethod.GET, "/api/v1/elections/*/integrity-summary")
                        .hasAnyAuthority(roleOrPerm(ADMINS_AUDIT, "VIEW_AUDIT_LOG"))
                        .requestMatchers("/api/v1/audit/**")
                        .hasAnyAuthority(roleOrPerm(ADMINS_AUDIT, "VIEW_AUDIT_LOG"))

                        // ── Auth: user management (write) ───────────────────────
                        // PERM_CREATE_USER lets an ORG_ADMIN delegate user-creation to a trusted custom role.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/create-user")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "CREATE_USER"))
                        // Role update: ADMIN/SUPER_ADMIN can update any user; ORG_ADMIN can update
                        // users within their own org (service layer enforces the org boundary).
                        // PERM_UPDATE_USER allows the same delegation pattern.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/users/*/role")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "UPDATE_USER"))
                        // Deactivate/activate intentionally restricted to platform admins only —
                        // the service layer has no org-boundary guard here yet.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/deactivate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/activate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Auth: user list (read) — PERM_VIEW_USERS unlocks for delegated roles.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/get-users")
                        .hasAnyAuthority(roleOrPerm(ADMINS_AUDIT, "VIEW_USERS"))

                        // ── Organization (write) ────────────────────────────────
                        // Only BH platform team creates / configures organizations
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/create").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/update/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // ORG_ADMIN may add users to their own org; PERM_UPDATE_USER delegates this.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/add-user")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "UPDATE_USER"))

                        // ── Election (write) ────────────────────────────────────
                        // DOERS: service layer enforces specific PermissionType
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/create").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update-status/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/candidate-add/**").hasAnyRole(DOERS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update-candidate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // PERM_PUBLISH_ELECTION: an ORG_ADMIN can delegate "bring to live" to
                        // a trusted custom role (e.g. "Senior Election Manager").
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/live/**")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "PUBLISH_ELECTION"))
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/verification/voters/**")
                        .hasAnyAuthority(roleOrPerm(ADMINS_AUDIT, "VIEW_VOTERS"))

                        // ── Roles (write) — PERM_ variants allow ORG_ADMINs to delegate
                        //    role management to a trusted custom role (e.g. "HR Manager").
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "CREATE_ROLE"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "UPDATE_ROLE"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "DELETE_ROLE"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles/users/**")
                        .hasAnyAuthority(roleOrPerm(ADMINS, "ASSIGN_ROLE"))
                        // Roles (read) — all internal roles can view; PERM_VIEW_ROLES unlocks for delegated roles.
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/**")
                        .hasAnyAuthority(roleOrPerm(ALL_INTERNAL, "VIEW_ROLES"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles")
                        .hasAnyAuthority(roleOrPerm(ALL_INTERNAL, "VIEW_ROLES"))

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
                // Disable HTTP Basic — this is a JWT-only API. Basic auth adds a
                // WWW-Authenticate: Basic header to every 401, which causes browsers
                // to show their native "sign in" popup dialog.
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"statusCode\":401,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required.\"}}"
                            );
                        })
                )
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

    /**
     * Builds a combined authority array for use with {@code hasAnyAuthority()}.
     *
     * <p>Role names are prefixed with {@code "ROLE_"} (matching what
     * {@code getAuthorities()} emits for role-based authorities).
     * Permission names are prefixed with {@code "PERM_"} (matching what
     * {@code getAuthorities()} emits for permission-based authorities).</p>
     *
     * <p>Use this instead of {@code hasAnyRole()} on endpoints that should be
     * accessible to users who hold the permission via a custom org role, even
     * when their base system role alone would not satisfy the check.</p>
     *
     * <pre>
     *   // Allows ADMIN/SUPER_ADMIN/ORG_ADMIN via system role,
     *   // OR any user whose custom role includes PUBLISH_ELECTION.
     *   .hasAnyAuthority(roleOrPerm(ADMINS, "PUBLISH_ELECTION"))
     * </pre>
     */
    private static String[] roleOrPerm(String[] roles, String... permissionNames) {
        String[] result = new String[roles.length + permissionNames.length];
        for (int i = 0; i < roles.length; i++) {
            result[i] = "ROLE_" + roles[i];
        }
        for (int i = 0; i < permissionNames.length; i++) {
            result[roles.length + i] = "PERM_" + permissionNames[i];
        }
        return result;
    }

}
