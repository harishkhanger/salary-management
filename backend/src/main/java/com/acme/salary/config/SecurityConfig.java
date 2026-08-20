package com.acme.salary.config;

import com.acme.salary.repository.HrUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal session-based auth for the single seeded HR user (no roles by
 * design). Everything under /api requires a session except login.
 * CSRF is disabled — documented trade-off: pure-JSON API consumed by the
 * same-origin SPA (dev proxy / same host in prod); production hardening
 * would re-enable it with CookieCsrfTokenRepository.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    // envelope shape emitted by hand: the entry point runs before MVC
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"success\":false,\"data\":null,\"error\":{\"code\":\"UNAUTHENTICATED\","
                                    + "\"message\":\"Login required\"}}");
                }))
                .sessionManagement(session -> session.maximumSessions(1))
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(HrUserRepository hrUserRepository) {
        return username -> hrUserRepository.findByUsername(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .authorities("HR")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }
}
