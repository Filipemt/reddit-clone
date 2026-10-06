package com.motadev.clone_reddit.shared.logging;

import com.motadev.clone_reddit.shared.config.AdminUserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminEventLog {

    private final Logger log = LoggerFactory.getLogger(AdminUserConfig.class);

    public AdminEventLog() {
    }

    public void seedSkipped(String username) {
        log.atInfo()
                .addKeyValue("event", "admin.seed.skipped")
                .addKeyValue("username", username)
                .setMessage("Admin user already exists")
                .log();
    }

    public void seedCreated(String username) {
        log.atInfo()
                .addKeyValue("event", "admin.seed.created")
                .addKeyValue("username", username)
                .setMessage("Admin user seeded")
                .log();
    }
}
