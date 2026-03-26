package de.goaldone.backend.controller;

import de.goaldone.backend.api.UsersApi;
import de.goaldone.backend.model.UpdateUserRequest;
import de.goaldone.backend.model.UserResponse;
import de.goaldone.backend.model.UpsertWorkingHoursRequest;
import de.goaldone.backend.model.WorkingHoursResponse;
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

    @Override
    public ResponseEntity<WorkingHoursResponse> getWorkingHours() {
        // TODO: Implement working hours retrieval
        return ResponseEntity.ok(WorkingHoursResponse.builder().build());
    }

    @Override
    public ResponseEntity<WorkingHoursResponse> upsertWorkingHours(UpsertWorkingHoursRequest upsertWorkingHoursRequest) {
        // TODO: Implement working hours persistence
        return ResponseEntity.ok(WorkingHoursResponse.builder()
                .days(upsertWorkingHoursRequest.getDays())
                .build());
    }
}
