package pl.gorskie.wyprawy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gorskie.wyprawy.model.Expedition;
import pl.gorskie.wyprawy.model.ExpeditionDay;
import pl.gorskie.wyprawy.model.ExpeditionLocation;
import pl.gorskie.wyprawy.model.GeonamesFeature;
import pl.gorskie.wyprawy.repository.ExpeditionDayRepository;
import pl.gorskie.wyprawy.repository.ExpeditionLocationRepository;
import pl.gorskie.wyprawy.repository.ExpeditionRepository;
import pl.gorskie.wyprawy.repository.GeonamesFeatureRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationRecognitionService {

    private static final double BBOX_MARGIN_DEG = 0.01;
    private static final double MAX_DISTANCE_M  = 100.0;

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("T.PK",   "Szczyt"),
            Map.entry("T.PKLT", "Wzniesienie"),
            Map.entry("T.MT",   "Góra"),
            Map.entry("T.PASS", "Przełęcz"),
            Map.entry("T.SADL", "Siodło"),
            Map.entry("T.RDG",  "Grzbiet"),
            Map.entry("T.VAL",  "Dolina"),
            Map.entry("S.HUT",  "Schronisko"),
            Map.entry("S.RSRT", "Ośrodek"),
            Map.entry("H.LK",   "Jezioro"),
            Map.entry("H.FLLS", "Wodospad"),
            Map.entry("H.SPNG", "Źródło"),
            Map.entry("L.PRK",  "Park")
    );

    private static final Set<String> PEAK_CODES = Set.of("PK", "PKLT", "MT");

    private final GeonamesFeatureRepository geonamesRepo;
    private final ExpeditionLocationRepository locationRepo;
    private final ExpeditionRepository expeditionRepository;
    private final ExpeditionDayRepository dayRepository;

    @Transactional
    public List<ExpeditionLocation> recognizeAndSave(Expedition expedition,
                                                     List<double[]> trackPoints) {
        return recognizeAndSave(expedition, trackPoints, null, null);
    }

    @Transactional
    public List<ExpeditionLocation> recognizeAndSave(Expedition expedition,
                                                     List<double[]> trackPoints,
                                                     Integer dayNumber,
                                                     ExpeditionDay day) {
        // Usuń stare lokalizacje dla tego dnia (i legacy bez dayNumber) przed dodaniem nowych
        if (dayNumber != null) {
            locationRepo.deleteByExpeditionIdAndDayNumber(expedition.getId(), dayNumber);
            locationRepo.deleteByExpeditionIdAndDayNumberIsNull(expedition.getId());
        }

        if (trackPoints.isEmpty()) {
            log.info("Brak punktów trasy dla wyprawy {}, pomijam rozpoznawanie lokalizacji", expedition.getId());
            return List.of();
        }

        if (!geonamesRepo.existsBy()) {
            log.info("Tabela geonames_features jest pusta — pomijam rozpoznawanie dla wyprawy {}", expedition.getId());
            return List.of();
        }

        double minLat = expedition.getBboxMinLat() - BBOX_MARGIN_DEG;
        double maxLat = expedition.getBboxMaxLat() + BBOX_MARGIN_DEG;
        double minLon = expedition.getBboxMinLon() - BBOX_MARGIN_DEG;
        double maxLon = expedition.getBboxMaxLon() + BBOX_MARGIN_DEG;

        List<GeonamesFeature> candidates = geonamesRepo.findWithinBbox(minLat, maxLat, minLon, maxLon);
        log.info("Znaleziono {} kandydatów GeoNames w bounding boxie wyprawy {}", candidates.size(), expedition.getId());

        List<ExpeditionLocation> results = new ArrayList<>();
        for (GeonamesFeature feature : candidates) {
            double minDist = Double.MAX_VALUE;
            int closestIdx = 0;

            for (int i = 0; i < trackPoints.size(); i++) {
                double d = haversineMeters(
                        feature.getLatitude(), feature.getLongitude(),
                        trackPoints.get(i)[0], trackPoints.get(i)[1]
                );
                if (d < minDist) {
                    minDist = d;
                    closestIdx = i;
                }
            }

            if (minDist <= MAX_DISTANCE_M) {
                results.add(ExpeditionLocation.builder()
                        .expedition(expedition)
                        .geonamesId(feature.getId())
                        .name(feature.getName())
                        .latitude(feature.getLatitude())
                        .longitude(feature.getLongitude())
                        .featureClass(feature.getFeatureClass())
                        .featureCode(feature.getFeatureCode())
                        .distanceM(Math.round(minDist * 10.0) / 10.0)
                        .routeIndex(closestIdx)
                        .typeLabelPl(toPolishLabel(feature.getFeatureClass(), feature.getFeatureCode()))
                        .dayNumber(dayNumber)
                        .build());
            }
        }

        results.sort(Comparator.comparingInt(ExpeditionLocation::getRouteIndex));
        List<ExpeditionLocation> saved = locationRepo.saveAll(results);
        log.info("Rozpoznano {} lokalizacji dla wyprawy {}", saved.size(), expedition.getId());

        // Najwyższy szczyt na trasie
        candidates.stream()
                .filter(f -> "T".equals(f.getFeatureClass()) && PEAK_CODES.contains(f.getFeatureCode()))
                .filter(f -> f.getElevationM() != null)
                .filter(f -> results.stream().anyMatch(r -> r.getGeonamesId().equals(f.getId())))
                .max(Comparator.comparingInt(GeonamesFeature::getElevationM))
                .ifPresent(peak -> {
                    expedition.setHighestPeakName(peak.getName());
                    expedition.setHighestPeakElevationM(peak.getElevationM());
                    log.info("Najwyższy szczyt wyprawy {}: {} ({} m)", expedition.getId(),
                            peak.getName(), peak.getElevationM());
                    if (day != null) {
                        day.setHighestPeakName(peak.getName());
                        day.setHighestPeakElevationM(peak.getElevationM());
                        dayRepository.save(day);
                    }
                });

        expeditionRepository.save(expedition);
        return saved;
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    static String toPolishLabel(String featureClass, String featureCode) {
        return LABELS.getOrDefault(featureClass + "." + featureCode,
                featureClass + "." + featureCode);
    }
}
