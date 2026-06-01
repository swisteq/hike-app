package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "expedition_day_gpx_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionDayGpxData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_day_id", nullable = false, unique = true)
    @ToString.Exclude
    private ExpeditionDay expeditionDay;

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
}
