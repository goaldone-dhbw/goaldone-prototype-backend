package de.goaldone.backend.controller;

import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.security.GoaldoneUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public abstract class BaseController {

    protected UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        throw new AccessDeniedException("Current user not found in security context");
    }

    protected UUID getCurrentOrgId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (details instanceof GoaldoneUserDetails) {
            UUID orgId = ((GoaldoneUserDetails) details).getOrganizationId();
            if (orgId == null) {
                // If it's a SUPER_ADMIN, they might not have an orgId.
                // But most /me endpoints require one.
                throw new AccessDeniedException("You do not belong to an organization");
            }
            return orgId;
        }
        throw new AccessDeniedException("User details not found in security context");
    }
}
