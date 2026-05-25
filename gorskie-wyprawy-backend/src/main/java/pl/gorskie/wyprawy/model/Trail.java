package pl.gorskie.wyprawy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Encja reprezentująca trasę górską zaimportowaną z pliku GPX.
 * Wszystkie pola metryczne są wyliczane przy imporcie z danych GPX.
 */
@Entity
@Table(name = "trails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Dane podstawowe ---

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Ścieżka do zapisanego pliku GPX na dysku */
    @Column(name = "gpx_file_path")
    private String gpxFilePath;

    /** Oryginalna nazwa pliku GPX */
    @Column(name = "gpx_file_name")
    private String gpxFileName;

    // --- Metryki wyliczane z GPX ---

    /** Całkowity dystans trasy w kilometrach */
    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    /** Łączne przewyższenie (suma podejść) w metrach */
    @Column(name = "elevation_gain_m", nullable = false)
    private Integer elevationGainM;

    /** Łączne zejście (suma zejść) w metrach, wartość dodatnia */
    @Column(name = "elevation_loss_m", nullable = false)
    private Integer elevationLossM;

    /** Maksymalna wysokość na trasie w metrach n.p.m. */
    @Column(name = "max_elevation_m")
    private Integer maxElevationM;

    /** Minimalna wysokość na trasie w metrach n.p.m. */
    @Column(name = "min_elevation_m")
    private Integer minElevationM;

    /** Czas przejścia trasy w minutach (z danych time w GPX) */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // --- Lokalizacja ---

    /**
     * Tagi lokalizacyjne — nazwy szczytów, dolin, schronisk.
     * Dodawane ręcznie przy imporcie lub przez reverse geocoding.
     * Przechowywane jako tablica w PostgreSQL.
     */
    /** Szerokość geograficzna punktu startowego */
    @Column(name = "start_lat")
    private Double startLat;

    /** Długość geograficzna punktu startowego */
    @Column(name = "start_lon")
    private Double startLon;

    // --- Metadane ---

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- Metody pomocnicze ---

    /** Zwraca czas trwania w formacie "Xh Ym" */
    @Transient
    public String getDurationFormatted() {
        if (durationMinutes == null) return "–";
        int h = durationMinutes / 60;
        int m = durationMinutes % 60;
        return h > 0 ? h + "h " + m + "min" : m + "min";
    }
}
