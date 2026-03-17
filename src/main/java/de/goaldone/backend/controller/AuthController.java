package de.goaldone.backend.controller;

import de.goaldone.backend.api.AuthApi;
import de.goaldone.backend.model.*;
import de.goaldone.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        AuthService.LoginResult result = authService.login(loginRequest);

        ResponseCookie cookie = createRefreshTokenCookie(result.rawRefreshToken(), Duration.ofDays(7));

        LoginResponse response = LoginResponse.builder()
                .accessToken(result.accessToken())
                .user(result.user())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @Override
    public ResponseEntity<RefreshResponse> refreshToken(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is missing");
        }

        AuthService.RefreshResult result = authService.refresh(refreshToken);

        ResponseCookie cookie = createRefreshTokenCookie(result.newRawRefreshToken(), Duration.ofDays(7));

        RefreshResponse response = RefreshResponse.builder()
                .accessToken(result.accessToken())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @Override
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }

        ResponseCookie cookie = createRefreshTokenCookie("", Duration.ZERO);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest changePasswordRequest) {
        authService.changePassword(changePasswordRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<LoginResponse> acceptInvitation(String token, AcceptInvitationRequest acceptInvitationRequest) {
        AuthService.LoginResult result = authService.acceptInvitation(token, acceptInvitationRequest);

        ResponseCookie cookie = createRefreshTokenCookie(result.rawRefreshToken(), Duration.ofDays(7));

        LoginResponse response = LoginResponse.builder()
                .accessToken(result.accessToken())
                .user(result.user())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private ResponseCookie createRefreshTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true) // Set to true for production, browser might ignore it on http/localhost if not handled
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
