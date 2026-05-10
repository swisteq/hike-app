package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "geonames_features")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeonamesFeature {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "ascii_name")
    private String asciiName;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "feature_class", nullable = false, length = 1)
    private String featureClass;

    @Column(name = "feature_code", nullable = false, length = 10)
    private String featureCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "elevation_m")
    private Integer elevationM;
}
