package pl.gorskie.wyprawy.dto;

import lombok.Data;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Set;

@Data
public class TrailFilterRequest {

    private Double minDistance;
    private Double maxDistance;
    private Integer minDuration;
    private Integer maxDuration;
    private Integer minElevation;
    private Integer maxElevation;
    private String name;

    private String sort = "name";
    private String dir = "asc";
    private int page = 0;
    private int size = 20;

    // Mapowanie nazw Java -> kolumny SQL (dla native query)
    private static final Map<String, String> SORT_FIELD_MAP = Map.of(
            "distanceKm",      "distance_km",
            "durationMinutes", "duration_minutes",
            "elevationGainM",  "elevation_gain_m",
            "maxElevationM",   "max_elevation_m",
            "name",            "name",
            "createdAt",       "created_at"
    );

    public String getSafeSortField() {
        return SORT_FIELD_MAP.getOrDefault(sort, "name");
    }

    public Sort.Direction getSortDirection() {
        return "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
