package pl.gorskie.wyprawy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.gorskie.wyprawy.dto.ExpeditionDto;
import pl.gorskie.wyprawy.model.Expedition;
import pl.gorskie.wyprawy.model.ExpeditionAuditLog;
import pl.gorskie.wyprawy.model.ExpeditionComment;
import pl.gorskie.wyprawy.model.ExpeditionDay;
import pl.gorskie.wyprawy.model.ExpeditionMember;
import pl.gorskie.wyprawy.security.CurrentUserResolver;
import pl.gorskie.wyprawy.service.ExpeditionService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API dla wypraw górskich.
 *
 *   POST   /api/expeditions                          — utwórz wyprawę
 *   GET    /api/expeditions                          — moje wyprawy
 *   GET    /api/expeditions/{id}                     — szczegóły
 *   PATCH  /api/expeditions/{id}                     — edytuj
 *   DELETE /api/expeditions/{id}                     — usuń
 *
 *   POST   /api/expeditions/{id}/invite              — zaproś użytkownika
 *   POST   /api/expeditions/{id}/respond             — odpowiedz na zaproszenie
 *
 *   POST   /api/expeditions/{id}/comments            — dodaj komentarz
 */
@RestController
@RequestMapping("/api/expeditions")
@RequiredArgsConstructor
public class ExpeditionController {

    private final ExpeditionService expeditionService;
    private final CurrentUserResolver currentUser;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> create(
            @Valid @RequestBody ExpeditionDto.CreateRequest request) {
        Expedition expedition = expeditionService.create(request, currentUser.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExpeditionDto.ExpeditionResponse.from(expedition));
    }

    @PostMapping("/multi-day")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> createMultiDay(
            @Valid @RequestBody ExpeditionDto.MultiDayCreateRequest request) {
        Expedition expedition = expeditionService.createMultiDay(request, currentUser.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExpeditionDto.ExpeditionResponse.from(expedition));
    }

    @PostMapping(value = "/{id}/days/{dayNumber}/trail", consumes = "multipart/form-data")
    public ResponseEntity<ExpeditionDto.DayResponse> addDayTrail(
            @PathVariable Long id,
            @PathVariable int dayNumber,
            @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = currentUser.getCurrentUserId();
        ExpeditionDay day = expeditionService.addDayTrail(id, dayNumber, file, userId);
        return ResponseEntity.ok(ExpeditionDto.DayResponse.from(day));
    }

    @PostMapping("/{id}/days/{dayNumber}/accommodation")
    public ResponseEntity<ExpeditionDto.DayResponse> setAccommodation(
            @PathVariable Long id,
            @PathVariable int dayNumber,
            @RequestBody ExpeditionDto.AccommodationRequest request) {
        return ResponseEntity.ok(expeditionService.setAccommodation(id, dayNumber, request, currentUser.getCurrentUserId()));
    }

    @DeleteMapping("/{id}/days/{dayNumber}/accommodation")
    public ResponseEntity<ExpeditionDto.DayResponse> removeAccommodation(
            @PathVariable Long id,
            @PathVariable int dayNumber) {
        return ResponseEntity.ok(expeditionService.removeAccommodation(id, dayNumber, currentUser.getCurrentUserId()));
    }

    @PostMapping("/{id}/transport/{type}/options")
    public ResponseEntity<ExpeditionDto.TransportSectionResponse> addTransportOption(
            @PathVariable Long id,
            @PathVariable String type,
            @RequestBody ExpeditionDto.TransportOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expeditionService.addTransportOption(id, type, request, currentUser.getCurrentUserId()));
    }

    @PutMapping("/{id}/transport/options/{optionId}")
    public ResponseEntity<ExpeditionDto.TransportSectionResponse> updateTransportOption(
            @PathVariable Long id,
            @PathVariable Long optionId,
            @RequestBody ExpeditionDto.TransportOptionRequest request) {
        return ResponseEntity.ok(expeditionService.updateTransportOption(id, optionId, request, currentUser.getCurrentUserId()));
    }

    @DeleteMapping("/{id}/transport/options/{optionId}")
    public ResponseEntity<Void> deleteTransportOption(
            @PathVariable Long id,
            @PathVariable Long optionId) {
        expeditionService.deleteTransportOption(id, optionId, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/days/{dayNumber}/track")
    public ResponseEntity<List<double[]>> getDayTrack(
            @PathVariable Long id,
            @PathVariable int dayNumber) throws IOException {
        List<double[]> points = expeditionService.getDayTrackPoints(id, dayNumber);
        return ResponseEntity.ok(points);
    }

    @PostMapping(value = "/from-gpx", consumes = "multipart/form-data")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> createFromGpx(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("plannedDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedDate,
            @RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "joinMode", required = false) String joinMode,
            @RequestParam(value = "visibility", required = false) String visibility) throws IOException {
        Expedition.JoinMode mode = Optional.ofNullable(joinMode)
                .map(s -> Expedition.JoinMode.valueOf(s.toUpperCase()))
                .orElse(Expedition.JoinMode.AUTO);
        Expedition.Visibility vis = Optional.ofNullable(visibility)
                .map(s -> Expedition.Visibility.valueOf(s.toUpperCase()))
                .orElse(Expedition.Visibility.PUBLIC);
        Expedition expedition = expeditionService.createFromGpx(
                file, name, plannedDate, startTime, description, mode, vis, currentUser.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExpeditionDto.ExpeditionResponse.from(expedition));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> cancel(@PathVariable Long id) {
        Expedition expedition = expeditionService.cancel(id, currentUser.getCurrentUserId());
        Long userId = currentUser.getCurrentUserId();
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition,
                expeditionService.resolveViewerRole(expedition, userId)));
    }

    @PostMapping("/{id}/mark-completed")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> markCompleted(@PathVariable Long id) {
        Long userId = currentUser.getCurrentUserId();
        Expedition expedition = expeditionService.markCompleted(id, userId);
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition,
                expeditionService.resolveViewerRole(expedition, userId)));
    }

    @PostMapping("/{id}/mark-unrealized")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> markUnrealized(@PathVariable Long id) {
        Long userId = currentUser.getCurrentUserId();
        Expedition expedition = expeditionService.markUnrealized(id, userId);
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition,
                expeditionService.resolveViewerRole(expedition, userId)));
    }

    @GetMapping
    public ResponseEntity<List<ExpeditionDto.ExpeditionResponse>> getMyExpeditions() {
        List<Expedition> expeditions = expeditionService.findAllForUser(currentUser.getCurrentUserId());
        return ResponseEntity.ok(expeditions.stream()
                .map(ExpeditionDto.ExpeditionResponse::from)
                .toList());
    }

    @GetMapping("/public")
    public ResponseEntity<List<ExpeditionDto.ExpeditionResponse>> getPublicExpeditions() {
        Long userId = null;
        try { userId = currentUser.getCurrentUserId(); } catch (Exception ignored) {}
        List<Expedition> expeditions = expeditionService.findAllPublic(userId);
        return ResponseEntity.ok(expeditions.stream()
                .map(ExpeditionDto.ExpeditionResponse::from)
                .toList());
    }

    @GetMapping("/{id}/track")
    public ResponseEntity<List<double[]>> getTrack(@PathVariable Long id) throws IOException {
        List<double[]> points = expeditionService.getTrackPoints(id);
        return ResponseEntity.ok(points);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        expeditionService.removeMember(id, userId, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id) {
        expeditionService.removeMember(id, currentUser.getCurrentUserId(), currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ExpeditionDto.MemberResponse> join(@PathVariable Long id) {
        ExpeditionMember member = expeditionService.join(id, currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.MemberResponse.from(member));
    }

    @PostMapping("/{id}/members/{userId}/approve")
    public ResponseEntity<ExpeditionDto.MemberResponse> approveMember(
            @PathVariable Long id, @PathVariable Long userId) {
        ExpeditionMember member = expeditionService.approveMember(id, userId, currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.MemberResponse.from(member));
    }

    @PostMapping(value = "/{id}/change-trail", consumes = "multipart/form-data")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> changeTrail(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = currentUser.getCurrentUserId();
        Expedition expedition = expeditionService.changeTrail(id, file, userId);
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition,
                expeditionService.resolveViewerRole(expedition, userId)));
    }

    @GetMapping("/{id}/audit-logs")
    public ResponseEntity<List<ExpeditionDto.AuditLogResponse>> getAuditLogs(@PathVariable Long id) {
        Long userId = currentUser.getCurrentUserId();
        List<ExpeditionAuditLog> logs = expeditionService.getAuditLogs(id, userId);
        return ResponseEntity.ok(logs.stream().map(ExpeditionDto.AuditLogResponse::from).toList());
    }

    @PutMapping("/{id}/equipment")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> updateEquipment(
            @PathVariable Long id,
            @RequestBody java.util.List<ExpeditionDto.EquipmentItemRequest> items) {
        Long userId = currentUser.getCurrentUserId();
        Expedition expedition = expeditionService.updateEquipment(id, items, userId);
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition,
                expeditionService.resolveViewerRole(expedition, userId)));
    }

    @PatchMapping("/{id}/members/{userId}/role")
    public ResponseEntity<ExpeditionDto.MemberResponse> updateMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestBody ExpeditionDto.RoleRequest request) {
        ExpeditionMember.MemberRole role = ExpeditionMember.MemberRole.valueOf(request.getRole());
        ExpeditionMember member = expeditionService.updateMemberRole(id, userId, role, currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.MemberResponse.from(member));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> getExpedition(@PathVariable Long id) {
        Long userId = currentUser.getCurrentUserId();
        Expedition expedition = expeditionService.findById(id, userId);
        String viewerRole = expeditionService.resolveViewerRole(expedition, userId);
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition, viewerRole));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ExpeditionDto.ExpeditionResponse> update(
            @PathVariable Long id,
            @RequestBody ExpeditionDto.UpdateRequest request) {
        Expedition expedition = expeditionService.update(id, request, currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.ExpeditionResponse.from(expedition));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expeditionService.delete(id, currentUser.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Zaproszenia
    // -------------------------------------------------------------------------

    /**
     * POST /api/expeditions/{id}/invite
     * Body: { "userId": 2 }
     * Tylko organizator może zapraszać.
     */
    @PostMapping("/{id}/invite")
    public ResponseEntity<ExpeditionDto.MemberResponse> invite(
            @PathVariable Long id,
            @Valid @RequestBody ExpeditionDto.InviteRequest request) {
        ExpeditionMember member = expeditionService.invite(id, request.getUsername(), currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.MemberResponse.from(member));
    }

    @PostMapping("/{id}/generate-link")
    public ResponseEntity<Map<String, String>> generateInviteLink(@PathVariable Long id) {
        String token = expeditionService.generateInviteLink(id, currentUser.getCurrentUserId());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/join-by-link/{token}")
    public ResponseEntity<ExpeditionDto.MemberResponse> joinByLink(@PathVariable String token) {
        ExpeditionMember member = expeditionService.joinByInviteLink(token, currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.MemberResponse.from(member));
    }

    /**
     * POST /api/expeditions/{id}/respond
     * Body: { "accept": true }
     * Zaproszony użytkownik akceptuje lub odrzuca zaproszenie.
     */
    @PostMapping("/{id}/respond")
    public ResponseEntity<ExpeditionDto.MemberResponse> respond(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        ExpeditionMember member = expeditionService.respondToInvite(id, currentUser.getCurrentUserId(), accept);
        return ResponseEntity.ok(ExpeditionDto.MemberResponse.from(member));
    }

    // -------------------------------------------------------------------------
    // Komentarze
    // -------------------------------------------------------------------------

    /**
     * POST /api/expeditions/{id}/comments
     * Body: { "content": "Pamiętajmy o kijkach!" }
     * Tylko członkowie wyprawy mogą komentować.
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<ExpeditionDto.CommentResponse> addComment(
            @PathVariable Long id,
            @Valid @RequestBody ExpeditionDto.CommentRequest request) {
        ExpeditionComment comment = expeditionService.addComment(
                id, request.getContent(), request.getParentId(), currentUser.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExpeditionDto.CommentResponse.from(comment));
    }

    @PostMapping("/{id}/comments/{commentId}/pin")
    public ResponseEntity<ExpeditionDto.CommentResponse> pinComment(
            @PathVariable Long id,
            @PathVariable Long commentId) {
        ExpeditionComment comment = expeditionService.pinComment(id, commentId, currentUser.getCurrentUserId());
        return ResponseEntity.ok(ExpeditionDto.CommentResponse.from(comment));
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/transport/options/{optionId}/approve")
    public ResponseEntity<ExpeditionDto.TransportSectionResponse> approveTransportOption(
            @PathVariable Long id, @PathVariable Long optionId) {
        return ResponseEntity.ok(expeditionService.approveTransportOption(id, optionId, currentUser.getCurrentUserId()));
    }

    @GetMapping("/resolve-place")
    public ResponseEntity<Map<String, String>> resolvePlace(@RequestParam String url) {
        String name = expeditionService.resolvePlaceName(url);
        return ResponseEntity.ok(Map.of("name", name != null ? name : ""));
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
