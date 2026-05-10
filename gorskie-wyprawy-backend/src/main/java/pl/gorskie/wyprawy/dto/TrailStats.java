package pl.gorskie.wyprawy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Statystyki zbiorcze wszystkich tras — używane w zapytaniu JPQL z konstruktorem.
 */
@Data
@AllArgsConstructor
public class TrailStats {
    private Long totalTrails;
    private Double totalDistanceKm;
    private Double longestTrailKm;
    private Integer highestElevationGainM;
}
