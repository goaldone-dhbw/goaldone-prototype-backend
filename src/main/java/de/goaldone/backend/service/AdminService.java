package de.goaldone.backend.service;

import de.goaldone.backend.entity.Invitation;
import de.goaldone.backend.entity.Organization;
import de.goaldone.backend.entity.User;
import de.goaldone.backend.entity.enums.Role;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.InvitationRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final EmailService emailService;

    public OrganizationPage listOrganizations(Pageable pageable) {
        Page<Organization> orgs = organizationRepository.findAll(pageable);
        return OrganizationPage.builder()
                .page(orgs.getNumber())
                .size(orgs.getSize())
                .totalElements((int) orgs.getTotalElements())
                .totalPages(orgs.getTotalPages())
                .content(orgs.getContent().stream().map(this::mapToOrganizationResponse).toList())
                .build();
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new ConflictException("Organization with this name already exists");
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .adminEmail(request.getAdminEmail())
                .build();
        organizationRepository.save(org);

        // Create initial invitation for the admin email
        Invitation invitation = Invitation.builder()
                .email(request.getAdminEmail())
                .organization(org)
                .token(UUID.randomUUID().toString())
                .role(Role.ADMIN)
                .expiresAt(Instant.now().plusSeconds(48 * 3600)) // 48 hours
                .build();
        invitationRepository.save(invitation);

        log.info("Organization created: {}. Admin invitation token: {}", org.getName(), invitation.getToken());

        try {
            emailService.sendInvitationEmail(invitation.getEmail(), invitation.getToken(), org.getName());
        } catch (Exception e) {
            log.error("Failed to send initial admin invitation email to {}: {}", request.getAdminEmail(), e.getMessage());
        }

        return mapToOrganizationResponse(org);
    }

    @Transactional
    public void deleteOrganization(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization not found");
        }
        organizationRepository.deleteById(orgId);
    }

    @Transactional
    public UserResponse addSuperAdmin(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setRole(Role.SUPER_ADMIN);
        userRepository.save(user);

        return mapToUserResponse(user);
    }

    private OrganizationResponse mapToOrganizationResponse(Organization org) {
        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .adminEmail(org.getAdminEmail())
                .allowedDomain(JsonNullable.of(org.getAllowedDomain()))
                .createdAt(OffsetDateTime.ofInstant(org.getCreatedAt(), ZoneOffset.UTC))
                .build();
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
