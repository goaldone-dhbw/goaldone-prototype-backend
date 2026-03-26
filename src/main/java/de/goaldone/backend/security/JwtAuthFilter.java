package de.goaldone.backend.security;

import de.goaldone.backend.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Authorization header does not start with 'Bearer ': {}", authHeader);
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();

        if (jwt.isEmpty()) {
            log.warn("JWT part of Authorization header is empty");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.validateToken(jwt);
            UUID userId = UUID.fromString(claims.getSubject());
            String orgIdStr = claims.get("organizationId", String.class);
            UUID organizationId = orgIdStr != null ? UUID.fromString(orgIdStr) : null;
            Role role = Role.valueOf(claims.get("role", String.class));

            log.info("Successfully validated JWT for user: {}, role: {}", userId, role);

            GoaldoneUserDetails userDetails = GoaldoneUserDetails.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .role(role)
                    .build();

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    userDetails.getAuthorities()
            );

            authToken.setDetails(userDetails);

            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.info("JWT expired: {}", e.getMessage());
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.info("Malformed JWT received: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.info("Invalid JWT signature: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}
