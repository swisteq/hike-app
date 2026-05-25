package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expeditions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Expedition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    @Column(name = "trail_name")
    private String trailName;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "elevation_gain_m")
    private Integer elevationGainM;

    @Column(name = "elevation_loss_m")
    private Integer elevationLossM;

    @Column(name = "max_elevation_m")
    private Integer maxElevationM;

    @Column(name = "min_elevation_m")
    private Integer minElevationM;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "gpx_file_path")
    private String gpxFilePath;

    @Column(name = "invite_token", unique = true)
    private String inviteToken;

    @Column(name = "highest_peak_name")
    private String highestPeakName;

    @Column(name = "highest_peak_elevation_m")
    private Integer highestPeakElevationM;

    @Column(name = "start_location_name")
    private String startLocationName;

    @Column(name = "end_location_name")
    private String endLocationName;

    @Column(name = "bbox_min_lat")
    private Double bboxMinLat;

    @Column(name = "bbox_max_lat")
    private Double bboxMaxLat;

    @Column(name = "bbox_min_lon")
    private Double bboxMinLon;

    @Column(name = "bbox_max_lon")
    private Double bboxMaxLon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    @ToString.Exclude
    private User organizer;

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("routeIndex ASC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionLocation> locations = new ArrayList<>();

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("item ASC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionEquipment> equipment = new ArrayList<>();

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionAuditLog> auditLogs = new ArrayList<>();

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionDay> days = new ArrayList<>();

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionTransportSection> transportSections = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpeditionStatus status = ExpeditionStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_mode", nullable = false)
    @Builder.Default
    private JoinMode joinMode = JoinMode.AUTO;

    @Column(name = "status_declaration_notified")
    @Builder.Default
    private Boolean statusDeclarationNotified = Boolean.FALSE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ExpeditionStatus {
        PLANNED, ONGOING, COMPLETED, CANCELLED, UNREALIZED
    }

    public enum Visibility {
        PUBLIC,             // widoczna dla wszystkich
        FRIENDS_ONLY,       // widoczna tylko dla znajomych organizatora
        GROUPS_ONLY,        // widoczna tylko dla członków wspólnych grup
        FRIENDS_AND_GROUPS  // widoczna dla znajomych i wspólnych grup
    }

    public enum JoinMode {
        AUTO,              // dołączenie od razu zatwierdzone
        APPROVAL_REQUIRED  // wymaga akceptacji organizatora
    }
}
