package com.dosys.platform.access.application;

import com.dosys.platform.access.domain.Role;
import com.dosys.platform.access.domain.User;
import com.dosys.platform.access.infrastructure.UserRepository;
import com.dosys.platform.access.interfaces.rest.dto.request.LoginRequest;
import com.dosys.platform.access.interfaces.rest.dto.request.RegisterRequest;
import com.dosys.platform.access.interfaces.rest.dto.response.LoginResponse;
import com.dosys.platform.access.interfaces.rest.dto.response.UserResponse;
import com.dosys.platform.shared.exception.DuplicateResourceException;
import com.dosys.platform.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AccessService accessService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("Ana", "Lopez", "ana@test.com", "StrongPass123");
    }

    @Test
    void registerDoesNotExposePasswordInResponse() {
        when(userRepository.existsByEmailIgnoreCase("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            return user;
        });

        UserResponse response = accessService.register(registerRequest);

        assertThat(response.email()).isEqualTo("ana@test.com");
        assertThat(response).extracting(UserResponse::firstName, UserResponse::lastName).containsExactly("Ana", "Lopez");
    }

    @Test
    void registerStoresEncodedPasswordInsteadOfPlainText() {
        when(userRepository.existsByEmailIgnoreCase("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accessService.register(registerRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("StrongPass123");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("ana@test.com")).thenReturn(true);

        assertThatThrownBy(() -> accessService.register(registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> accessService.login(new LoginRequest("ana@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginReturnsBearerTokenForValidCredentials() {
        User user = new User();
        user.setEmail("ana@test.com");
        user.setRole(Role.USER);

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtService.generateToken("ana@test.com", "USER")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = accessService.login(new LoginRequest("ana@test.com", "StrongPass123"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("ana@test.com");
    }
}
