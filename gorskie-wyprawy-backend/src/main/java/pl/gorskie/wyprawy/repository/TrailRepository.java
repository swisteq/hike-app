package pl.gorskie.wyprawy.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.dto.TrailStats;
import pl.gorskie.wyprawy.model.Trail;

import java.util.List;

@Repository
public interface TrailRepository extends JpaRepository<Trail, Long> {

    @Query(value = """
        SELECT t.* FROM trails t
        WHERE (:minDistance IS NULL OR t.distance_km >= CAST(:minDistance AS DOUBLE PRECISION))
          AND (:maxDistance IS NULL OR t.distance_km <= CAST(:maxDistance AS DOUBLE PRECISION))
          AND (:minDuration IS NULL OR t.duration_minutes >= CAST(:minDuration AS INTEGER))
          AND (:maxDuration IS NULL OR t.duration_minutes <= CAST(:maxDuration AS INTEGER))
          AND (:minElevation IS NULL OR t.elevation_gain_m >= CAST(:minElevation AS INTEGER))
          AND (:maxElevation IS NULL OR t.elevation_gain_m <= CAST(:maxElevation AS INTEGER))
          AND (:nameQuery IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:nameQuery AS TEXT), '%')))
        """,
        countQuery = """
        SELECT COUNT(t.id) FROM trails t
        WHERE (:minDistance IS NULL OR t.distance_km >= CAST(:minDistance AS DOUBLE PRECISION))
          AND (:maxDistance IS NULL OR t.distance_km <= CAST(:maxDistance AS DOUBLE PRECISION))
          AND (:minDuration IS NULL OR t.duration_minutes >= CAST(:minDuration AS INTEGER))
          AND (:maxDuration IS NULL OR t.duration_minutes <= CAST(:maxDuration AS INTEGER))
          AND (:minElevation IS NULL OR t.elevation_gain_m >= CAST(:minElevation AS INTEGER))
          AND (:maxElevation IS NULL OR t.elevation_gain_m <= CAST(:maxElevation AS INTEGER))
          AND (:nameQuery IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:nameQuery AS TEXT), '%')))
        """,
        nativeQuery = true)
    Page<Trail> findWithFilters(
            @Param("minDistance") Double minDistance,
            @Param("maxDistance") Double maxDistance,
            @Param("minDuration") Integer minDuration,
            @Param("maxDuration") Integer maxDuration,
            @Param("minElevation") Integer minElevation,
            @Param("maxElevation") Integer maxElevation,
            @Param("nameQuery") String nameQuery,
            Pageable pageable
    );

    @Query("""
        SELECT new pl.gorskie.wyprawy.dto.TrailStats(
            COUNT(t),
            COALESCE(SUM(t.distanceKm), 0),
            COALESCE(MAX(t.distanceKm), 0),
            COALESCE(MAX(t.elevationGainM), 0)
        ) FROM Trail t
        """)
    TrailStats getStats();
}
