package pl.gorskie.wyprawy.dto;

import lombok.*;
import pl.gorskie.wyprawy.model.Friendship;
import pl.gorskie.wyprawy.model.User;

import java.time.LocalDateTime;

public class FriendshipDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSearchResult {
        private Long id;
        private String name;
        // null | PENDING_SENT | PENDING_RECEIVED | ACCEPTED
        private String friendshipStatus;
        private Long friendshipId;

        public static UserSearchResult from(User u, Friendship f, Long currentUserId) {
            String status = null;
            Long fid = null;
            if (f != null) {
                fid = f.getId();
                status = switch (f.getStatus()) {
                    case ACCEPTED -> "ACCEPTED";
                    case PENDING -> f.getRequester().getId().equals(currentUserId)
                            ? "PENDING_SENT" : "PENDING_RECEIVED";
                    case DECLINED -> null;
                };
            }
            return UserSearchResult.builder()
                    .id(u.getId())
                    .name(u.getName())
                    .friendshipStatus(status)
                    .friendshipId(fid)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendResponse {
        private Long friendshipId;
        private Long userId;
        private String name;
        private LocalDateTime since;

        public static FriendResponse from(Friendship f, Long currentUserId) {
            User friend = f.getRequester().getId().equals(currentUserId)
                    ? f.getAddressee() : f.getRequester();
            return FriendResponse.builder()
                    .friendshipId(f.getId())
                    .userId(friend.getId())
                    .name(friend.getName())
                    .since(f.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestResponse {
        private Long friendshipId;
        private Long fromUserId;
        private String fromUserName;
        private LocalDateTime sentAt;

        public static RequestResponse from(Friendship f) {
            return RequestResponse.builder()
                    .friendshipId(f.getId())
                    .fromUserId(f.getRequester().getId())
                    .fromUserName(f.getRequester().getName())
                    .sentAt(f.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingCountResponse {
        private long count;
    }
}
