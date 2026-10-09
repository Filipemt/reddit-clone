package com.motadev.clone_reddit.user.service;

import java.util.UUID;

public interface UserAccountDeletionHandler {

    void onAccountDeleted(UUID userId);
}
