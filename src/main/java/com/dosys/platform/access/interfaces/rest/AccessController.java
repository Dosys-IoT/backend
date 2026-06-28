package com.dosys.platform.access.interfaces.rest;

import com.dosys.platform.access.application.AccessService;
import com.dosys.platform.access.interfaces.rest.dto.request.LoginRequest;
import com.dosys.platform.access.interfaces.rest.dto.request.RegisterRequest;
import com.dosys.platform.access.interfaces.rest.dto.request.UpdateProfileRequest;
import com.dosys.platform.access.interfaces.rest.dto.response.LoginResponse;
import com.dosys.platform.access.interfaces.rest.dto.response.ProfileResponse;
import com.dosys.platform.access.interfaces.rest.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/access")
@Tag(name = "Access")
public class AccessController {

    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @Operation(summary = "Register new user")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return accessService.register(request);
    }

    @Operation(summary = "Authenticate user and issue JWT")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return accessService.login(request);
    }

    @Operation(summary = "Get authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return accessService.getAuthenticatedUser(principal.getName());
    }

    @Operation(summary = "Update authenticated user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/me")
    public ProfileResponse updateMe(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return accessService.updateAuthenticatedUser(principal.getName(), request);
    }
}
