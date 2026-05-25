package pl.gorskie.wyprawy.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Wynik parsowania pliku GPX — zawiera wszystkie wyliczone metryki trasy.
 */
@Data
@Builder
public class GpxParseResult {

    private String name;

    // Metryki
    private double distanceKm;
    private int elevationGainM;
    private int elevationLossM;
    private int maxElevationM;
    private int minElevationM;
    private Integer durationMinutes;
    // Punkt startowy
    private double startLat;
    private double startLon;

    // Nazwy z waypointów GPX
    private String startWaypointName;
    private String endWaypointName;

    // Bounding box
    private double bboxMinLat;
    private double bboxMaxLat;
    private double bboxMinLon;
    private double bboxMaxLon;
}
