package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "expedition_days")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExpeditionDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_id", nullable = false)
    @ToString.Exclude
    private Expedition expedition;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

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

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lon")
    private Double startLon;

    @Column(name = "bbox_min_lat")
    private Double bboxMinLat;

    @Column(name = "bbox_max_lat")
    private Double bboxMaxLat;

    @Column(name = "bbox_min_lon")
    private Double bboxMinLon;

    @Column(name = "bbox_max_lon")
    private Double bboxMaxLon;

    @Column(name = "start_location_name")
    private String startLocationName;

    @Column(name = "end_location_name")
    private String endLocationName;

    @Column(name = "highest_peak_name")
    private String highestPeakName;

    @Column(name = "highest_peak_elevation_m")
    private Integer highestPeakElevationM;

    @Column(name = "accommodation_name")
    private String accommodationName;

    @Column(name = "accommodation_url")
    private String accommodationUrl;
}
