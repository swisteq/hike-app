package pl.gorskie.wyprawy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.gorskie.wyprawy.model.Friendship;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.requester.id = :userId OR f.addressee.id = :userId)
          AND f.status = 'ACCEPTED'
        """)
    List<Friendship> findAcceptedFriendships(@Param("userId") Long userId);

    @Query("""
        SELECT f FROM Friendship f
        WHERE f.addressee.id = :userId AND f.status = 'PENDING'
        """)
    List<Friendship> findIncomingRequests(@Param("userId") Long userId);

    @Query("""
        SELECT f FROM Friendship f
        WHERE f.requester.id = :userId AND f.status = 'PENDING'
        """)
    List<Friendship> findOutgoingRequests(@Param("userId") Long userId);

    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.requester.id = :a AND f.addressee.id = :b)
           OR (f.requester.id = :b AND f.addressee.id = :a)
        """)
    Optional<Friendship> findBetween(@Param("a") Long userA, @Param("b") Long userB);

    @Query("""
        SELECT COUNT(f) FROM Friendship f
        WHERE f.addressee.id = :userId AND f.status = 'PENDING'
        """)
    long countIncomingRequests(@Param("userId") Long userId);
}
