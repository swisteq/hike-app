package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionMember;

import java.util.Optional;

@Repository
public interface ExpeditionMemberRepository extends JpaRepository<ExpeditionMember, Long> {
    Optional<ExpeditionMember> findByExpeditionIdAndUserId(Long expeditionId, Long userId);
}
