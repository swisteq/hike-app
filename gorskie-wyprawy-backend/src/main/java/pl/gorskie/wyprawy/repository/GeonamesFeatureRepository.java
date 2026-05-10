package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.GeonamesFeature;

import java.util.List;

@Repository
public interface GeonamesFeatureRepository extends JpaRepository<GeonamesFeature, Long> {

    @Query("""
        SELECT g FROM GeonamesFeature g
        WHERE g.latitude  BETWEEN :minLat AND :maxLat
          AND g.longitude BETWEEN :minLon AND :maxLon
        """)
    List<GeonamesFeature> findWithinBbox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon
    );

    boolean existsBy();
}
