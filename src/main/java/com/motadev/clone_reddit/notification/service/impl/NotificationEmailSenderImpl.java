package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.config.NotificationMailProperties;
import com.motadev.clone_reddit.notification.service.NotificationEmailSenderI;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class NotificationEmailSenderImpl implements NotificationEmailSenderI {

    private final JavaMailSender javaMailSender;
    private final NotificationMailProperties mailProperties;

    public NotificationEmailSenderImpl(JavaMailSender javaMailSender,NotificationMailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailProperties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            javaMailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send notification email", ex);
        }
    }
}
