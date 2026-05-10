package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionAuditLog;

import java.util.List;

@Repository
public interface ExpeditionAuditLogRepository extends JpaRepository<ExpeditionAuditLog, Long> {
    List<ExpeditionAuditLog> findByExpeditionIdOrderByCreatedAtDesc(Long expeditionId);
}
