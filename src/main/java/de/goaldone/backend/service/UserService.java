package de.goaldone.backend.service;

import de.goaldone.backend.entity.User;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.UpdateUserRequest;
import de.goaldone.backend.model.UserResponse;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateMyProfile(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        userRepository.save(user);

        return mapToUserResponse(user);
    }

    @Transactional
    public void deleteMyAccount(UUID userId) {
        userRepository.deleteById(userId);
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
