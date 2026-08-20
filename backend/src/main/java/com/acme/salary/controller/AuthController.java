package com.acme.salary.controller;

import com.acme.salary.dto.request.LoginRequest;
import com.acme.salary.dto.response.SessionUserResponse;
import com.acme.salary.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public SessionUserResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
    }

    @GetMapping("/me")
    public SessionUserResponse me(Principal principal) {
        return authService.currentUser(principal.getName());
    }
}
