package de.goaldone.backend.controller;

import de.goaldone.backend.api.OrganizationsApi;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.model.*;
import de.goaldone.backend.security.GoaldoneUserDetails;
import de.goaldone.backend.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrganizationsController implements OrganizationsApi {

    private final OrganizationService organizationService;

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<InvitationResponse> createInvitation(CreateInvitationRequest createInvitationRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createInvitation(getCurrentOrgId(), createInvitationRequest.getEmail(), getCurrentUserId()));
    }

    @Override
    public ResponseEntity<OrganizationResponse> getMyOrganization() {
        return ResponseEntity.ok(organizationService.getMyOrganization(getCurrentOrgId()));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<InvitationPage> listInvitations(Integer page, Integer size) {
        return ResponseEntity.ok(organizationService.listInvitations(getCurrentOrgId(), PageRequest.of(page, size)));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<MemberPage> listMembers(Integer page, Integer size) {
        return ResponseEntity.ok(organizationService.listMembers(getCurrentOrgId(), PageRequest.of(page, size)));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> removeMember(UUID userId) {
        organizationService.removeMember(getCurrentOrgId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> revokeInvitation(UUID invitationId) {
        organizationService.revokeInvitation(getCurrentOrgId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<MemberResponse> updateMemberRole(UUID userId, UpdateMemberRoleRequest updateMemberRoleRequest) {
        Role entityRole = Role.valueOf(updateMemberRoleRequest.getRole().getValue());
        return ResponseEntity.ok(organizationService.updateMemberRole(getCurrentOrgId(), userId, entityRole));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<OrganizationResponse> updateOrganizationSettings(UpdateOrganizationSettingsRequest updateOrganizationSettingsRequest) {
        return ResponseEntity.ok(organizationService.updateSettings(getCurrentOrgId(), updateOrganizationSettingsRequest));
    }

    private UUID getCurrentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private UUID getCurrentOrgId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (details instanceof GoaldoneUserDetails) {
            UUID orgId = ((GoaldoneUserDetails) details).getOrganizationId();
            if (orgId == null) {
                throw new RuntimeException("Current user has no organization");
            }
            return orgId;
        }
        throw new RuntimeException("GoaldoneUserDetails not found in security context");
    }
}
