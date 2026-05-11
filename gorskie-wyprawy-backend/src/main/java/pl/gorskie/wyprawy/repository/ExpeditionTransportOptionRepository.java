package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.gorskie.wyprawy.model.ExpeditionTransportOption;

public interface ExpeditionTransportOptionRepository extends JpaRepository<ExpeditionTransportOption, Long> {
}
