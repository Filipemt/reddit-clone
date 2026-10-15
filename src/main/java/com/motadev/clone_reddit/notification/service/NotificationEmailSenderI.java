package com.motadev.clone_reddit.notification.service;

public interface NotificationEmailSenderI {

    void send(String to, String subject, String htmlBody);
}
