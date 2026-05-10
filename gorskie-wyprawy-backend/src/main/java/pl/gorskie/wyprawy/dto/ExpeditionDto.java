package pl.gorskie.wyprawy.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import pl.gorskie.wyprawy.model.Expedition;
import pl.gorskie.wyprawy.model.ExpeditionAuditLog;
import pl.gorskie.wyprawy.model.ExpeditionComment;
import pl.gorskie.wyprawy.model.ExpeditionDay;
import pl.gorskie.wyprawy.model.ExpeditionEquipment;
import pl.gorskie.wyprawy.model.ExpeditionLocation;
import pl.gorskie.wyprawy.model.ExpeditionMember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// viewerRole values: ORGANIZER, MEMBER, NAWIGATOR, LOGISTYK, PENDING, INVITED, VISITOR

public class ExpeditionDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Nazwa wyprawy jest wymagana")
        private String name;

        private String description;

        @NotNull(message = "Data wyprawy jest wymagana")
        @Future(message = "Data wyprawy musi byc w przyszlosci")
        private LocalDate plannedDate;

        @NotNull(message = "Trasa jest wymagana")
        private Long trailId;

        private Expedition.JoinMode joinMode;
        private Expedition.Visibility visibility;
        private LocalTime startTime;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private String description;
        private LocalDate plannedDate;
        private LocalTime startTime;
        private Expedition.JoinMode joinMode;
        private Expedition.Visibility visibility;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpeditionResponse {
        private Long id;
        private String name;
        private String description;
        private LocalDate plannedDate;
        private Expedition.ExpeditionStatus status;
        private Expedition.JoinMode joinMode;
        private Expedition.Visibility visibility;
        private LocalTime startTime;
        private AuthDto.UserResponse organizer;
        private TrailResponse trail;
        private List<MemberResponse> members;
        private List<CommentResponse> comments;
        private int memberCount;
        private LocalDateTime createdAt;
        private String viewerRole; // ORGANIZER | MEMBER | NAWIGATOR | LOGISTYK | PENDING | INVITED | VISITOR

        // pola trasy osadzone bezpośrednio
        private String trailName;
        private Double distanceKm;
        private Integer elevationGainM;
        private Integer elevationLossM;
        private Integer maxElevationM;
        private Integer minElevationM;
        private Integer durationMinutes;
        private String durationFormatted;
        private List<LocationResponse> locations;
        private String highestPeakName;
        private Integer highestPeakElevationM;
        private String routeLabel;
        private List<EquipmentResponse> equipment;
        private LocalDate endDate;
        private List<DayResponse> days;

        // Pełny dostęp — dla list "moje wyprawy" gdzie użytkownik jest zawsze członkiem
        public static ExpeditionResponse from(Expedition e) {
            return from(e, "MEMBER");
        }

        public static ExpeditionResponse from(Expedition e, String viewerRole) {
            boolean fullAccess = "ORGANIZER".equals(viewerRole) || "MEMBER".equals(viewerRole)
                    || "NAWIGATOR".equals(viewerRole) || "LOGISTYK".equals(viewerRole);

            String tName = e.getTrailName() != null ? e.getTrailName()
                    : (e.getTrail() != null ? e.getTrail().getName() : null);
            Double dist = e.getDistanceKm() != null ? e.getDistanceKm()
                    : (e.getTrail() != null ? e.getTrail().getDistanceKm() : null);
            Integer gain = e.getElevationGainM() != null ? e.getElevationGainM()
                    : (e.getTrail() != null ? e.getTrail().getElevationGainM() : null);
            Integer loss = e.getElevationLossM() != null ? e.getElevationLossM()
                    : (e.getTrail() != null ? e.getTrail().getElevationLossM() : null);
            Integer maxEl = e.getMaxElevationM() != null ? e.getMaxElevationM()
                    : (e.getTrail() != null ? e.getTrail().getMaxElevationM() : null);
            Integer minEl = e.getMinElevationM() != null ? e.getMinElevationM()
                    : (e.getTrail() != null ? e.getTrail().getMinElevationM() : null);
            Integer dur = e.getDurationMinutes() != null ? e.getDurationMinutes()
                    : (e.getTrail() != null ? e.getTrail().getDurationMinutes() : null);

            // Dla wypraw wielodniowych: agreguj statystyki ze wszystkich dni
            if (!e.getDays().isEmpty()) {
                double totalDist = e.getDays().stream()
                        .filter(d -> d.getDistanceKm() != null)
                        .mapToDouble(ExpeditionDay::getDistanceKm).sum();
                dist = totalDist > 0 ? Math.round(totalDist * 10.0) / 10.0 : null;

                int totalGain = e.getDays().stream()
                        .filter(d -> d.getElevationGainM() != null)
                        .mapToInt(ExpeditionDay::getElevationGainM).sum();
                gain = totalGain > 0 ? totalGain : null;

                int totalLoss = e.getDays().stream()
                        .filter(d -> d.getElevationLossM() != null)
                        .mapToInt(ExpeditionDay::getElevationLossM).sum();
                loss = totalLoss > 0 ? totalLoss : null;

                java.util.OptionalInt maxElOpt = e.getDays().stream()
                        .filter(d -> d.getMaxElevationM() != null)
                        .mapToInt(ExpeditionDay::getMaxElevationM).max();
                maxEl = maxElOpt.isPresent() ? maxElOpt.getAsInt() : null;

                java.util.OptionalInt minElOpt = e.getDays().stream()
                        .filter(d -> d.getMinElevationM() != null)
                        .mapToInt(ExpeditionDay::getMinElevationM).min();
                minEl = minElOpt.isPresent() ? minElOpt.getAsInt() : null;

                int totalDur = e.getDays().stream()
                        .filter(d -> d.getDurationMinutes() != null)
                        .mapToInt(ExpeditionDay::getDurationMinutes).sum();
                dur = totalDur > 0 ? totalDur : null;
            }

            return ExpeditionResponse.builder()
                    .id(e.getId())
                    .name(e.getName())
                    .description(e.getDescription())
                    .plannedDate(e.getPlannedDate())
                    .status(e.getStatus())
                    .joinMode(e.getJoinMode())
                    .visibility(e.getVisibility())
                    .startTime(e.getStartTime())
                    .organizer(AuthDto.UserResponse.from(e.getOrganizer()))
                    .trail(e.getTrail() != null ? TrailResponse.from(e.getTrail()) : null)
                    .members(fullAccess
                            ? e.getMembers().stream().map(MemberResponse::from).toList()
                            : null)
                    .comments(fullAccess
                            ? e.getComments().stream()
                                    .filter(c -> c.getParent() == null)
                                    .sorted(java.util.Comparator
                                            .comparing(ExpeditionComment::isPinned).reversed()
                                            .thenComparing(ExpeditionComment::getCreatedAt))
                                    .map(CommentResponse::from)
                                    .toList()
                            : null)
                    .memberCount(e.getMembers().size())
                    .createdAt(e.getCreatedAt())
                    .viewerRole(viewerRole)
                    .trailName(tName)
                    .distanceKm(dist)
                    .elevationGainM(gain)
                    .elevationLossM(loss)
                    .maxElevationM(maxEl)
                    .minElevationM(minEl)
                    .durationMinutes(dur)
                    .durationFormatted(formatDuration(dur))
                    .locations(e.getLocations().stream().map(LocationResponse::from).toList())
                    .highestPeakName(e.getHighestPeakName())
                    .highestPeakElevationM(e.getHighestPeakElevationM())
                    .routeLabel(buildRouteLabel(e))
                    .equipment(e.getEquipment().stream().map(EquipmentResponse::from).toList())
                    .endDate(e.getEndDate())
                    .days(e.getDays().stream().map(DayResponse::from).toList())
                    .build();
        }

        private static String formatDuration(Integer minutes) {
            if (minutes == null) return null;
            int h = minutes / 60;
            int m = minutes % 60;
            return h > 0 ? h + "h " + m + "min" : m + "min";
        }

        private static String buildRouteLabel(pl.gorskie.wyprawy.model.Expedition e) {
            String start = e.getStartLocationName();
            String end   = e.getEndLocationName();
            String peak  = e.getHighestPeakName();

            if (start == null && end == null) {
                // Brak danych GeoNames — fallback do nazwy trasy z GPX
                return e.getTrailName() != null ? e.getTrailName()
                        : (e.getTrail() != null ? e.getTrail().getName() : null);
            }

            boolean loop = start != null && start.equals(end);

            if (peak != null && !peak.equals(start) && !peak.equals(end)) {
                // start — szczyt — koniec  (lub  start — szczyt  dla pętli)
                return loop
                        ? start + " — " + peak + " — " + start
                        : (start != null ? start : "") + " — " + peak
                          + (end != null && !end.equals(start) ? " — " + end : "");
            }

            if (loop) return start;

            if (start != null && end != null) return start + " — " + end;
            return start != null ? start : end;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private Long id;
        private AuthDto.UserResponse user;
        private ExpeditionMember.MemberStatus status;
        private ExpeditionMember.MemberRole memberRole;
        private LocalDateTime createdAt;

        public static MemberResponse from(ExpeditionMember m) {
            return MemberResponse.builder()
                    .id(m.getId())
                    .user(AuthDto.UserResponse.from(m.getUser()))
                    .status(m.getStatus())
                    .memberRole(m.getMemberRole() != null ? m.getMemberRole() : ExpeditionMember.MemberRole.MEMBER)
                    .createdAt(m.getCreatedAt())
                    .build();
        }
    }

    @Data
    public static class RoleRequest {
        private String role; // MEMBER | NAWIGATOR | LOGISTYK
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentResponse {
        private Long id;
        private AuthDto.UserResponse author;
        private String content;
        private LocalDateTime createdAt;
        private boolean pinned;
        private Long parentId;
        private List<CommentResponse> replies;

        public static CommentResponse from(ExpeditionComment c) {
            return CommentResponse.builder()
                    .id(c.getId())
                    .author(AuthDto.UserResponse.from(c.getAuthor()))
                    .content(c.getContent())
                    .createdAt(c.getCreatedAt())
                    .pinned(c.isPinned())
                    .parentId(c.getParent() != null ? c.getParent().getId() : null)
                    .replies(c.getReplies().stream().map(CommentResponse::from).toList())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationResponse {
        private Long id;
        private String name;
        private Double latitude;
        private Double longitude;
        private String featureClass;
        private String featureCode;
        private String typeLabelPl;
        private Double distanceM;
        private Integer routeIndex;
        private Integer dayNumber;

        public static LocationResponse from(ExpeditionLocation loc) {
            return LocationResponse.builder()
                    .id(loc.getId())
                    .name(loc.getName())
                    .latitude(loc.getLatitude())
                    .longitude(loc.getLongitude())
                    .featureClass(loc.getFeatureClass())
                    .featureCode(loc.getFeatureCode())
                    .typeLabelPl(loc.getTypeLabelPl())
                    .distanceM(loc.getDistanceM())
                    .routeIndex(loc.getRouteIndex())
                    .dayNumber(loc.getDayNumber())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentResponse {
        private ExpeditionEquipment.EquipmentItem item;
        private ExpeditionEquipment.RequirementLevel level;

        public static EquipmentResponse from(ExpeditionEquipment eq) {
            return EquipmentResponse.builder()
                    .item(eq.getItem())
                    .level(eq.getLevel())
                    .build();
        }
    }

    @Data
    public static class EquipmentItemRequest {
        private String item;
        private String level;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogResponse {
        private Long id;
        private AuthDto.UserResponse user;
        private java.time.LocalDateTime createdAt;
        private ExpeditionAuditLog.ChangeType changeType;
        private String description;

        public static AuditLogResponse from(ExpeditionAuditLog log) {
            return AuditLogResponse.builder()
                    .id(log.getId())
                    .user(AuthDto.UserResponse.from(log.getUser()))
                    .createdAt(log.getCreatedAt())
                    .changeType(log.getChangeType())
                    .description(log.getDescription())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayResponse {
        private Long id;
        private int dayNumber;
        private LocalDate dayDate;
        private String trailName;
        private Double distanceKm;
        private Integer elevationGainM;
        private Integer elevationLossM;
        private Integer maxElevationM;
        private Integer minElevationM;
        private Integer durationMinutes;
        private String durationFormatted;
        private boolean hasTrack;

        public static DayResponse from(ExpeditionDay day) {
            Integer dur = day.getDurationMinutes();
            return DayResponse.builder()
                    .id(day.getId())
                    .dayNumber(day.getDayNumber())
                    .dayDate(day.getDayDate())
                    .trailName(day.getTrailName())
                    .distanceKm(day.getDistanceKm())
                    .elevationGainM(day.getElevationGainM())
                    .elevationLossM(day.getElevationLossM())
                    .maxElevationM(day.getMaxElevationM())
                    .minElevationM(day.getMinElevationM())
                    .durationMinutes(dur)
                    .durationFormatted(dur == null ? null : (dur / 60 > 0 ? dur / 60 + "h " + dur % 60 + "min" : dur % 60 + "min"))
                    .hasTrack(day.getGpxFilePath() != null)
                    .build();
        }
    }

    @Data
    public static class MultiDayCreateRequest {
        @NotBlank(message = "Nazwa wyprawy jest wymagana")
        private String name;
        private String description;
        @NotNull(message = "Data startowa jest wymagana")
        private LocalDate plannedDate;
        @NotNull(message = "Data końcowa jest wymagana")
        private LocalDate endDate;
        private LocalTime startTime;
        private Expedition.JoinMode joinMode;
        private Expedition.Visibility visibility;
    }

    @Data
    public static class InviteRequest {
        @NotBlank(message = "Nazwa użytkownika jest wymagana")
        private String username;
    }

    @Data
    public static class CommentRequest {
        @NotBlank(message = "Tresc komentarza jest wymagana")
        private String content;
        private Long parentId;
    }
}
