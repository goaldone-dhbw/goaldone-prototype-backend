package de.goaldone.backend.scheduler;

import de.goaldone.backend.repository.InvitationRepository;
import de.goaldone.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class CleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final InvitationRepository invitationRepository;

    @Scheduled(cron = "${app.scheduler.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
        if (deleted > 0) {
            log.info("Cleanup: removed {} expired/revoked refresh tokens", deleted);
        }
    }

    @Scheduled(cron = "${app.scheduler.invitation-cleanup-cron:0 15 2 * * *}")
    @Transactional
    public void cleanupExpiredInvitations() {
        int deleted = invitationRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleanup: removed {} expired invitations", deleted);
        }
    }
}
