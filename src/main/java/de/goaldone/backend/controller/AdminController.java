package de.goaldone.backend.controller;

import de.goaldone.backend.api.AdminApi;
import de.goaldone.backend.model.AddSuperAdminRequest;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.model.OrganizationPage;
import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.model.UserResponse;
import de.goaldone.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController extends BaseController implements AdminApi {

    private final AdminService adminService;

    @Override
    public ResponseEntity<OrganizationPage> listOrganizations(Integer page, Integer size) {
        return ResponseEntity.ok(adminService.listOrganizations(PageRequest.of(page, size)));
    }

    @Override
    public ResponseEntity<OrganizationResponse> createOrganization(CreateOrganizationRequest createOrganizationRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createOrganization(createOrganizationRequest));
    }

    @Override
    public ResponseEntity<Void> deleteOrganization(UUID orgId) {
        adminService.deleteOrganization(orgId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserResponse> addSuperAdmin(AddSuperAdminRequest addSuperAdminRequest) {
        return ResponseEntity.ok(adminService.addSuperAdmin(addSuperAdminRequest.getEmail()));
    }

    @Override
    public ResponseEntity<Void> deleteSuperAdmin(UUID superAdminId) {
        adminService.deleteSuperAdmin(superAdminId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
