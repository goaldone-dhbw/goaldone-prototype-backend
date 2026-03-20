package de.goaldone.backend.service;

import de.goaldone.backend.entity.Invitation;
import de.goaldone.backend.entity.RefreshToken;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.exception.GoneException;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.InvitationRepository;
import de.goaldone.backend.repository.RefreshTokenRepository;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ValidationService validationService;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpirySeconds;

    public record LoginResult(String accessToken, String rawRefreshToken, UserResponse user) {}
    public record RefreshResult(String accessToken, String newRawRefreshToken) {}

    @Transactional
    public LoginResult login(LoginRequest request) {
        validationService.requireNotBlank(request.getEmail(), "email");
        validationService.requireValidEmail(request.getEmail());
        validationService.requireNotBlank(request.getPassword(), "password");

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        saveRefreshToken(user, refreshToken);

        return new LoginResult(accessToken, refreshToken, mapToUserResponse(user));
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is missing");
        }

        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token is revoked or expired");
        }

        // Token rotation: revoke old token
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        saveRefreshToken(user, refreshToken);

        return new RefreshResult(accessToken, refreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        validationService.requireNotBlank(request.getCurrentPassword(), "currentPassword");
        validationService.requireNotBlank(request.getNewPassword(), "newPassword");
        validationService.requireMinLength(request.getNewPassword(), "newPassword", 8);

        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findById(UUID.fromString(userIdStr))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens
        refreshTokenRepository.deleteByUserIdAndRevokedAtIsNull(user.getId());
    }

    @Transactional(readOnly = true)
    public InvitationTokenInfoResponse getInvitationInfo(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new GoneException("Invitation expired");
        }

        return InvitationTokenInfoResponse.builder()
                .email(invitation.getEmail())
                .build();
    }

    @Transactional
    public LoginResult acceptInvitation(String token, AcceptInvitationRequest request) {
        validationService.requireNotBlank(request.getFirstName(), "firstName");
        validationService.requireMaxLength(request.getFirstName(), "firstName", 100);
        validationService.requireNotBlank(request.getLastName(), "lastName");
        validationService.requireMaxLength(request.getLastName(), "lastName", 100);
        validationService.requireNotBlank(request.getPassword(), "password");
        validationService.requireMinLength(request.getPassword(), "password", 8);

        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new GoneException("Invitation expired");
        }

        User user = User.builder()
                .email(invitation.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(invitation.getRole())
                .organization(invitation.getOrganization())
                .build();

        userRepository.save(user);
        invitationRepository.delete(invitation);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        saveRefreshToken(user, refreshToken);

        return new LoginResult(accessToken, refreshToken, mapToUserResponse(user));
    }

    private void saveRefreshToken(User user, String rawToken) {
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawToken))
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(de.goaldone.backend.model.Role.fromValue(user.getRole().name()))
                .organizationId(user.getOrganization() != null ? JsonNullable.of(user.getOrganization().getId()) : JsonNullable.<UUID>undefined())
                .createdAt(OffsetDateTime.ofInstant(user.getCreatedAt(), ZoneOffset.UTC))
                .build();
    }
}
