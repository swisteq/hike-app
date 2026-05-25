package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.gorskie.wyprawy.model.ExpeditionLocation;

@Repository
public interface ExpeditionLocationRepository extends JpaRepository<ExpeditionLocation, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM ExpeditionLocation l WHERE l.expedition.id = :expeditionId AND l.dayNumber = :dayNumber")
    void deleteByExpeditionIdAndDayNumber(@Param("expeditionId") Long expeditionId, @Param("dayNumber") Integer dayNumber);

    @Modifying
    @Transactional
    @Query("DELETE FROM ExpeditionLocation l WHERE l.expedition.id = :expeditionId AND l.dayNumber IS NULL")
    void deleteByExpeditionIdAndDayNumberIsNull(@Param("expeditionId") Long expeditionId);
}
