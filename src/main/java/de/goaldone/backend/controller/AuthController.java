package de.goaldone.backend.controller;

import de.goaldone.backend.api.AuthApi;
import de.goaldone.backend.model.*;
import de.goaldone.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @Override
    public ResponseEntity<RefreshResponse> refreshToken(RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refresh(refreshRequest));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequest refreshRequest) {
        authService.logout(refreshRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest changePasswordRequest) {
        authService.changePassword(changePasswordRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<LoginResponse> acceptInvitation(String token, AcceptInvitationRequest acceptInvitationRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.acceptInvitation(token, acceptInvitationRequest));
    }
}
