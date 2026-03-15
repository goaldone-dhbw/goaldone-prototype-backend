package de.goaldone.backend.controller;

import de.goaldone.backend.api.UsersApi;
import de.goaldone.backend.model.UpdateUserRequest;
import de.goaldone.backend.model.UserResponse;
import de.goaldone.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UsersController extends BaseController implements UsersApi {

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
}
