package pl.gorskie.wyprawy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.gorskie.wyprawy.dto.FriendshipDto;
import pl.gorskie.wyprawy.model.Friendship;
import pl.gorskie.wyprawy.security.CurrentUserResolver;
import pl.gorskie.wyprawy.service.ExpeditionService;
import pl.gorskie.wyprawy.service.FriendshipService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public ResponseEntity<List<FriendshipDto.FriendResponse>> getFriends() {
        return ResponseEntity.ok(friendshipService.getFriends(currentUser.getCurrentUserId()));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendshipDto.RequestResponse>> getRequests() {
        return ResponseEntity.ok(friendshipService.getIncomingRequests(currentUser.getCurrentUserId()));
    }

    @GetMapping("/requests/count")
    public ResponseEntity<FriendshipDto.PendingCountResponse> countRequests() {
        long count = friendshipService.countIncomingRequests(currentUser.getCurrentUserId());
        return ResponseEntity.ok(new FriendshipDto.PendingCountResponse(count));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FriendshipDto.UserSearchResult>> searchUsers(
            @RequestParam String q) {
        return ResponseEntity.ok(friendshipService.searchUsers(q, currentUser.getCurrentUserId()));
    }

    @PostMapping("/request/{userId}")
    public ResponseEntity<FriendshipDto.UserSearchResult> sendRequest(@PathVariable Long userId) {
        Friendship f = friendshipService.sendRequest(userId, currentUser.getCurrentUserId());
        FriendshipDto.UserSearchResult result = FriendshipDto.UserSearchResult.builder()
                .id(f.getAddressee().getId())
                .name(f.getAddressee().getName())
                .friendshipStatus("PENDING_SENT")
                .friendshipId(f.getId())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{friendshipId}/respond")
    public ResponseEntity<Map<String, String>> respond(
            @PathVariable Long friendshipId,
            @RequestBody Map<String, Boolean> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        friendshipService.respond(friendshipId, currentUser.getCurrentUserId(), accept);
        return ResponseEntity.ok(Map.of("status", accept ? "ACCEPTED" : "DECLINED"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeFriend(@PathVariable Long userId) {
        friendshipService.removeFriend(userId, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
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
