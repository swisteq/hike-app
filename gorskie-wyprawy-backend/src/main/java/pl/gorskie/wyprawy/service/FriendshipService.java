package pl.gorskie.wyprawy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gorskie.wyprawy.dto.FriendshipDto;
import pl.gorskie.wyprawy.model.Friendship;
import pl.gorskie.wyprawy.model.Notification.NotificationType;
import pl.gorskie.wyprawy.model.User;
import pl.gorskie.wyprawy.repository.FriendshipRepository;
import pl.gorskie.wyprawy.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<FriendshipDto.UserSearchResult> searchUsers(String query, Long currentUserId) {
        if (query == null || query.isBlank() || query.length() < 2) return List.of();

        List<User> users = userRepository.searchByName(query.trim(), currentUserId);
        return users.stream().map(u -> {
            Optional<Friendship> f = friendshipRepository.findBetween(currentUserId, u.getId());
            return FriendshipDto.UserSearchResult.from(u, f.orElse(null), currentUserId);
        }).toList();
    }

    @Transactional
    public Friendship sendRequest(Long targetUserId, Long requesterId) {
        if (requesterId.equals(targetUserId)) {
            throw new IllegalArgumentException("Nie można wysłać zaproszenia do samego siebie");
        }
        User requester = findUser(requesterId);
        User addressee = findUser(targetUserId);

        friendshipRepository.findBetween(requesterId, targetUserId).ifPresent(f -> {
            throw new IllegalArgumentException(switch (f.getStatus()) {
                case PENDING -> "Zaproszenie już zostało wysłane";
                case ACCEPTED -> "Jesteście już znajomymi";
                case DECLINED -> "Zaproszenie zostało odrzucone";
            });
        });

        Friendship friendship = Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(Friendship.FriendshipStatus.PENDING)
                .build();
        Friendship saved = friendshipRepository.save(friendship);
        notificationService.notify(targetUserId, NotificationType.FRIEND_REQUEST_RECEIVED,
                requester.getName() + " wysłał(a) Ci zaproszenie do znajomych", "/friends");
        return saved;
    }

    @Transactional
    public Friendship respond(Long friendshipId, Long addresseeId, boolean accept) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Zaproszenie nie istnieje"));

        if (!friendship.getAddressee().getId().equals(addresseeId)) {
            throw new ExpeditionService.AccessDeniedException("Brak uprawnień");
        }
        if (friendship.getStatus() != Friendship.FriendshipStatus.PENDING) {
            throw new IllegalArgumentException("Zaproszenie zostało już rozpatrzone");
        }

        friendship.setStatus(accept
                ? Friendship.FriendshipStatus.ACCEPTED
                : Friendship.FriendshipStatus.DECLINED);
        Friendship saved = friendshipRepository.save(friendship);
        String responderName = friendship.getAddressee().getName();
        NotificationType type = accept ? NotificationType.FRIEND_REQUEST_ACCEPTED : NotificationType.FRIEND_REQUEST_DECLINED;
        String msg = accept
                ? responderName + " zaakceptował(a) Twoje zaproszenie do znajomych"
                : responderName + " odrzucił(a) Twoje zaproszenie do znajomych";
        notificationService.notify(friendship.getRequester().getId(), type, msg, "/friends");
        return saved;
    }

    @Transactional
    public void removeFriend(Long otherUserId, Long currentUserId) {
        Friendship friendship = friendshipRepository.findBetween(currentUserId, otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("Nie jesteście znajomymi"));
        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendshipDto.FriendResponse> getFriends(Long userId) {
        return friendshipRepository.findAcceptedFriendships(userId).stream()
                .map(f -> FriendshipDto.FriendResponse.from(f, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipDto.RequestResponse> getIncomingRequests(Long userId) {
        return friendshipRepository.findIncomingRequests(userId).stream()
                .map(FriendshipDto.RequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countIncomingRequests(Long userId) {
        return friendshipRepository.countIncomingRequests(userId);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika id=" + id));
    }
}
