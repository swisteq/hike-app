package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionDayGpxData;

@Repository
public interface ExpeditionDayGpxDataRepository extends JpaRepository<ExpeditionDayGpxData, Long> {
}
