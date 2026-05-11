package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "expedition_transport_options")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionTransportOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    @ToString.Exclude
    private ExpeditionTransportSection section;

    @Column(name = "meeting_point")
    private String meetingPoint;

    @Column(name = "meeting_point_url")
    private String meetingPointUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type")
    private TransportType transportType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column
    private String url;

    @Column
    private Integer seats;

    @Column(name = "driver_username")
    private String driverUsername;

    @Column(nullable = false)
    @Builder.Default
    private boolean approved = false;

    public enum TransportType {
        CAR, PUBLIC_TRANSPORT
    }
}
