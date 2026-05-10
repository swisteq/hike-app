package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "expedition_locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_id", nullable = false)
    @ToString.Exclude
    private Expedition expedition;

    @Column(name = "geonames_id")
    private Long geonamesId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "feature_class", nullable = false, length = 1)
    private String featureClass;

    @Column(name = "feature_code", nullable = false, length = 10)
    private String featureCode;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "route_index")
    private Integer routeIndex;

    @Column(name = "type_label_pl")
    private String typeLabelPl;

    @Column(name = "day_number")
    private Integer dayNumber;
}
