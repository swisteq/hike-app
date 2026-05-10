package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionDay;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpeditionDayRepository extends JpaRepository<ExpeditionDay, Long> {
    List<ExpeditionDay> findByExpeditionIdOrderByDayNumberAsc(Long expeditionId);
    Optional<ExpeditionDay> findByExpeditionIdAndDayNumber(Long expeditionId, int dayNumber);
}
