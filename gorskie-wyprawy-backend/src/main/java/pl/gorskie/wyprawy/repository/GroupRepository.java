package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.Group;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("""
        SELECT DISTINCT g FROM Group g
        WHERE g.owner.id = :userId
           OR EXISTS (
               SELECT m FROM GroupMember m
               WHERE m.group = g AND m.user.id = :userId
                 AND m.status IN ('ACCEPTED', 'INVITED', 'PENDING')
           )
        ORDER BY g.createdAt DESC
        """)
    List<Group> findAllForUser(@Param("userId") Long userId);

    @Query("SELECT g FROM Group g ORDER BY g.createdAt DESC")
    List<Group> findAllPublic();

    @Query("""
        SELECT COUNT(g) > 0 FROM Group g
        WHERE (g.owner.id = :a OR EXISTS (
                SELECT m FROM GroupMember m WHERE m.group = g AND m.user.id = :a AND m.status = 'ACCEPTED'))
          AND (g.owner.id = :b OR EXISTS (
                SELECT m FROM GroupMember m WHERE m.group = g AND m.user.id = :b AND m.status = 'ACCEPTED'))
        """)
    boolean existsSharedGroup(@Param("a") Long userA, @Param("b") Long userB);
}
