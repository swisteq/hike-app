package pl.gorskie.wyprawy.dto;

import lombok.Builder;
import lombok.Data;
import pl.gorskie.wyprawy.model.Trail;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO odpowiedzi API dla trasy.
 * Eksponuje tylko publiczne pola — ukrywa ścieżkę systemową pliku GPX.
 */
@Data
@Builder
public class TrailResponse {

    private Long id;
    private String name;
    private String description;

    // Metryki
    private Double distanceKm;
    private Integer elevationGainM;
    private Integer elevationLossM;
    private Integer maxElevationM;
    private Integer minElevationM;
    private Integer durationMinutes;
    private String durationFormatted;

    // Lokalizacja
    private Double startLat;
    private Double startLon;

    // Meta
    private String gpxFileName;
    private LocalDateTime createdAt;

    /**
     * Mapuje encję Trail na DTO odpowiedzi.
     */
    public static TrailResponse from(Trail trail) {
        return TrailResponse.builder()
                .id(trail.getId())
                .name(trail.getName())
                .description(trail.getDescription())
                .distanceKm(trail.getDistanceKm())
                .elevationGainM(trail.getElevationGainM())
                .elevationLossM(trail.getElevationLossM())
                .maxElevationM(trail.getMaxElevationM())
                .minElevationM(trail.getMinElevationM())
                .durationMinutes(trail.getDurationMinutes())
                .durationFormatted(trail.getDurationFormatted())
                .startLat(trail.getStartLat())
                .startLon(trail.getStartLon())
                .gpxFileName(trail.getGpxFileName())
                .createdAt(trail.getCreatedAt())
                .build();
    }
}
