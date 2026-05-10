package pl.gorskie.wyprawy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.gorskie.wyprawy.dto.ExpeditionDto;
import pl.gorskie.wyprawy.dto.GpxParseResult;
import pl.gorskie.wyprawy.model.*;
import pl.gorskie.wyprawy.model.Notification.NotificationType;
import pl.gorskie.wyprawy.repository.ExpeditionRepository;
import pl.gorskie.wyprawy.repository.ExpeditionMemberRepository;
import pl.gorskie.wyprawy.repository.ExpeditionCommentRepository;
import pl.gorskie.wyprawy.repository.FriendshipRepository;
import pl.gorskie.wyprawy.repository.GroupRepository;
import pl.gorskie.wyprawy.repository.UserRepository;
import pl.gorskie.wyprawy.service.gpx.GpxParserService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpeditionService {

    private final ExpeditionRepository expeditionRepository;
    private final ExpeditionMemberRepository memberRepository;
    private final ExpeditionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;
    private final TrailService trailService;
    private final GpxParserService gpxParserService;
    private final LocationRecognitionService locationRecognitionService;
    private final pl.gorskie.wyprawy.repository.ExpeditionEquipmentRepository equipmentRepository;
    private final pl.gorskie.wyprawy.repository.ExpeditionAuditLogRepository auditLogRepository;
    private final pl.gorskie.wyprawy.repository.ExpeditionDayRepository dayRepository;

    @Transactional
    public Expedition create(ExpeditionDto.CreateRequest request, Long organizerId) {
        User organizer = findUser(organizerId);
        Trail trail = trailService.findById(request.getTrailId());

        Expedition expedition = Expedition.builder()
                .name(request.getName())
                .description(request.getDescription())
                .plannedDate(request.getPlannedDate())
                .startTime(request.getStartTime())
                .trail(trail)
                .organizer(organizer)
                .joinMode(request.getJoinMode() != null ? request.getJoinMode() : Expedition.JoinMode.AUTO)
                .visibility(request.getVisibility() != null ? request.getVisibility() : Expedition.Visibility.PUBLIC)
                .build();

        return expeditionRepository.save(expedition);
    }

    @Transactional
    public Expedition createFromGpx(MultipartFile file, String name, LocalDate plannedDate,
                                    java.time.LocalTime startTime, String description,
                                    Expedition.JoinMode joinMode, Expedition.Visibility visibility,
                                    Long organizerId) throws IOException {
        trailService.validateGpxFile(file);
        User organizer = findUser(organizerId);

        String fallbackName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("(?i)\\.gpx$", "") : "Nieznana trasa";
        GpxParseResult parsed = gpxParserService.parse(file.getInputStream(), fallbackName);
        String gpxPath = trailService.saveGpxFile(file);

        Expedition expedition = Expedition.builder()
                .name(name)
                .description(description)
                .plannedDate(plannedDate)
                .organizer(organizer)
                .startTime(startTime)
                .joinMode(joinMode != null ? joinMode : Expedition.JoinMode.AUTO)
                .visibility(visibility != null ? visibility : Expedition.Visibility.PUBLIC)
                .trailName(parsed.getName())
                .distanceKm(parsed.getDistanceKm())
                .elevationGainM(parsed.getElevationGainM())
                .elevationLossM(parsed.getElevationLossM())
                .maxElevationM(parsed.getMaxElevationM())
                .minElevationM(parsed.getMinElevationM())
                .durationMinutes(parsed.getDurationMinutes())
                .gpxFilePath(gpxPath)
                .startLat(parsed.getStartLat())
                .startLon(parsed.getStartLon())
                .bboxMinLat(parsed.getBboxMinLat())
                .bboxMaxLat(parsed.getBboxMaxLat())
                .bboxMinLon(parsed.getBboxMinLon())
                .bboxMaxLon(parsed.getBboxMaxLon())
                .startLocationName(parsed.getStartWaypointName())
                .endLocationName(parsed.getEndWaypointName())
                .build();

        Expedition saved = expeditionRepository.save(expedition);

        List<double[]> trackPoints = List.of();
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(gpxPath))) {
            trackPoints = gpxParserService.parseTrackPoints(is);
        } catch (Exception e) {
            log.warn("Nie udało się odczytać punktów trasy dla rozpoznawania lokalizacji: {}", e.getMessage());
        }
        locationRecognitionService.recognizeAndSave(saved, trackPoints);

        return saved;
    }

    @Transactional
    public Expedition createMultiDay(ExpeditionDto.MultiDayCreateRequest request, Long organizerId) {
        if (!request.getEndDate().isAfter(request.getPlannedDate())) {
            throw new IllegalArgumentException("Data końcowa musi być późniejsza niż data startowa");
        }
        User organizer = findUser(organizerId);

        Expedition expedition = Expedition.builder()
                .name(request.getName())
                .description(request.getDescription())
                .plannedDate(request.getPlannedDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .organizer(organizer)
                .joinMode(request.getJoinMode() != null ? request.getJoinMode() : Expedition.JoinMode.AUTO)
                .visibility(request.getVisibility() != null ? request.getVisibility() : Expedition.Visibility.PUBLIC)
                .build();

        Expedition saved = expeditionRepository.save(expedition);

        LocalDate date = request.getPlannedDate();
        int dayNum = 1;
        while (!date.isAfter(request.getEndDate())) {
            dayRepository.save(ExpeditionDay.builder()
                    .expedition(saved)
                    .dayNumber(dayNum)
                    .dayDate(date)
                    .build());
            date = date.plusDays(1);
            dayNum++;
        }

        return expeditionRepository.findById(saved.getId()).orElse(saved);
    }

    @Transactional
    public ExpeditionDay addDayTrail(Long expeditionId, int dayNumber, MultipartFile file, Long userId) throws IOException {
        Expedition expedition = findAndCheckOrganizerOrNavigator(expeditionId, userId);
        trailService.validateGpxFile(file);

        ExpeditionDay day = dayRepository.findByExpeditionIdAndDayNumber(expeditionId, dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("Dzień " + dayNumber + " nie istnieje w tej wyprawie"));

        String fallbackName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("(?i)\\.gpx$", "") : "Trasa dnia " + dayNumber;
        GpxParseResult parsed = gpxParserService.parse(file.getInputStream(), fallbackName);
        String gpxPath = trailService.saveGpxFile(file);

        day.setTrailName(parsed.getName());
        day.setDistanceKm(parsed.getDistanceKm());
        day.setElevationGainM(parsed.getElevationGainM());
        day.setElevationLossM(parsed.getElevationLossM());
        day.setMaxElevationM(parsed.getMaxElevationM());
        day.setMinElevationM(parsed.getMinElevationM());
        day.setDurationMinutes(parsed.getDurationMinutes());
        day.setGpxFilePath(gpxPath);
        day.setStartLat(parsed.getStartLat());
        day.setStartLon(parsed.getStartLon());
        day.setBboxMinLat(parsed.getBboxMinLat());
        day.setBboxMaxLat(parsed.getBboxMaxLat());
        day.setBboxMinLon(parsed.getBboxMinLon());
        day.setBboxMaxLon(parsed.getBboxMaxLon());
        day.setStartLocationName(parsed.getStartWaypointName());
        day.setEndLocationName(parsed.getEndWaypointName());

        ExpeditionDay saved = dayRepository.save(day);

        // Rozszerzamy bounding box ekspedycji o obszar tego dnia (potrzebne do rozpoznawania lokalizacji)
        expedition.setBboxMinLat(expedition.getBboxMinLat() == null ? parsed.getBboxMinLat()
                : Math.min(expedition.getBboxMinLat(), parsed.getBboxMinLat()));
        expedition.setBboxMaxLat(expedition.getBboxMaxLat() == null ? parsed.getBboxMaxLat()
                : Math.max(expedition.getBboxMaxLat(), parsed.getBboxMaxLat()));
        expedition.setBboxMinLon(expedition.getBboxMinLon() == null ? parsed.getBboxMinLon()
                : Math.min(expedition.getBboxMinLon(), parsed.getBboxMinLon()));
        expedition.setBboxMaxLon(expedition.getBboxMaxLon() == null ? parsed.getBboxMaxLon()
                : Math.max(expedition.getBboxMaxLon(), parsed.getBboxMaxLon()));
        Expedition savedExp = expeditionRepository.save(expedition);

        // Rozpoznaj lokalizacje dla tego dnia
        List<double[]> trackPoints = java.util.List.of();
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(gpxPath))) {
            trackPoints = gpxParserService.parseTrackPoints(is);
        } catch (Exception e) {
            log.warn("Nie udało się odczytać punktów trasy dnia {}: {}", dayNumber, e.getMessage());
        }
        locationRecognitionService.recognizeAndSave(savedExp, trackPoints, dayNumber);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<double[]> getDayTrackPoints(Long expeditionId, int dayNumber) throws IOException {
        ExpeditionDay day = dayRepository.findByExpeditionIdAndDayNumber(expeditionId, dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("Dzień " + dayNumber + " nie istnieje"));

        if (day.getGpxFilePath() == null) return List.of();

        java.nio.file.Path path = java.nio.file.Paths.get(day.getGpxFilePath());
        if (!java.nio.file.Files.exists(path)) return List.of();

        try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
            return gpxParserService.parseTrackPoints(is);
        }
    }

    @Transactional
    public Expedition update(Long expeditionId, ExpeditionDto.UpdateRequest request, Long userId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, userId);

        if (request.getName() != null) expedition.setName(request.getName());
        if (request.getDescription() != null) expedition.setDescription(request.getDescription());
        if (request.getPlannedDate() != null) expedition.setPlannedDate(request.getPlannedDate());
        if (request.getStartTime() != null) expedition.setStartTime(request.getStartTime());
        if (request.getJoinMode() != null) expedition.setJoinMode(request.getJoinMode());
        if (request.getVisibility() != null) expedition.setVisibility(request.getVisibility());

        return expeditionRepository.save(expedition);
    }

    @Transactional
    public void delete(Long expeditionId, Long userId) {
        findAndCheckOrganizer(expeditionId, userId);
        expeditionRepository.deleteById(expeditionId);
    }

    @Transactional(readOnly = true)
    public Expedition findById(Long id, Long userId) {
        return expeditionRepository.findById(id)
                .orElseThrow(() -> new TrailNotFoundException(id));
    }

    public String resolveViewerRole(Expedition expedition, Long userId) {
        if (expedition.getOrganizer().getId().equals(userId)) return "ORGANIZER";
        return expedition.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .map(m -> switch (m.getStatus()) {
                    case ACCEPTED -> m.getMemberRole() != null ? m.getMemberRole().name() : "MEMBER";
                    case INVITED  -> "INVITED";
                    case PENDING  -> "PENDING";
                    case DECLINED -> "VISITOR";
                })
                .orElse("VISITOR");
    }

    @Transactional
    public Expedition updateEquipment(Long expeditionId, java.util.List<ExpeditionDto.EquipmentItemRequest> items, Long userId) {
        Expedition expedition = findAndCheckOrganizerOrNavigator(expeditionId, userId);

        // Przechwytujemy stary stan jako niezmienną mapę — PRZED jakimikolwiek mutacjami encji
        java.util.Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> oldSnapshot =
                expedition.getEquipment().stream().collect(java.util.stream.Collectors.toMap(
                        ExpeditionEquipment::getItem, ExpeditionEquipment::getLevel));

        java.util.Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> newMap =
                items.stream().collect(java.util.stream.Collectors.toMap(
                        req -> ExpeditionEquipment.EquipmentItem.valueOf(req.getItem()),
                        req -> ExpeditionEquipment.RequirementLevel.valueOf(req.getLevel())));

        // Usuń elementy, których nie ma w nowej liście
        expedition.getEquipment().removeIf(eq -> !newMap.containsKey(eq.getItem()));
        // Zaktualizuj poziom istniejących elementów
        expedition.getEquipment().forEach(eq -> eq.setLevel(newMap.get(eq.getItem())));
        // Dodaj nowe elementy (których jeszcze nie ma)
        java.util.Set<ExpeditionEquipment.EquipmentItem> existing =
                expedition.getEquipment().stream().map(ExpeditionEquipment::getItem)
                        .collect(java.util.stream.Collectors.toSet());
        newMap.forEach((item, level) -> {
            if (!existing.contains(item)) {
                expedition.getEquipment().add(ExpeditionEquipment.builder()
                        .expedition(expedition).item(item).level(level).build());
            }
        });

        Expedition saved = expeditionRepository.save(expedition);
        String diff = buildEquipmentDiff(oldSnapshot, newMap);
        if (diff != null) {
            logChange(saved, findUser(userId), ExpeditionAuditLog.ChangeType.EQUIPMENT_CHANGED, diff);
        }
        return saved;
    }

    @Transactional
    public Expedition changeTrail(Long expeditionId, org.springframework.web.multipart.MultipartFile file, Long userId) throws java.io.IOException {
        Expedition expedition = findAndCheckOrganizerOrNavigator(expeditionId, userId);
        trailService.validateGpxFile(file);

        String oldName = expedition.getTrailName() != null ? expedition.getTrailName()
                : (expedition.getTrail() != null ? expedition.getTrail().getName() : "nieznana");

        String fallbackName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("(?i)\\.gpx$", "") : "Nieznana trasa";
        GpxParseResult parsed = gpxParserService.parse(file.getInputStream(), fallbackName);
        String gpxPath = trailService.saveGpxFile(file);

        expedition.setTrail(null);
        expedition.setTrailName(parsed.getName());
        expedition.setDistanceKm(parsed.getDistanceKm());
        expedition.setElevationGainM(parsed.getElevationGainM());
        expedition.setElevationLossM(parsed.getElevationLossM());
        expedition.setMaxElevationM(parsed.getMaxElevationM());
        expedition.setMinElevationM(parsed.getMinElevationM());
        expedition.setDurationMinutes(parsed.getDurationMinutes());
        expedition.setGpxFilePath(gpxPath);
        expedition.setStartLat(parsed.getStartLat());
        expedition.setStartLon(parsed.getStartLon());
        expedition.setBboxMinLat(parsed.getBboxMinLat());
        expedition.setBboxMaxLat(parsed.getBboxMaxLat());
        expedition.setBboxMinLon(parsed.getBboxMinLon());
        expedition.setBboxMaxLon(parsed.getBboxMaxLon());
        expedition.setStartLocationName(parsed.getStartWaypointName());
        expedition.setEndLocationName(parsed.getEndWaypointName());
        expedition.setHighestPeakName(null);
        expedition.setHighestPeakElevationM(null);
        expedition.getLocations().clear();

        Expedition saved = expeditionRepository.save(expedition);

        java.util.List<double[]> trackPoints = java.util.List.of();
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(gpxPath))) {
            trackPoints = gpxParserService.parseTrackPoints(is);
        } catch (Exception e) {
            log.warn("Nie udało się odczytać punktów trasy: {}", e.getMessage());
        }
        locationRecognitionService.recognizeAndSave(saved, trackPoints);

        String newName = parsed.getName();
        logChange(saved, findUser(userId), ExpeditionAuditLog.ChangeType.TRAIL_CHANGED,
                "Zmieniono trasę z \"" + oldName + "\" na \"" + newName + "\"");

        return expeditionRepository.findById(expeditionId).orElse(saved);
    }

    @Transactional(readOnly = true)
    public java.util.List<ExpeditionAuditLog> getAuditLogs(Long expeditionId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));
        String role = resolveViewerRole(expedition, userId);
        if (!java.util.List.of("ORGANIZER", "MEMBER", "NAWIGATOR", "LOGISTYK").contains(role)) {
            throw new AccessDeniedException("Brak dostępu do logów zmian");
        }
        return auditLogRepository.findByExpeditionIdOrderByCreatedAtDesc(expeditionId);
    }

    @Transactional
    public ExpeditionMember updateMemberRole(Long expeditionId, Long targetUserId,
                                             ExpeditionMember.MemberRole role, Long organizerId) {
        findAndCheckOrganizer(expeditionId, organizerId);
        ExpeditionMember member = memberRepository.findByExpeditionIdAndUserId(expeditionId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie jest uczestnikiem tej wyprawy"));
        if (member.getStatus() != ExpeditionMember.MemberStatus.ACCEPTED) {
            throw new IllegalArgumentException("Można zmieniać rolę tylko zaakceptowanym uczestnikom");
        }
        member.setMemberRole(role);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public List<Expedition> findAllForUser(Long userId) {
        return expeditionRepository.findAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<Expedition> findAllPublic(Long viewerId) {
        return expeditionRepository.findAllPublic().stream()
                .filter(e -> isVisibleTo(e, viewerId))
                .toList();
    }

    private boolean isVisibleTo(Expedition e, Long viewerId) {
        if (viewerId != null && e.getOrganizer().getId().equals(viewerId)) return true;
        if (viewerId != null) {
            boolean isMember = e.getMembers().stream()
                    .anyMatch(m -> m.getUser().getId().equals(viewerId)
                            && m.getStatus() == ExpeditionMember.MemberStatus.ACCEPTED);
            if (isMember) return true;
        }
        return switch (e.getVisibility()) {
            case PUBLIC -> true;
            case FRIENDS_ONLY -> viewerId != null && friendshipRepository
                    .findBetween(e.getOrganizer().getId(), viewerId)
                    .map(f -> f.getStatus() == pl.gorskie.wyprawy.model.Friendship.FriendshipStatus.ACCEPTED)
                    .orElse(false);
            case GROUPS_ONLY -> viewerId != null &&
                    groupRepository.existsSharedGroup(e.getOrganizer().getId(), viewerId);
        };
    }

    @Transactional
    public Expedition cancel(Long expeditionId, Long userId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, userId);
        if (expedition.getStatus() == Expedition.ExpeditionStatus.COMPLETED ||
            expedition.getStatus() == Expedition.ExpeditionStatus.CANCELLED) {
            throw new IllegalArgumentException("Nie można odwołać wyprawy o tym statusie");
        }
        expedition.setStatus(Expedition.ExpeditionStatus.CANCELLED);
        Expedition saved = expeditionRepository.save(expedition);
        notifyExpeditionMembers(saved, "Wyprawa \"" + saved.getName() + "\" została odwołana przez organizatora");
        return saved;
    }

    void notifyExpeditionMembers(Expedition expedition, String message) {
        String url = "/expeditions/" + expedition.getId();
        expedition.getMembers().stream()
                .filter(m -> m.getStatus() == ExpeditionMember.MemberStatus.ACCEPTED)
                .forEach(m -> notificationService.notify(m.getUser().getId(),
                        NotificationType.EXPEDITION_STATUS_CHANGED, message, url));
    }

    @Transactional
    public ExpeditionMember join(Long expeditionId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));

        if (expedition.getStatus() != Expedition.ExpeditionStatus.PLANNED) {
            throw new IllegalArgumentException("Można dołączyć tylko do planowanych wypraw");
        }
        if (expedition.getOrganizer().getId().equals(userId)) {
            throw new IllegalArgumentException("Jesteś organizatorem tej wyprawy");
        }

        boolean alreadyMember = expedition.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId));
        if (alreadyMember) {
            throw new IllegalArgumentException("Jesteś już uczestnikiem tej wyprawy");
        }

        User user = findUser(userId);
        ExpeditionMember.MemberStatus joinStatus =
                expedition.getJoinMode() == Expedition.JoinMode.APPROVAL_REQUIRED
                        ? ExpeditionMember.MemberStatus.PENDING
                        : ExpeditionMember.MemberStatus.ACCEPTED;

        ExpeditionMember member = ExpeditionMember.builder()
                .expedition(expedition)
                .user(user)
                .status(joinStatus)
                .build();

        ExpeditionMember saved = memberRepository.save(member);
        String expUrl = "/expeditions/" + expeditionId;
        if (joinStatus == ExpeditionMember.MemberStatus.PENDING) {
            notificationService.notify(expedition.getOrganizer().getId(),
                    NotificationType.EXPEDITION_JOIN_REQUEST,
                    user.getName() + " chce dołączyć do wyprawy \"" + expedition.getName() + "\"", expUrl);
        }
        return saved;
    }

    @Transactional
    public ExpeditionMember approveMember(Long expeditionId, Long targetUserId, Long organizerId) {
        findAndCheckOrganizer(expeditionId, organizerId);
        ExpeditionMember member = memberRepository.findByExpeditionIdAndUserId(expeditionId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie jest uczestnikiem tej wyprawy"));

        if (member.getStatus() != ExpeditionMember.MemberStatus.PENDING) {
            throw new IllegalArgumentException("Tylko oczekujące prośby można zatwierdzić");
        }
        member.setStatus(ExpeditionMember.MemberStatus.ACCEPTED);
        ExpeditionMember saved = memberRepository.save(member);
        notificationService.notify(targetUserId, NotificationType.EXPEDITION_JOIN_APPROVED,
                "Twoja prośba o dołączenie do wyprawy \"" + member.getExpedition().getName() + "\" została zaakceptowana",
                "/expeditions/" + expeditionId);
        return saved;
    }

    // --- Mapa trasy ---

    @Transactional(readOnly = true)
    public List<double[]> getTrackPoints(Long expeditionId) throws IOException {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));

        String gpxPath = expedition.getGpxFilePath() != null
                ? expedition.getGpxFilePath()
                : (expedition.getTrail() != null ? expedition.getTrail().getGpxFilePath() : null);

        if (gpxPath == null) return List.of();

        java.nio.file.Path path = java.nio.file.Paths.get(gpxPath);
        if (!java.nio.file.Files.exists(path)) return List.of();

        try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
            return gpxParserService.parseTrackPoints(is);
        }
    }

    // --- Zaproszenia ---

    @Transactional
    public ExpeditionMember invite(Long expeditionId, String username, Long organizerId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, organizerId);
        User invited = userRepository.findByNameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika: " + username));

        if (invited.getId().equals(organizerId)) {
            throw new IllegalArgumentException("Nie można zaprosić samego siebie");
        }
        boolean alreadyMember = expedition.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(invited.getId()));
        if (alreadyMember) {
            throw new IllegalArgumentException("Użytkownik jest już uczestnikiem tej wyprawy");
        }

        ExpeditionMember member = ExpeditionMember.builder()
                .expedition(expedition)
                .user(invited)
                .status(ExpeditionMember.MemberStatus.INVITED)
                .build();

        ExpeditionMember saved = memberRepository.save(member);
        notificationService.notify(invited.getId(), NotificationType.EXPEDITION_INVITATION,
                "Zostałeś zaproszony do wyprawy \"" + expedition.getName() + "\" przez " + expedition.getOrganizer().getName(),
                "/expeditions/" + expeditionId);
        return saved;
    }

    @Transactional
    public String generateInviteLink(Long expeditionId, Long organizerId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, organizerId);
        String token = UUID.randomUUID().toString();
        expedition.setInviteToken(token);
        expeditionRepository.save(expedition);
        return token;
    }

    @Transactional
    public ExpeditionMember joinByInviteLink(String token, Long userId) {
        Expedition expedition = expeditionRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy link zaproszenia"));

        if (expedition.getStatus() != Expedition.ExpeditionStatus.PLANNED) {
            throw new IllegalArgumentException("Można dołączyć tylko do planowanych wypraw");
        }
        if (expedition.getOrganizer().getId().equals(userId)) {
            throw new IllegalArgumentException("Jesteś organizatorem tej wyprawy");
        }
        boolean alreadyMember = expedition.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId));
        if (alreadyMember) {
            throw new IllegalArgumentException("Jesteś już uczestnikiem tej wyprawy");
        }

        User user = findUser(userId);
        ExpeditionMember member = ExpeditionMember.builder()
                .expedition(expedition)
                .user(user)
                .status(ExpeditionMember.MemberStatus.ACCEPTED)
                .build();
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long expeditionId, Long targetUserId, Long requesterId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));

        boolean isOrganizer = expedition.getOrganizer().getId().equals(requesterId);
        boolean isSelf = requesterId.equals(targetUserId);

        if (!isOrganizer && !isSelf) {
            throw new AccessDeniedException("Brak uprawnień do tej operacji");
        }
        if (isOrganizer && expedition.getOrganizer().getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Organizator nie może opuścić własnej wyprawy");
        }

        ExpeditionMember member = memberRepository.findByExpeditionIdAndUserId(expeditionId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie jest uczestnikiem tej wyprawy"));

        memberRepository.delete(member);
    }

    @Transactional
    public ExpeditionMember respondToInvite(Long expeditionId, Long userId, boolean accept) {
        ExpeditionMember member = memberRepository
                .findByExpeditionIdAndUserId(expeditionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono zaproszenia"));

        if (member.getStatus() != ExpeditionMember.MemberStatus.INVITED) {
            throw new IllegalArgumentException("Zaproszenie zostalo juz rozpatrzone");
        }

        member.setStatus(accept
                ? ExpeditionMember.MemberStatus.ACCEPTED
                : ExpeditionMember.MemberStatus.DECLINED);

        return memberRepository.save(member);
    }

    // --- Komentarze ---

    @Transactional
    public ExpeditionComment addComment(Long expeditionId, String content, Long parentId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));

        if (!expeditionRepository.hasAccess(expeditionId, userId)) {
            throw new AccessDeniedException("Tylko czlonkowie wyprawy moga komentowac");
        }

        User author = findUser(userId);

        ExpeditionComment parent = null;
        if (parentId != null) {
            parent = commentRepository.findByIdAndExpeditionId(parentId, expeditionId)
                    .orElseThrow(() -> new IllegalArgumentException("Komentarz nadrzędny nie istnieje"));
        }

        ExpeditionComment comment = ExpeditionComment.builder()
                .expedition(expedition)
                .author(author)
                .content(content)
                .parent(parent)
                .build();

        return commentRepository.save(comment);
    }

    @Transactional
    public ExpeditionComment pinComment(Long expeditionId, Long commentId, Long organizerId) {
        findAndCheckOrganizer(expeditionId, organizerId);
        ExpeditionComment comment = commentRepository.findByIdAndExpeditionId(commentId, expeditionId)
                .orElseThrow(() -> new IllegalArgumentException("Komentarz nie istnieje"));
        if (comment.getParent() != null) {
            throw new IllegalArgumentException("Można przypiąć tylko komentarze główne");
        }
        comment.setPinned(!comment.isPinned());
        return commentRepository.save(comment);
    }

    // --- Helpers ---

    private static final java.util.Map<ExpeditionEquipment.EquipmentItem, String> EQUIPMENT_LABELS =
            java.util.Map.of(
                    ExpeditionEquipment.EquipmentItem.RACZKI,                   "Raczki",
                    ExpeditionEquipment.EquipmentItem.RAKI,                     "Raki",
                    ExpeditionEquipment.EquipmentItem.CZEKAN,                   "Czekan",
                    ExpeditionEquipment.EquipmentItem.KIJKI_TREKKINGOWE,        "Kijki trekkingowe",
                    ExpeditionEquipment.EquipmentItem.STUPTUTY,                 "Stuptuty",
                    ExpeditionEquipment.EquipmentItem.KASK,                     "Kask",
                    ExpeditionEquipment.EquipmentItem.LATARKA_CZOLOWA,          "Latarka czołowa",
                    ExpeditionEquipment.EquipmentItem.KREM_Z_FILTREM,           "Krem z filtrem",
                    ExpeditionEquipment.EquipmentItem.OKULARY_PRZECIWSLONECZNE, "Okulary przeciwsłoneczne"
            );

    private String buildEquipmentDiff(
            java.util.Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> oldMap,
            java.util.Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> newMap) {

        java.util.List<String> parts = new java.util.ArrayList<>();

        newMap.forEach((item, level) -> {
            String label = EQUIPMENT_LABELS.getOrDefault(item, item.name());
            String lvl = level == ExpeditionEquipment.RequirementLevel.REQUIRED ? "wymagany" : "zalecany";
            if (!oldMap.containsKey(item)) {
                parts.add("dodano " + label + " (" + lvl + ")");
            } else if (!oldMap.get(item).equals(level)) {
                String oldLvl = oldMap.get(item) == ExpeditionEquipment.RequirementLevel.REQUIRED ? "wymagany" : "zalecany";
                parts.add("zmieniono " + label + " (" + oldLvl + " → " + lvl + ")");
            }
        });
        oldMap.forEach((item, level) -> {
            if (!newMap.containsKey(item)) {
                parts.add("usunięto " + EQUIPMENT_LABELS.getOrDefault(item, item.name()));
            }
        });

        if (parts.isEmpty()) return null;
        return "Zmieniono sprzęt: " + String.join(", ", parts);
    }

    private void logChange(Expedition expedition, User user,
                           ExpeditionAuditLog.ChangeType type, String description) {
        auditLogRepository.save(ExpeditionAuditLog.builder()
                .expedition(expedition)
                .user(user)
                .changeType(type)
                .description(description)
                .build());
    }

    private Expedition findAndCheckOrganizerOrNavigator(Long expeditionId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));
        boolean isOrganizer = expedition.getOrganizer().getId().equals(userId);
        boolean isNavigator = expedition.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId)
                        && m.getStatus() == ExpeditionMember.MemberStatus.ACCEPTED
                        && m.getMemberRole() == ExpeditionMember.MemberRole.NAWIGATOR);
        if (!isOrganizer && !isNavigator) {
            throw new AccessDeniedException("Tylko organizator lub nawigator może wykonać tę akcję");
        }
        return expedition;
    }

    private Expedition findAndCheckOrganizer(Long expeditionId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));
        if (!expedition.getOrganizer().getId().equals(userId)) {
            throw new AccessDeniedException("Tylko organizator moze wykonac te akcje");
        }
        return expedition;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono uzytkownika id=" + userId));
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
