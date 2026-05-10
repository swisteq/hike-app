package pl.gorskie.wyprawy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gorskie.wyprawy.model.Expedition;
import pl.gorskie.wyprawy.model.Notification.NotificationType;
import pl.gorskie.wyprawy.repository.ExpeditionRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpeditionStatusScheduler {

    private final ExpeditionRepository expeditionRepository;
    private final ExpeditionService expeditionService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void transitionToOngoing() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<Expedition> candidates = expeditionRepository
                .findByStatusAndPlannedDateLessThanEqual(Expedition.ExpeditionStatus.PLANNED, today);

        for (Expedition e : candidates) {
            boolean shouldStart;
            if (e.getPlannedDate().isBefore(today)) {
                shouldStart = true;
            } else {
                // plannedDate == today
                shouldStart = e.getStartTime() == null || !now.isBefore(e.getStartTime());
            }
            if (shouldStart) {
                e.setStatus(Expedition.ExpeditionStatus.ONGOING);
                expeditionRepository.save(e);
                log.info("Wyprawa {} '{}' przeszła w status ONGOING", e.getId(), e.getName());
                String msg = "Wyprawa \"" + e.getName() + "\" właśnie się rozpoczęła";
                notificationService.notify(e.getOrganizer().getId(),
                        NotificationType.EXPEDITION_STATUS_CHANGED, msg, "/expeditions/" + e.getId());
                expeditionService.notifyExpeditionMembers(e, msg);
            }
        }
    }
}
