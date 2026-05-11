package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gorskie.wyprawy.model.ExpeditionTransportSection;

import java.util.List;
import java.util.Optional;

public interface ExpeditionTransportSectionRepository extends JpaRepository<ExpeditionTransportSection, Long> {

    List<ExpeditionTransportSection> findByExpeditionIdOrderBySectionTypeAscDayNumberAsc(Long expeditionId);

    Optional<ExpeditionTransportSection> findByExpeditionIdAndSectionTypeAndDayNumber(
            Long expeditionId,
            ExpeditionTransportSection.SectionType sectionType,
            Integer dayNumber);
}
