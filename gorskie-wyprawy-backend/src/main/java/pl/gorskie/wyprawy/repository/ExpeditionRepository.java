package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.Expedition;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpeditionRepository extends JpaRepository<Expedition, Long> {

    List<Expedition> findByOrganizerIdOrderByPlannedDateDesc(Long organizerId);

    @Query("""
        SELECT e FROM Expedition e
        JOIN e.members m
        WHERE m.user.id = :userId
          AND m.status = 'ACCEPTED'
        ORDER BY e.plannedDate DESC
        """)
    List<Expedition> findAcceptedByMemberId(@Param("userId") Long userId);

    @Query("""
        SELECT DISTINCT e FROM Expedition e
        LEFT JOIN e.members m
        WHERE e.organizer.id = :userId
           OR (m.user.id = :userId AND m.status = 'ACCEPTED')
        ORDER BY e.plannedDate DESC
        """)
    List<Expedition> findAllForUser(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(e) > 0 FROM Expedition e
        LEFT JOIN e.members m
        WHERE e.id = :expeditionId
          AND (e.organizer.id = :userId
               OR (m.user.id = :userId AND m.status = 'ACCEPTED'))
        """)
    boolean hasAccess(@Param("expeditionId") Long expeditionId, @Param("userId") Long userId);

    @Query("""
        SELECT e FROM Expedition e
        WHERE e.status = 'PLANNED'
        ORDER BY e.plannedDate ASC
        """)
    List<Expedition> findAllPublic();

    Optional<Expedition> findByInviteToken(String inviteToken);

    List<Expedition> findByStatusAndPlannedDateLessThanEqual(
            Expedition.ExpeditionStatus status, java.time.LocalDate date);
}
