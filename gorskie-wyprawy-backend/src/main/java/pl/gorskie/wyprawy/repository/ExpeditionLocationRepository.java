package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionLocation;

@Repository
public interface ExpeditionLocationRepository extends JpaRepository<ExpeditionLocation, Long> {
    void deleteByExpeditionIdAndDayNumber(Long expeditionId, Integer dayNumber);
    void deleteByExpeditionIdAndDayNumberIsNull(Long expeditionId);
}
