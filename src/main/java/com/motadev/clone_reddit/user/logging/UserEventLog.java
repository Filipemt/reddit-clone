package com.motadev.clone_reddit.user.logging;

import com.motadev.clone_reddit.user.service.impl.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserEventLog {

    private final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    public UserEventLog() {
    }

    public void registerSuccess(UUID userId, String username) {
        log.atInfo()
                .addKeyValue("event", "user.register.success")
                .addKeyValue("userId", userId)
                .addKeyValue("username", username)
                .setMessage("User registered")
                .log();
    }

    public void getSuccess(UUID userId) {
        log.atInfo()
                .addKeyValue("event", "user.get.success")
                .addKeyValue("userId", userId)
                .setMessage("User fetched")
                .log();
    }

    public void accountDeleted(UUID userId, String username) {
        log.atInfo()
                .addKeyValue("event", "user.account.deleted")
                .addKeyValue("userId", userId)
                .addKeyValue("username", username)
                .setMessage("User account soft deleted")
                .log();
    }

    public void registerConflictUsername(String username) {
        log.atWarn()
                .addKeyValue("event", "user.register.conflict")
                .addKeyValue("field", "username")
                .addKeyValue("username", username)
                .setMessage("Attempt to register with an existing username")
                .log();
    }

    public void registerConflictEmail() {
        log.atWarn()
                .addKeyValue("event", "user.register.conflict")
                .addKeyValue("field", "email")
                .setMessage("Attempt to register with an existing email")
                .log();
    }

    public void notFound(UUID userId) {
        log.atWarn()
                .addKeyValue("event", "user.not_found")
                .addKeyValue("userId", userId)
                .setMessage("User not found")
                .log();
    }
}
