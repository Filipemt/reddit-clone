package com.motadev.clone_reddit.notification.controller;

import com.motadev.clone_reddit.notification.dtos.response.NotificationResponseDTO;
import com.motadev.clone_reddit.notification.service.NotificationServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationServiceI notificationServiceI;

    public NotificationController(NotificationServiceI notificationServiceI) {
        this.notificationServiceI = notificationServiceI;
    }

    @GetMapping
    public ResponseEntity<PagedResponseDTO<NotificationResponseDTO>> listMine(
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.ok(notificationServiceI.listMine(pageable));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID notificationId) {
        notificationServiceI.markRead(notificationId);
        return ResponseEntity.noContent().build();
    }
}
