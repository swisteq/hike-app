package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gorskie.wyprawy.model.ExpeditionTransportSection;

import java.util.List;
import java.util.Optional;

public interface ExpeditionTransportSectionRepository extends JpaRepository<ExpeditionTransportSection, Long> {

    Optional<ExpeditionTransportSection> findByExpeditionIdAndSectionTypeAndExpeditionDayId(
            Long expeditionId,
            ExpeditionTransportSection.SectionType sectionType,
            Long expeditionDayId);

}
