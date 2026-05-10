package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionEquipment;

@Repository
public interface ExpeditionEquipmentRepository extends JpaRepository<ExpeditionEquipment, Long> {
}
