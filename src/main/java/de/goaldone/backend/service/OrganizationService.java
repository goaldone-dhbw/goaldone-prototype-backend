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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final EmailService emailService;
    private final ValidationService validationService;

    public OrganizationResponse getMyOrganization(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        return mapToOrganizationResponse(org);
    }

    @Transactional
    public OrganizationResponse updateSettings(UUID orgId, UpdateOrganizationSettingsRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        org.setName(request.getName());
        if (request.getAllowedDomain() != null) {
            org.setAllowedDomain(request.getAllowedDomain().orElse(null));
        }
        organizationRepository.save(org);

        return mapToOrganizationResponse(org);
    }

    public MemberPage listMembers(UUID orgId, Pageable pageable) {
        Page<User> members = userRepository.findByOrganizationId(orgId, pageable);
        return MemberPage.builder()
                .page(members.getNumber())
                .size(members.getSize())
                .totalElements((int) members.getTotalElements())
                .totalPages(members.getTotalPages())
                .content(members.getContent().stream().map(this::mapToMemberResponse).toList())
                .build();
    }

    @Transactional
    public void removeMember(UUID orgId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (!user.getOrganization().getId().equals(orgId)) {
            throw new AccessDeniedException("Member does not belong to your organization");
        }

        if (user.getRole() == de.goaldone.backend.entity.enums.Role.ADMIN) {
            long adminCount = userRepository.countByOrganizationIdAndRole(orgId, de.goaldone.backend.entity.enums.Role.ADMIN);
            if (adminCount <= 1) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "last-admin-cannot-be-removed");
            }
        }

        userRepository.delete(user);
    }

    @Transactional
    public MemberResponse updateMemberRole(UUID orgId, UUID userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (!user.getOrganization().getId().equals(orgId)) {
            throw new AccessDeniedException("Member does not belong to your organization");
        }

        user.setRole(role);
        userRepository.save(user);

        return mapToMemberResponse(user);
    }

    @Transactional
    public InvitationResponse createInvitation(UUID orgId, String email, UUID invitedByUserId) {
        validationService.requireNotBlank(email, "email");
        validationService.requireValidEmail(email);

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("User with this email is already a member");
        }
        if (invitationRepository.existsByEmailAndOrganizationId(email, orgId)) {
            throw new ConflictException("An open invitation already exists for this email");
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        User invitedBy = userRepository.findById(invitedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Inviting user not found"));

        Invitation invitation = Invitation.builder()
                .email(email)
                .organization(org)
                .invitedBy(invitedBy)
                .token(UUID.randomUUID().toString())
                .role(Role.USER)
                .expiresAt(Instant.now().plusSeconds(48 * 3600)) // 48 hours
                .build();

        invitationRepository.save(invitation);

        log.info("Invitation created for {} in organization {}. Token: {}", email, org.getName(), invitation.getToken());

        try {
            emailService.sendInvitationEmail(invitation.getEmail(), invitation.getToken(), org.getName());
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", email, e.getMessage());
        }

        return mapToInvitationResponse(invitation);
    }

    public InvitationPage listInvitations(UUID orgId, Pageable pageable) {
        Page<Invitation> invitations = invitationRepository.findByOrganizationId(orgId, pageable);
        return InvitationPage.builder()
                .page(invitations.getNumber())
                .size(invitations.getSize())
                .totalElements((int) invitations.getTotalElements())
                .totalPages(invitations.getTotalPages())
                .content(invitations.getContent().stream().map(this::mapToInvitationResponse).toList())
                .build();
    }

    @Transactional
    public void revokeInvitation(UUID orgId, UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (!invitation.getOrganization().getId().equals(orgId)) {
            throw new AccessDeniedException("Invitation does not belong to your organization");
        }

        invitationRepository.delete(invitation);
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

    private MemberResponse mapToMemberResponse(User user) {
        return MemberResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(de.goaldone.backend.model.Role.fromValue(user.getRole().name()))
                .joinedAt(OffsetDateTime.ofInstant(user.getCreatedAt(), ZoneOffset.UTC))
                .build();
    }

    private InvitationResponse mapToInvitationResponse(Invitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .organizationId(invitation.getOrganization().getId())
                .expiresAt(OffsetDateTime.ofInstant(invitation.getExpiresAt(), ZoneOffset.UTC))
                .createdAt(OffsetDateTime.ofInstant(invitation.getCreatedAt(), ZoneOffset.UTC))
                .build();
    }
}
