package de.goaldone.backend.controller;

import de.goaldone.backend.api.UsersApi;
import de.goaldone.backend.model.UpdateUserRequest;
import de.goaldone.backend.model.UserResponse;
import de.goaldone.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UsersController implements UsersApi {

    private final UserService userService;

    @Override
    public ResponseEntity<Void> deleteMyAccount() {
        userService.deleteMyAccount(getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile(getCurrentUserId()));
    }

    @Override
    public ResponseEntity<UserResponse> updateMyProfile(UpdateUserRequest updateUserRequest) {
        return ResponseEntity.ok(userService.updateMyProfile(getCurrentUserId(), updateUserRequest));
    }

    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        // If we change JwtAuthFilter to use userDetails as principal
        // if (principal instanceof GoaldoneUserDetails) {
        //     return ((GoaldoneUserDetails) principal).getUserId();
        // }
        throw new RuntimeException("Current user not found in security context");
    }
}
