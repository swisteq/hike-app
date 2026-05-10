package pl.gorskie.wyprawy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.gorskie.wyprawy.dto.GroupDto;
import pl.gorskie.wyprawy.model.Group;
import pl.gorskie.wyprawy.model.GroupMember;
import pl.gorskie.wyprawy.model.GroupMessage;
import pl.gorskie.wyprawy.security.CurrentUserResolver;
import pl.gorskie.wyprawy.service.ExpeditionService;
import pl.gorskie.wyprawy.service.GroupService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final CurrentUserResolver currentUser;

    @GetMapping("/public")
    public ResponseEntity<List<GroupDto.GroupResponse>> getPublic() {
        Long userId = null;
        try { userId = currentUser.getCurrentUserId(); } catch (Exception ignored) {}
        final Long uid = userId;
        return ResponseEntity.ok(groupService.findAllPublic().stream()
                .map(g -> GroupDto.GroupResponse.from(g, uid != null
                        ? groupService.resolveViewerRole(g, uid) : "VISITOR"))
                .toList());
    }

    @GetMapping
    public ResponseEntity<List<GroupDto.GroupResponse>> getMy() {
        Long userId = currentUser.getCurrentUserId();
        return ResponseEntity.ok(groupService.findAllForUser(userId).stream()
                .map(g -> GroupDto.GroupResponse.from(g, groupService.resolveViewerRole(g, userId)))
                .toList());
    }

    @PostMapping
    public ResponseEntity<GroupDto.GroupResponse> create(@Valid @RequestBody GroupDto.CreateRequest req) {
        Long userId = currentUser.getCurrentUserId();
        Group group = groupService.create(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GroupDto.GroupResponse.from(group, "OWNER"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDto.GroupResponse> get(@PathVariable Long id) {
        Long userId = currentUser.getCurrentUserId();
        Group group = groupService.findById(id);
        String role = groupService.resolveViewerRole(group, userId);
        return ResponseEntity.ok(GroupDto.GroupResponse.from(group, role));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GroupDto.GroupResponse> update(
            @PathVariable Long id, @RequestBody GroupDto.UpdateRequest req) {
        Long userId = currentUser.getCurrentUserId();
        Group group = groupService.update(id, req, userId);
        return ResponseEntity.ok(GroupDto.GroupResponse.from(group, "OWNER"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        groupService.delete(id, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<GroupDto.MemberResponse> join(@PathVariable Long id) {
        GroupMember member = groupService.join(id, currentUser.getCurrentUserId());
        return ResponseEntity.ok(GroupDto.MemberResponse.from(member));
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<GroupDto.MemberResponse> invite(
            @PathVariable Long id, @Valid @RequestBody GroupDto.InviteRequest req) {
        GroupMember member = groupService.invite(id, req.getUsername(), currentUser.getCurrentUserId());
        return ResponseEntity.ok(GroupDto.MemberResponse.from(member));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<GroupDto.MemberResponse> respond(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        GroupMember member = groupService.respondToInvite(id, currentUser.getCurrentUserId(), accept);
        return ResponseEntity.ok(GroupDto.MemberResponse.from(member));
    }

    @PostMapping("/{id}/members/{userId}/approve")
    public ResponseEntity<GroupDto.MemberResponse> approve(
            @PathVariable Long id, @PathVariable Long userId) {
        GroupMember member = groupService.approveMember(id, userId, currentUser.getCurrentUserId());
        return ResponseEntity.ok(GroupDto.MemberResponse.from(member));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        groupService.removeMember(id, userId, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id) {
        Long userId = currentUser.getCurrentUserId();
        groupService.removeMember(id, userId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<GroupDto.MessageResponse>> getMessages(@PathVariable Long id) {
        List<GroupMessage> msgs = groupService.getMessages(id, currentUser.getCurrentUserId());
        return ResponseEntity.ok(msgs.stream().map(GroupDto.MessageResponse::from).toList());
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<GroupDto.MessageResponse> sendMessage(
            @PathVariable Long id, @Valid @RequestBody GroupDto.MessageRequest req) {
        GroupMessage msg = groupService.sendMessage(id, req.getContent(), currentUser.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupDto.MessageResponse.from(msg));
    }

    @ExceptionHandler(ExpeditionService.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(ExpeditionService.AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", 403, "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "message", e.getMessage()));
    }
}
