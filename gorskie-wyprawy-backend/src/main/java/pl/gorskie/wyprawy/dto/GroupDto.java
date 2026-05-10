package pl.gorskie.wyprawy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import pl.gorskie.wyprawy.model.Group;
import pl.gorskie.wyprawy.model.GroupMember;
import pl.gorskie.wyprawy.model.GroupMessage;

import java.time.LocalDateTime;
import java.util.List;

public class GroupDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Nazwa grupy jest wymagana")
        private String name;
        private String description;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupResponse {
        private Long id;
        private String name;
        private String description;
        private AuthDto.UserResponse owner;
        private int memberCount;
        private LocalDateTime createdAt;
        private String viewerRole; // OWNER | MEMBER | PENDING | INVITED | VISITOR
        private List<MemberResponse> members;
        private List<MessageResponse> messages;

        public static GroupResponse from(Group g, String viewerRole) {
            boolean fullAccess = "OWNER".equals(viewerRole) || "MEMBER".equals(viewerRole);
            return GroupResponse.builder()
                    .id(g.getId())
                    .name(g.getName())
                    .description(g.getDescription())
                    .owner(AuthDto.UserResponse.from(g.getOwner()))
                    .memberCount(g.getMembers().size())
                    .createdAt(g.getCreatedAt())
                    .viewerRole(viewerRole)
                    .members(fullAccess
                            ? g.getMembers().stream().map(MemberResponse::from).toList()
                            : null)
                    .messages(fullAccess
                            ? g.getMessages().stream().map(MessageResponse::from).toList()
                            : null)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private Long id;
        private AuthDto.UserResponse user;
        private GroupMember.MemberStatus status;
        private LocalDateTime createdAt;

        public static MemberResponse from(GroupMember m) {
            return MemberResponse.builder()
                    .id(m.getId())
                    .user(AuthDto.UserResponse.from(m.getUser()))
                    .status(m.getStatus())
                    .createdAt(m.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private Long id;
        private AuthDto.UserResponse author;
        private String content;
        private LocalDateTime createdAt;

        public static MessageResponse from(GroupMessage m) {
            return MessageResponse.builder()
                    .id(m.getId())
                    .author(AuthDto.UserResponse.from(m.getAuthor()))
                    .content(m.getContent())
                    .createdAt(m.getCreatedAt())
                    .build();
        }
    }

    @Data
    public static class MessageRequest {
        @NotBlank(message = "Treść wiadomości jest wymagana")
        private String content;
    }

    @Data
    public static class InviteRequest {
        @NotBlank(message = "Nazwa użytkownika jest wymagana")
        private String username;
    }
}
