package pl.gorskie.wyprawy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.gorskie.wyprawy.model.Notification;
import pl.gorskie.wyprawy.security.CurrentUserResolver;
import pl.gorskie.wyprawy.service.NotificationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll() {
        Long userId = currentUser.getCurrentUserId();
        List<NotificationResponse> list = notificationService.getForUser(userId).stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        long count = notificationService.countUnread(currentUser.getCurrentUserId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead(currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    public record NotificationResponse(
            Long id,
            String type,
            String message,
            String relatedUrl,
            boolean read,
            LocalDateTime createdAt
    ) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getId(), n.getType().name(), n.getMessage(),
                    n.getRelatedUrl(), n.isRead(), n.getCreatedAt());
        }
    }
}
