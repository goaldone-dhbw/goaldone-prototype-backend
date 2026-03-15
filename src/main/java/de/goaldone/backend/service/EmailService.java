package de.goaldone.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.invitation-link-base}")
    private String invitationLinkBase;

    public void sendInvitationEmail(String toEmail, String token, String organizationName) {
        String invitationUrl = invitationLinkBase + "/" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
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
        // Stub only for now — just log "Password reset email would be sent to: [email]"
        log.info("Password reset email would be sent to: {} with token: {}", toEmail, resetToken);
    }
}
