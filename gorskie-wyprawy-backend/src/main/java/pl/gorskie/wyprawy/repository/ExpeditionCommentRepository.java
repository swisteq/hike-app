package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.ExpeditionComment;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpeditionCommentRepository extends JpaRepository<ExpeditionComment, Long> {

    List<ExpeditionComment> findByExpeditionIdAndParentIsNullOrderByPinnedDescCreatedAtAsc(Long expeditionId);

    Optional<ExpeditionComment> findByIdAndExpeditionId(Long id, Long expeditionId);
}
