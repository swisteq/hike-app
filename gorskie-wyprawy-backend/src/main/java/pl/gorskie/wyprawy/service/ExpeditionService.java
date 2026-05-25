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
import pl.gorskie.wyprawy.repository.ExpeditionAuditLogRepository;
import pl.gorskie.wyprawy.repository.ExpeditionCommentRepository;
import pl.gorskie.wyprawy.repository.ExpeditionDayRepository;
import pl.gorskie.wyprawy.repository.ExpeditionEquipmentRepository;
import pl.gorskie.wyprawy.repository.ExpeditionLocationRepository;
import pl.gorskie.wyprawy.repository.ExpeditionMemberRepository;
import pl.gorskie.wyprawy.repository.ExpeditionRepository;
import pl.gorskie.wyprawy.repository.ExpeditionTransportOptionRepository;
import pl.gorskie.wyprawy.repository.ExpeditionTransportSectionRepository;
import pl.gorskie.wyprawy.repository.FriendshipRepository;
import pl.gorskie.wyprawy.repository.GroupRepository;
import pl.gorskie.wyprawy.repository.UserRepository;
import pl.gorskie.wyprawy.service.gpx.GpxParserService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final ExpeditionEquipmentRepository equipmentRepository;
    private final ExpeditionAuditLogRepository auditLogRepository;
    private final ExpeditionDayRepository dayRepository;
    private final ExpeditionTransportSectionRepository transportSectionRepository;
    private final ExpeditionTransportOptionRepository transportOptionRepository;
    private final ExpeditionLocationRepository locationRepository;

    @Transactional
    public Expedition createFromGpx(MultipartFile file, String name, LocalDate plannedDate,
                                    LocalTime startTime, String description,
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
                .bboxMinLat(parsed.getBboxMinLat())
                .bboxMaxLat(parsed.getBboxMaxLat())
                .bboxMinLon(parsed.getBboxMinLon())
                .bboxMaxLon(parsed.getBboxMaxLon())
                .startLocationName(parsed.getStartWaypointName())
                .endLocationName(parsed.getEndWaypointName())
                .build();

        Expedition saved = expeditionRepository.save(expedition);
        locationRecognitionService.recognizeAndSave(saved, readTrackPoints(gpxPath));
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

        locationRecognitionService.recognizeAndSave(savedExp, readTrackPoints(gpxPath), dayNumber, saved);
        logChange(savedExp, findUser(userId), ExpeditionAuditLog.ChangeType.DAY_TRAIL_CHANGED,
                "Wgrano trasę dnia " + dayNumber + ": " + parsed.getName());
        return saved;
    }

    @Transactional
    public ExpeditionDay clearDayTrail(Long expeditionId, int dayNumber, Long userId) {
        Expedition expedition = findAndCheckOrganizerOrNavigator(expeditionId, userId);
        ExpeditionDay day = dayRepository.findByExpeditionIdAndDayNumber(expeditionId, dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("Dzień " + dayNumber + " nie istnieje w tej wyprawie"));

        String oldTrail = day.getTrailName();
        day.setTrailName(null);
        day.setDistanceKm(null);
        day.setElevationGainM(null);
        day.setElevationLossM(null);
        day.setMaxElevationM(null);
        day.setMinElevationM(null);
        day.setDurationMinutes(null);
        day.setGpxFilePath(null);
        day.setHighestPeakName(null);
        day.setHighestPeakElevationM(null);

        locationRepository.deleteByExpeditionIdAndDayNumber(expeditionId, dayNumber);

        ExpeditionDay saved = dayRepository.save(day);
        String desc = "Dzień " + dayNumber + " oznaczony jako dzień odpoczynku"
                + (oldTrail != null ? " (usunięto trasę: " + oldTrail + ")" : "");
        logChange(expedition, findUser(userId), ExpeditionAuditLog.ChangeType.DAY_TRAIL_CLEARED, desc);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<double[]> getDayTrackPoints(Long expeditionId, int dayNumber) throws IOException {
        ExpeditionDay day = dayRepository.findByExpeditionIdAndDayNumber(expeditionId, dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("Dzień " + dayNumber + " nie istnieje"));

        if (day.getGpxFilePath() == null) return List.of();

        Path path = Paths.get(day.getGpxFilePath());
        if (!Files.exists(path)) return List.of();

        try (InputStream is = Files.newInputStream(path)) {
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
    public Expedition findById(Long id) {
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
    public Expedition updateEquipment(Long expeditionId, List<ExpeditionDto.EquipmentItemRequest> items, Long userId) {
        Expedition expedition = findAndCheckOrganizerOrNavigator(expeditionId, userId);

        // Przechwytujemy stary stan jako niezmienną mapę — PRZED jakimikolwiek mutacjami encji
        Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> oldSnapshot =
                expedition.getEquipment().stream().collect(Collectors.toMap(
                        ExpeditionEquipment::getItem, ExpeditionEquipment::getLevel));

        Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> newMap =
                items.stream().collect(Collectors.toMap(
                        req -> ExpeditionEquipment.EquipmentItem.valueOf(req.getItem()),
                        req -> ExpeditionEquipment.RequirementLevel.valueOf(req.getLevel())));

        expedition.getEquipment().removeIf(eq -> !newMap.containsKey(eq.getItem()));
        expedition.getEquipment().forEach(eq -> eq.setLevel(newMap.get(eq.getItem())));
        Set<ExpeditionEquipment.EquipmentItem> existing =
                expedition.getEquipment().stream().map(ExpeditionEquipment::getItem)
                        .collect(Collectors.toSet());
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
    public Expedition changeTrail(Long expeditionId, MultipartFile file, Long userId) throws IOException {
        Expedition expedition = findAndCheckOrganizerOrNavigator(expeditionId, userId);
        trailService.validateGpxFile(file);

        String oldName = expedition.getTrailName() != null ? expedition.getTrailName() : "nieznana";

        String fallbackName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("(?i)\\.gpx$", "") : "Nieznana trasa";
        GpxParseResult parsed = gpxParserService.parse(file.getInputStream(), fallbackName);
        String gpxPath = trailService.saveGpxFile(file);

        expedition.setTrailName(parsed.getName());
        expedition.setDistanceKm(parsed.getDistanceKm());
        expedition.setElevationGainM(parsed.getElevationGainM());
        expedition.setElevationLossM(parsed.getElevationLossM());
        expedition.setMaxElevationM(parsed.getMaxElevationM());
        expedition.setMinElevationM(parsed.getMinElevationM());
        expedition.setDurationMinutes(parsed.getDurationMinutes());
        expedition.setGpxFilePath(gpxPath);
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
        locationRecognitionService.recognizeAndSave(saved, readTrackPoints(gpxPath));

        logChange(saved, findUser(userId), ExpeditionAuditLog.ChangeType.TRAIL_CHANGED,
                "Zmieniono trasę z \"" + oldName + "\" na \"" + parsed.getName() + "\"");

        return expeditionRepository.findById(expeditionId).orElse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpeditionAuditLog> getAuditLogs(Long expeditionId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));
        String role = resolveViewerRole(expedition, userId);
        if (!List.of("ORGANIZER", "MEMBER", "NAWIGATOR", "LOGISTYK").contains(role)) {
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
                    .map(f -> f.getStatus() == Friendship.FriendshipStatus.ACCEPTED)
                    .orElse(false);
            case GROUPS_ONLY -> viewerId != null &&
                    groupRepository.existsSharedGroup(e.getOrganizer().getId(), viewerId);
            case FRIENDS_AND_GROUPS -> viewerId != null && (
                    friendshipRepository.findBetween(e.getOrganizer().getId(), viewerId)
                            .map(f -> f.getStatus() == Friendship.FriendshipStatus.ACCEPTED)
                            .orElse(false)
                    || groupRepository.existsSharedGroup(e.getOrganizer().getId(), viewerId));
        };
    }

    @Transactional
    public Expedition cancel(Long expeditionId, Long userId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, userId);
        if (expedition.getStatus() == Expedition.ExpeditionStatus.COMPLETED ||
            expedition.getStatus() == Expedition.ExpeditionStatus.CANCELLED ||
            expedition.getStatus() == Expedition.ExpeditionStatus.UNREALIZED) {
            throw new IllegalArgumentException("Nie można odwołać wyprawy o tym statusie");
        }
        expedition.setStatus(Expedition.ExpeditionStatus.CANCELLED);
        Expedition saved = expeditionRepository.save(expedition);
        notifyExpeditionMembers(saved, "Wyprawa \"" + saved.getName() + "\" została odwołana przez organizatora");
        return saved;
    }

    @Transactional
    public Expedition markCompleted(Long expeditionId, Long userId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, userId);
        if (expedition.getStatus() != Expedition.ExpeditionStatus.ONGOING) {
            throw new IllegalArgumentException("Wyprawa musi być w trakcie, aby ją zakończyć");
        }
        if (!isStatusDeclarationWindowOpen(expedition)) {
            throw new IllegalArgumentException("Nie minęło jeszcze 24h od zakończenia wyprawy");
        }
        expedition.setStatus(Expedition.ExpeditionStatus.COMPLETED);
        Expedition saved = expeditionRepository.save(expedition);
        notifyExpeditionMembers(saved, "Wyprawa \"" + saved.getName() + "\" została zakończona");
        return saved;
    }

    @Transactional
    public Expedition markUnrealized(Long expeditionId, Long userId) {
        Expedition expedition = findAndCheckOrganizer(expeditionId, userId);
        if (expedition.getStatus() != Expedition.ExpeditionStatus.ONGOING) {
            throw new IllegalArgumentException("Wyprawa musi być w trakcie, aby oznaczyć ją jako niezrealizowaną");
        }
        if (!isStatusDeclarationWindowOpen(expedition)) {
            throw new IllegalArgumentException("Nie minęło jeszcze 24h od zakończenia wyprawy");
        }
        expedition.setStatus(Expedition.ExpeditionStatus.UNREALIZED);
        Expedition saved = expeditionRepository.save(expedition);
        notifyExpeditionMembers(saved, "Wyprawa \"" + saved.getName() + "\" została oznaczona jako niezrealizowana");
        return saved;
    }

    private boolean isStatusDeclarationWindowOpen(Expedition expedition) {
        LocalDate lastDay = expedition.getEndDate() != null
                ? expedition.getEndDate() : expedition.getPlannedDate();
        return LocalDateTime.now().isAfter(lastDay.plusDays(1).atStartOfDay());
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

        String gpxPath = expedition.getGpxFilePath();

        if (gpxPath == null) return List.of();

        Path path = Paths.get(gpxPath);
        if (!Files.exists(path)) return List.of();

        try (InputStream is = Files.newInputStream(path)) {
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

    private static final Map<ExpeditionEquipment.EquipmentItem, String> EQUIPMENT_LABELS =
            Map.of(
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
            Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> oldMap,
            Map<ExpeditionEquipment.EquipmentItem, ExpeditionEquipment.RequirementLevel> newMap) {

        List<String> parts = new ArrayList<>();

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

    @Transactional
    public ExpeditionDto.DayResponse setAccommodation(Long expeditionId, int dayNumber,
                                                       ExpeditionDto.AccommodationRequest request, Long userId) {
        Expedition expedition = findAndCheckOrganizerOrLogistyk(expeditionId, userId);
        ExpeditionDay day = dayRepository.findByExpeditionIdAndDayNumber(expeditionId, dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("Dzień " + dayNumber + " nie istnieje"));

        String url = request.getUrl() != null && !request.getUrl().isBlank() ? request.getUrl().trim() : null;
        String name = request.getName() != null && !request.getName().isBlank() ? request.getName().trim() : null;

        if (url != null && name == null) {
            if (url.contains("maps.app.goo.gl") || url.contains("maps.google.com") || url.contains("google.com/maps")) {
                name = resolveGoogleMapsName(url);
            } else if (url.contains("booking.com") || url.contains("bkng.com")) {
                name = resolveBookingName(url);
            }
        }

        String oldName = day.getAccommodationName();
        day.setAccommodationName(name);
        day.setAccommodationUrl(url);
        ExpeditionDto.DayResponse result = ExpeditionDto.DayResponse.from(dayRepository.save(day));
        String desc = oldName != null
                ? "Zmieniono nocleg dnia " + dayNumber + " z \"" + oldName + "\" na \"" + name + "\""
                : "Dodano nocleg dnia " + dayNumber + ": " + name;
        logChange(expedition, findUser(userId), ExpeditionAuditLog.ChangeType.ACCOMMODATION_CHANGED, desc);
        return result;
    }

    @Transactional
    public ExpeditionDto.DayResponse removeAccommodation(Long expeditionId, int dayNumber, Long userId) {
        Expedition expedition = findAndCheckOrganizerOrLogistyk(expeditionId, userId);
        ExpeditionDay day = dayRepository.findByExpeditionIdAndDayNumber(expeditionId, dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("Dzień " + dayNumber + " nie istnieje"));
        String oldName = day.getAccommodationName();
        day.setAccommodationName(null);
        day.setAccommodationUrl(null);
        ExpeditionDto.DayResponse result = ExpeditionDto.DayResponse.from(dayRepository.save(day));
        logChange(expedition, findUser(userId), ExpeditionAuditLog.ChangeType.ACCOMMODATION_CHANGED,
                "Usunięto nocleg dnia " + dayNumber + (oldName != null ? " (" + oldName + ")" : ""));
        return result;
    }

    @Transactional
    public ExpeditionDto.TransportSectionResponse addTransportOption(Long expeditionId, String type,
                                                                      ExpeditionDto.TransportOptionRequest req, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));
        if (req.getTransportType() == null)
            throw new IllegalArgumentException("Rodzaj transportu jest wymagany");
        if (req.getDescription() == null || req.getDescription().isBlank())
            throw new IllegalArgumentException("Opis opcji transportu jest wymagany");
        if (req.getTransportType() == ExpeditionTransportOption.TransportType.CAR
                && (req.getSeats() == null || req.getSeats() < 1))
            throw new IllegalArgumentException("Liczba miejsc jest wymagana dla transportu samochodem");

        boolean isPrivileged = isOrganizerOrLogistyk(expedition, userId);
        if (!isPrivileged && !isAcceptedMember(expedition, userId))
            throw new AccessDeniedException("Musisz być uczestnikiem wyprawy");

        String driverUsername = null;
        if (req.getTransportType() == ExpeditionTransportOption.TransportType.CAR) {
            driverUsername = userRepository.findById(userId).map(User::getName).orElse(null);
        }
        ExpeditionTransportSection section = getOrCreateSection(expedition, type);
        ExpeditionTransportOption option = ExpeditionTransportOption.builder()
                .section(section)
                .transportType(req.getTransportType())
                .description(req.getDescription().trim())
                .url(req.getUrl() != null && !req.getUrl().isBlank() ? req.getUrl().trim() : null)
                .seats(req.getTransportType() == ExpeditionTransportOption.TransportType.CAR ? req.getSeats() : null)
                .driverUsername(driverUsername)
                .approved(isPrivileged)
                .build();
        applyMeetingPoint(option, req.getMeetingPoint());
        section.getOptions().add(option);
        ExpeditionDto.TransportSectionResponse result =
                ExpeditionDto.TransportSectionResponse.from(transportSectionRepository.save(section));
        logChange(expedition, findUser(userId), ExpeditionAuditLog.ChangeType.TRANSPORT_CHANGED,
                "Dodano transport [" + transportSectionLabel(section) + "]: " + req.getDescription().trim());
        return result;
    }

    @Transactional
    public ExpeditionDto.TransportSectionResponse approveTransportOption(Long expeditionId, Long optionId, Long userId) {
        findAndCheckOrganizerOrLogistyk(expeditionId, userId);
        ExpeditionTransportOption option = transportOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Opcja transportu nie istnieje"));
        if (!option.getSection().getExpedition().getId().equals(expeditionId))
            throw new AccessDeniedException("Opcja nie należy do tej wyprawy");
        option.setApproved(true);
        transportOptionRepository.save(option);
        return ExpeditionDto.TransportSectionResponse.from(option.getSection());
    }

    @Transactional
    public ExpeditionDto.TransportSectionResponse updateTransportOption(Long expeditionId, Long optionId,
                                                                         ExpeditionDto.TransportOptionRequest req, Long userId) {
        findAndCheckOrganizerOrLogistyk(expeditionId, userId);
        if (req.getTransportType() == null)
            throw new IllegalArgumentException("Rodzaj transportu jest wymagany");
        if (req.getDescription() == null || req.getDescription().isBlank())
            throw new IllegalArgumentException("Opis opcji transportu jest wymagany");
        if (req.getTransportType() == ExpeditionTransportOption.TransportType.CAR
                && (req.getSeats() == null || req.getSeats() < 1))
            throw new IllegalArgumentException("Liczba miejsc jest wymagana dla transportu samochodem");

        ExpeditionTransportOption option = transportOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Opcja transportu nie istnieje"));
        if (!option.getSection().getExpedition().getId().equals(expeditionId))
            throw new AccessDeniedException("Opcja nie należy do tej wyprawy");
        applyMeetingPoint(option, req.getMeetingPoint());
        option.setTransportType(req.getTransportType());
        option.setDescription(req.getDescription().trim());
        option.setUrl(req.getUrl() != null && !req.getUrl().isBlank() ? req.getUrl().trim() : null);
        option.setSeats(req.getTransportType() == ExpeditionTransportOption.TransportType.CAR ? req.getSeats() : null);
        if (req.getTransportType() == ExpeditionTransportOption.TransportType.CAR && option.getDriverUsername() == null) {
            option.setDriverUsername(userRepository.findById(userId).map(User::getName).orElse(null));
        } else if (req.getTransportType() != ExpeditionTransportOption.TransportType.CAR) {
            option.setDriverUsername(null);
        }
        transportOptionRepository.save(option);
        logChange(option.getSection().getExpedition(), findUser(userId), ExpeditionAuditLog.ChangeType.TRANSPORT_CHANGED,
                "Zaktualizowano transport [" + transportSectionLabel(option.getSection()) + "]: " + req.getDescription().trim());
        return ExpeditionDto.TransportSectionResponse.from(option.getSection());
    }

    private void applyMeetingPoint(ExpeditionTransportOption option, String value) {
        if (value == null || value.isBlank()) {
            option.setMeetingPoint(null);
            option.setMeetingPointUrl(null);
        } else if (value.contains("maps.app.goo.gl")) {
            String resolved = resolveGoogleMapsName(value.trim());
            option.setMeetingPoint(resolved != null ? resolved : value.trim());
            option.setMeetingPointUrl(value.trim());
        } else if (value.startsWith("http://") || value.startsWith("https://")) {
            option.setMeetingPoint(value.trim());
            option.setMeetingPointUrl(value.trim());
        } else {
            option.setMeetingPoint(value.trim());
            option.setMeetingPointUrl(null);
        }
    }

    @Transactional
    public void deleteTransportOption(Long expeditionId, Long optionId, Long userId) {
        findAndCheckOrganizerOrLogistyk(expeditionId, userId);
        ExpeditionTransportOption option = transportOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Opcja transportu nie istnieje"));
        if (!option.getSection().getExpedition().getId().equals(expeditionId))
            throw new AccessDeniedException("Opcja nie należy do tej wyprawy");
        ExpeditionTransportSection section = option.getSection();
        String optionDesc = option.getDescription();
        String sectionDesc = transportSectionLabel(section);
        Expedition expedition = section.getExpedition();
        section.getOptions().remove(option);
        transportSectionRepository.save(section);
        logChange(expedition, findUser(userId), ExpeditionAuditLog.ChangeType.TRANSPORT_CHANGED,
                "Usunięto transport [" + sectionDesc + "]: " + optionDesc);
    }

    @Transactional
    public void deleteTransportSection(Long expeditionId, Long sectionId, Long userId) {
        Expedition expedition = findAndCheckOrganizerOrLogistyk(expeditionId, userId);
        ExpeditionTransportSection section = transportSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Sekcja transportu nie istnieje"));
        if (!section.getExpedition().getId().equals(expeditionId))
            throw new AccessDeniedException("Sekcja nie należy do tej wyprawy");
        String label = transportSectionLabel(section);
        transportSectionRepository.delete(section);
        logChange(expedition, findUser(userId), ExpeditionAuditLog.ChangeType.TRANSPORT_CHANGED,
                "Usunięto sekcję transportu: " + label);
    }

    private String transportSectionLabel(ExpeditionTransportSection section) {
        return switch (section.getSectionType()) {
            case ARRIVAL -> "Dojazd";
            case RETURN -> "Powrót";
            case DAY_TRANSITION -> "Dzień " + section.getDayNumber();
        };
    }

    private ExpeditionTransportSection getOrCreateSection(Expedition expedition, String type) {
        ExpeditionTransportSection.SectionType sectionType;
        Integer dayNumber = null;
        if ("arrival".equals(type)) {
            sectionType = ExpeditionTransportSection.SectionType.ARRIVAL;
        } else if ("return".equals(type)) {
            sectionType = ExpeditionTransportSection.SectionType.RETURN;
        } else if (type != null && type.startsWith("day-")) {
            sectionType = ExpeditionTransportSection.SectionType.DAY_TRANSITION;
            dayNumber = Integer.parseInt(type.substring(4));
        } else {
            throw new IllegalArgumentException("Nieznany typ sekcji transportu: " + type);
        }
        Integer finalDayNumber = dayNumber;
        return transportSectionRepository
                .findByExpeditionIdAndSectionTypeAndDayNumber(expedition.getId(), sectionType, dayNumber)
                .orElseGet(() -> transportSectionRepository.save(ExpeditionTransportSection.builder()
                        .expedition(expedition)
                        .sectionType(sectionType)
                        .dayNumber(finalDayNumber)
                        .build()));
    }

    public String resolvePlaceName(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.contains("maps.app.goo.gl")) {
            return resolveGoogleMapsName(url);
        }
        return null;
    }

    private String resolveGoogleMapsName(String url) {
        try {
            String current = url;
            for (int i = 0; i < 6; i++) {
                HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                int status = conn.getResponseCode();
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (status >= 300 && status < 400 && location != null) {
                    if (location.contains("/maps/place/")) {
                        String[] parts = location.split("/maps/place/");
                        if (parts.length > 1) {
                            String raw = parts[1].split("/")[0].split("\\?")[0];
                            return URLDecoder.decode(raw.replace("+", " "), StandardCharsets.UTF_8);
                        }
                    }
                    current = location.startsWith("http") ? location : "https://www.google.com" + location;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Nie udało się rozwiązać URL Google Maps: {}", url);
        }
        return null;
    }

    private String resolveBookingName(String url) {
        try {
            String target = url;
            if (!url.contains("/hotel/")) {
                target = resolveRedirect(url);
                if (target == null) return null;
            }
            Matcher m = Pattern.compile("/hotel/[a-z]{2}/([^./?]+)").matcher(target);
            if (m.find()) {
                String[] words = m.group(1).split("-");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (w.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Nie udało się wyciągnąć nazwy z URL Booking.com: {}", url);
        }
        return null;
    }

    private String resolveRedirect(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            int status = conn.getResponseCode();
            conn.disconnect();
            if (status >= 300 && status < 400) {
                return conn.getHeaderField("Location");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<double[]> readTrackPoints(String gpxPath) {
        try (InputStream is = Files.newInputStream(Paths.get(gpxPath))) {
            return gpxParserService.parseTrackPoints(is);
        } catch (Exception e) {
            log.warn("Nie udało się odczytać punktów trasy: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isOrganizerOrLogistyk(Expedition expedition, Long userId) {
        if (expedition.getOrganizer().getId().equals(userId)) return true;
        return expedition.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId)
                        && m.getStatus() == ExpeditionMember.MemberStatus.ACCEPTED
                        && m.getMemberRole() == ExpeditionMember.MemberRole.LOGISTYK);
    }

    private boolean isAcceptedMember(Expedition expedition, Long userId) {
        if (expedition.getOrganizer().getId().equals(userId)) return true;
        return expedition.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId)
                        && m.getStatus() == ExpeditionMember.MemberStatus.ACCEPTED);
    }

    private Expedition findAndCheckOrganizerOrLogistyk(Long expeditionId, Long userId) {
        Expedition expedition = expeditionRepository.findById(expeditionId)
                .orElseThrow(() -> new TrailNotFoundException(expeditionId));
        if (!isOrganizerOrLogistyk(expedition, userId)) {
            throw new AccessDeniedException("Tylko organizator lub logistyk może wykonać tę akcję");
        }
        return expedition;
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

}
