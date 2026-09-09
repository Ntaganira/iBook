/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : MailService.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Email delivery service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String appUrl;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.url:http://localhost:8080}") String appUrl) {
        this.mailSender = mailSender;
        this.appUrl = appUrl;
    }

    public void sendPasswordResetEmail(String recipientEmail, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("Reset your Ebook Online password");
            message.setText("You requested a password reset for your Ebook Online account.\n\n"
                    + "Open the link below within 24 hours to choose a new password:\n"
                    + appUrl + "/reset-password?token=" + token);
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.warn("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage());
        }
    }
}