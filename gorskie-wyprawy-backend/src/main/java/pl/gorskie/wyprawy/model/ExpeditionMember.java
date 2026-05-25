package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "expedition_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expedition_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_id", nullable = false)
    @ToString.Exclude
    private Expedition expedition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.INVITED;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role")
    @Builder.Default
    private MemberRole memberRole = MemberRole.MEMBER;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum MemberStatus {
        INVITED,  // zaproszony przez organizatora — czeka na akceptację użytkownika
        PENDING,  // użytkownik poprosił o dołączenie — czeka na akceptację organizatora
        ACCEPTED  // potwierdził udział
    }

    public enum MemberRole {
        MEMBER,
        NAWIGATOR,
        LOGISTYK
    }
}
