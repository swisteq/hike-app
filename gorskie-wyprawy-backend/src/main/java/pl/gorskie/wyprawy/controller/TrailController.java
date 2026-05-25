package pl.gorskie.wyprawy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.gorskie.wyprawy.dto.*;
import pl.gorskie.wyprawy.model.Trail;
import pl.gorskie.wyprawy.service.TrailService;

import java.io.IOException;

/**
 * REST API dla tras górskich.
 *
 * Endpointy:
 *
 *   POST   /api/trails/import              — import nowej trasy z GPX
 *   GET    /api/trails                     — lista tras z filtrowaniem, sortowaniem, paginacją
 *   GET    /api/trails/{id}                — szczegóły trasy
 *   GET    /api/trails/{id}/gpx            — pobierz plik GPX
 *   PATCH  /api/trails/{id}                — edytuj opis i tagi
 *   DELETE /api/trails/{id}                — usuń trasę
 *   GET    /api/trails/meta/tags           — lista unikalnych tagów lokalizacji
 *   GET    /api/trails/meta/stats          — statystyki zbiorcze
 */
@RestController
@RequestMapping("/api/trails")
@RequiredArgsConstructor
public class TrailController {

    private final TrailService trailService;

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    @PostMapping("/import")
    public ResponseEntity<TrailResponse> importTrail(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        Trail trail = trailService.importFromGpx(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(TrailResponse.from(trail));
    }

    // -------------------------------------------------------------------------
    // Lista z filtrowaniem i sortowaniem
    // -------------------------------------------------------------------------

    /**
     * GET /api/trails
     *
     * Parametry filtrowania (wszystkie opcjonalne):
     *   minDistance, maxDistance     — dystans w km
     *   minDuration, maxDuration     — czas w minutach
     *   minElevation, maxElevation   — przewyższenie w metrach
     *   locationTag                  — fragment nazwy szczytu/doliny
     *   name                         — fragment nazwy trasy
     *
     * Parametry sortowania:
     *   sort   — distanceKm | durationMinutes | elevationGainM | name | createdAt
     *   dir    — asc | desc
     *
     * Paginacja:
     *   page   — numer strony (od 0)
     *   size   — rozmiar strony (max 100)
     *
     * Przykłady:
     *   GET /api/trails?minDistance=10&maxDistance=20&sort=elevationGainM&dir=desc
     *   GET /api/trails?locationTag=Tatry&sort=distanceKm&page=1&size=10
     */
    @GetMapping
    public ResponseEntity<PagedResponse<TrailResponse>> getTrails(
            @ModelAttribute TrailFilterRequest filter
    ) {
        Page<Trail> page = trailService.findWithFilters(filter);
        return ResponseEntity.ok(PagedResponse.from(page, TrailResponse::from));
    }

    // -------------------------------------------------------------------------
    // Szczegóły
    // -------------------------------------------------------------------------

    /**
     * GET /api/trails/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TrailResponse> getTrail(@PathVariable Long id) {
        return ResponseEntity.ok(TrailResponse.from(trailService.findById(id)));
    }

    // -------------------------------------------------------------------------
    // Pobieranie pliku GPX
    // -------------------------------------------------------------------------

    /**
     * GET /api/trails/{id}/gpx
     *
     * Zwraca oryginalny plik GPX do pobrania.
     * Używany przez Leaflet.js na frontendzie lub do ręcznego pobrania.
     */
    @GetMapping("/{id}/gpx")
    public ResponseEntity<Resource> downloadGpx(@PathVariable Long id) {
        Trail trail = trailService.findById(id);
        Resource resource = trailService.getGpxFile(id);

        String filename = trail.getGpxFileName() != null ? trail.getGpxFileName() : "trail-" + id + ".gpx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/gpx+xml"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // -------------------------------------------------------------------------
    // Edycja
    // -------------------------------------------------------------------------

    public record UpdateTrailRequest(String description) {}

    @PatchMapping("/{id}")
    public ResponseEntity<TrailResponse> updateTrail(
            @PathVariable Long id,
            @RequestBody UpdateTrailRequest body) {
        Trail updated = trailService.updateTrail(id, body.description());
        return ResponseEntity.ok(TrailResponse.from(updated));
    }

    // -------------------------------------------------------------------------
    // Usuwanie
    // -------------------------------------------------------------------------

    /**
     * DELETE /api/trails/{id}
     *
     * Usuwa trasę z bazy danych i plik GPX z dysku.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrail(@PathVariable Long id) {
        trailService.deleteTrail(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Metadane
    // -------------------------------------------------------------------------

    /**
     * GET /api/trails/meta/stats
     *
     * Statystyki zbiorcze: liczba tras, łączny dystans, najdłuższa trasa, max przewyższenie.
     */
    @GetMapping("/meta/stats")
    public ResponseEntity<TrailStats> getStats() {
        return ResponseEntity.ok(trailService.getStats());
    }
}
