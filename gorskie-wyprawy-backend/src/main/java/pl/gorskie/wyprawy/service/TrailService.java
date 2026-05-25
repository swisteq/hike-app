package pl.gorskie.wyprawy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.gorskie.wyprawy.dto.GpxParseResult;
import pl.gorskie.wyprawy.dto.TrailFilterRequest;
import pl.gorskie.wyprawy.dto.TrailStats;
import pl.gorskie.wyprawy.model.Trail;
import pl.gorskie.wyprawy.repository.TrailRepository;
import pl.gorskie.wyprawy.service.gpx.GpxParserService;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrailService {

    private final TrailRepository trailRepository;
    private final GpxParserService gpxParserService;

    @Value("${app.uploads.dir:./uploads/gpx}")
    private String uploadsDir;

    // --- Import ---

    @Transactional
    public Trail importFromGpx(MultipartFile file) throws IOException {
        validateGpxFile(file);

        String fallbackName = stripGpxExtension(file.getOriginalFilename());
        GpxParseResult parsed = gpxParserService.parse(file.getInputStream(), fallbackName);
        String savedPath = saveGpxFile(file);

        Trail trail = Trail.builder()
                .name(parsed.getName())
                .gpxFilePath(savedPath)
                .gpxFileName(file.getOriginalFilename())
                .distanceKm(parsed.getDistanceKm())
                .elevationGainM(parsed.getElevationGainM())
                .elevationLossM(parsed.getElevationLossM())
                .maxElevationM(parsed.getMaxElevationM())
                .minElevationM(parsed.getMinElevationM())
                .durationMinutes(parsed.getDurationMinutes())
                .startLat(parsed.getStartLat())
                .startLon(parsed.getStartLon())
                .build();

        Trail saved = trailRepository.save(trail);
        log.info("Zaimportowano trasę id={} '{}'", saved.getId(), saved.getName());
        return saved;
    }

    // --- Filtrowanie i wyszukiwanie ---

    @Transactional(readOnly = true)
    public Page<Trail> findWithFilters(TrailFilterRequest filter) {
        Sort sort = Sort.by(filter.getSortDirection(), filter.getSafeSortField());
        Pageable pageable = PageRequest.of(
                Math.max(filter.getPage(), 0),
                Math.min(Math.max(filter.getSize(), 1), 100),
                sort
        );

        return trailRepository.findWithFilters(
                filter.getMinDistance(),
                filter.getMaxDistance(),
                filter.getMinDuration(),
                filter.getMaxDuration(),
                filter.getMinElevation(),
                filter.getMaxElevation(),
                emptyToNull(filter.getName()),
                pageable
        );
    }

    // --- Szczegóły ---

    @Transactional(readOnly = true)
    public Trail findById(Long id) {
        return trailRepository.findById(id)
                .orElseThrow(() -> new TrailNotFoundException(id));
    }

    // --- Pobieranie pliku GPX ---

    @Transactional(readOnly = true)
    public Resource getGpxFile(Long id) {
        Trail trail = findById(id);

        if (trail.getGpxFilePath() == null) {
            throw new IllegalStateException("Trasa id=" + id + " nie ma przypisanego pliku GPX");
        }

        try {
            Path path = Paths.get(trail.getGpxFilePath());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Plik GPX nie istnieje lub jest niedostępny: " + path);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Nieprawidłowa ścieżka pliku GPX", e);
        }
    }

    // --- Statystyki ---

    @Transactional(readOnly = true)
    public TrailStats getStats() {
        return trailRepository.getStats();
    }

    // --- Edycja ---

    @Transactional
    public Trail updateTrail(Long id, String description) {
        Trail trail = findById(id);
        if (description != null) trail.setDescription(description);
        return trailRepository.save(trail);
    }

    // --- Usuwanie ---

    @Transactional
    public void deleteTrail(Long id) {
        Trail trail = findById(id);
        if (trail.getGpxFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(trail.getGpxFilePath()));
            } catch (IOException e) {
                log.warn("Nie można usunąć pliku GPX: {}", trail.getGpxFilePath());
            }
        }
        trailRepository.delete(trail);
        log.info("Usunięto trasę id={}", id);
    }

    // --- Helpers ---

    public void validateGpxFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Plik GPX jest wymagany");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".gpx")) {
            throw new IllegalArgumentException("Plik musi mieć rozszerzenie .gpx");
        }
    }

    public String saveGpxFile(MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadsDir);
        Files.createDirectories(dir);
        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path dest = dir.resolve(uniqueName);
        file.transferTo(dest);
        return dest.toString();
    }

    private String stripGpxExtension(String filename) {
        return filename.replaceAll("(?i)\\.gpx$", "");
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
