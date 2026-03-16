package de.goaldone.backend.service;

import de.goaldone.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public void sendInvitationEmail(String toEmail, String token, String organizationName) {
        String invitationUrl = appProperties.getFrontendUrl() +
                appProperties.getMail().getInvitationPath() +
                "?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.getMail().getFrom());
        message.setTo(toEmail);
        message.setSubject("You have been invited to join " + organizationName + " on Goaldone");
        message.setText("You have been invited to join " + organizationName + ".\n" +
                "Click the link below to create your account:\n" +
                invitationUrl + "\n" +
                "This link expires in 48 hours.");

        mailSender.send(message);
        log.info("Invitation email sent to {} for organization {}", toEmail, organizationName);
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetUrl = appProperties.getFrontendUrl() +
                appProperties.getMail().getPasswordResetPath() +
                "?token=" + resetToken;

        log.info("Password reset link for {}: {}", toEmail, resetUrl);
        // Full implementation will follow when password reset service is implemented
    }
}
