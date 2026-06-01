package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "gpx_file_path")
    private String gpxFilePath;

    @OneToOne(mappedBy = "expeditionDay", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private ExpeditionDayGpxData gpxData;

    @Column(name = "accommodation_name")
    private String accommodationName;

    @Column(name = "accommodation_url")
    private String accommodationUrl;

    @OneToMany(mappedBy = "expeditionDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("routeIndex ASC")
    @Builder.Default
    @ToString.Exclude
    private List<ExpeditionLocation> locations = new ArrayList<>();
}
