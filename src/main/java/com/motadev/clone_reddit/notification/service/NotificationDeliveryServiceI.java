package com.motadev.clone_reddit.notification.service;

import com.motadev.clone_reddit.notification.entity.Notification;

public interface NotificationDeliveryServiceI {

    boolean deliver(Notification notification);
}
