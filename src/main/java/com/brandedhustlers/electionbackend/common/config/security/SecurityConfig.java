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
    private final CustomUserDetailsService userDetailsService;
    private final AllRequestsLoggingFilter loggingFilter;

    public SecurityConfig(JWTAuthFilter jwtFilter,
            CustomUserDetailsService userDetailsService,
            AllRequestsLoggingFilter loggingFilter) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
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
        String[] ADMINS = { "ADMIN", "SUPER_ADMIN", "ORG_ADMIN" };
        String[] ADMINS_AUDIT = { "ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR" };
        String[] READERS = { "ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR", "CONTRIBUTOR" };
        String[] ALL_INTERNAL = { "ADMIN", "SUPER_ADMIN", "ORG_ADMIN", "AUDITOR", "CONTRIBUTOR", "OBSERVER" };

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
                                "/api/v1/auth/mfa/verify")
                        .permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/**")
                        .permitAll()
                        .requestMatchers("/ws/**", "/ws-raw/**").permitAll()

                        // ── Voter-only ──────────────────────────────────────────
                        .requestMatchers("/api/v1/verification/send").hasRole("VOTER")
                        .requestMatchers("/api/v1/verification/confirm").hasRole("VOTER")
                        .requestMatchers("/api/v1/votes/cast").hasRole("VOTER")

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
                        .requestMatchers("/api/v1/audit/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "AUDITOR")

                        // ── Auth: user management (write) ───────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/create-user").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/deactivate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/activate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Auth: user list (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/get-users").hasAnyRole(ADMINS_AUDIT)

                        // ── Organization (write) ────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/organization/create").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/update/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization/add-user").hasAnyRole(ADMINS)

                        // ── Election (write) ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/create").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update-status/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/candidate-add/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/election/update-candidate/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/election/live/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Election (read) — includes OBSERVER
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/get-all").hasAnyRole(ALL_INTERNAL)
                        .requestMatchers(HttpMethod.GET, "/api/v1/election/live").hasAnyRole(ALL_INTERNAL)

                        // ── Election types (write) ──────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/election-types").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/election-types/presets").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/election-types/**").hasAnyRole(ADMINS)
                        // Election types (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/election-types").hasAnyRole(READERS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/election-types/**").hasAnyRole(READERS)

                        // ── Voters (write) ──────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/voters").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/voters/assign").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/voters/import").hasAnyRole(ADMINS)
                        // Voters (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/voters").hasAnyRole(READERS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/voters/**").hasAnyRole(READERS)

                        // ── Verification: attribute coverage (read) ─────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/verification/voters/**").hasAnyRole(ADMINS_AUDIT)

                        // ── Stations (write) ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stations/**").hasAnyRole(ADMINS)
                        // Stations (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/stations/**").hasAnyRole(READERS)

                        // ── Devices (write) ─────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/devices/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/devices/**").hasAnyRole(ADMINS)
                        // Devices (read)
                        .requestMatchers(HttpMethod.GET, "/api/v1/devices/**").hasAnyRole(READERS)

                        // ── Launches (write) ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/launches/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/launches/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/launches/**").hasAnyRole(ADMINS)
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
