package edu.cit.escuzar.notification.dto;

import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        String message,
        Instant createdAt
) {
}

