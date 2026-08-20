package com.acme.salary.service;

import com.acme.salary.dto.request.LoginRequest;
import com.acme.salary.dto.response.SessionUserResponse;
import com.acme.salary.entities.HrUser;
import com.acme.salary.exception.UnauthenticatedException;
import com.acme.salary.repository.HrUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final HrUserRepository hrUserRepository;

    /**
     * Authenticates and binds the security context to the HTTP session so the
     * session cookie carries the login across requests.
     */
    public SessionUserResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username(), request.password()));
        } catch (AuthenticationException e) {
            throw new UnauthenticatedException("Invalid username or password");
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true).setAttribute(
                "SPRING_SECURITY_CONTEXT", context);
        return currentUser(authentication.getName());
    }

    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
    }

    public SessionUserResponse currentUser(String username) {
        HrUser user = hrUserRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthenticatedException("Login required"));
        return new SessionUserResponse(user.getUsername(), user.getName());
    }
}
