package com.acme.salary.service;

import com.acme.salary.dto.request.LoginRequest;
import com.acme.salary.dto.response.SessionUserResponse;
import com.acme.salary.entities.HrUser;
import com.acme.salary.exception.UnauthenticatedException;
import com.acme.salary.repository.HrUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HrUserRepository hrUserRepository;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpSession session;

    @InjectMocks
    private AuthService service;

    private HrUser hrUser() {
        HrUser user = new HrUser();
        user.setUsername("hr");
        user.setName("HR Manager");
        return user;
    }

    @Test
    void loginBindsSecurityContextToSessionAndReturnsUser() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated("hr", null, null));
        when(hrUserRepository.findByUsername("hr")).thenReturn(Optional.of(hrUser()));
        when(httpRequest.getSession(true)).thenReturn(session);

        SessionUserResponse response = service.login(new LoginRequest("hr", "secret"), httpRequest);

        assertThat(response.username()).isEqualTo("hr");
        assertThat(response.name()).isEqualTo("HR Manager");
        verify(session).setAttribute(eq("SPRING_SECURITY_CONTEXT"), any());
    }

    @Test
    void badCredentialsBecomeUnauthenticated() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new LoginRequest("hr", "wrong"), httpRequest))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void currentUserResolvesFromRepository() {
        when(hrUserRepository.findByUsername("hr")).thenReturn(Optional.of(hrUser()));

        assertThat(service.currentUser("hr").name()).isEqualTo("HR Manager");
    }
}
