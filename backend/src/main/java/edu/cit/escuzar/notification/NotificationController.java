package edu.cit.escuzar.notification;

import edu.cit.escuzar.notification.dto.NotificationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:5173}")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications() {
        List<NotificationResponse> notifications = notificationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(n -> new NotificationResponse(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(notifications);
    }
}

