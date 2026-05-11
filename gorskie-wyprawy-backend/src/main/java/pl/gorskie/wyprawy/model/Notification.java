package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    @ToString.Exclude
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "related_url")
    private String relatedUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        FRIEND_REQUEST_RECEIVED,
        FRIEND_REQUEST_ACCEPTED,
        FRIEND_REQUEST_DECLINED,
        GROUP_INVITATION,
        GROUP_JOIN_REQUEST,
        GROUP_JOIN_APPROVED,
        GROUP_JOIN_DECLINED,
        EXPEDITION_INVITATION,
        EXPEDITION_JOIN_REQUEST,
        EXPEDITION_JOIN_APPROVED,
        EXPEDITION_JOIN_DECLINED,
        EXPEDITION_STATUS_CHANGED,
        EXPEDITION_STATUS_DECLARATION_REQUIRED
    }
}
