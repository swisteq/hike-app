package pl.gorskie.wyprawy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.gorskie.wyprawy.dto.GpxParseResult;
import pl.gorskie.wyprawy.service.gpx.GpxParserService;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GpxParserService — testy parsowania pliku mapa-turystyczna.pl")
class GpxParserServiceTest {

    private GpxParserService parser;

    @BeforeEach
    void setUp() {
        parser = new GpxParserService();
    }

    @Test
    @DisplayName("Parsuje przykładowy plik GPX z Kuźnic")
    void shouldParseKuzniceSampleFile() throws Exception {
        InputStream gpx = getClass().getResourceAsStream("/test-routes/route-f46ddc62.gpx");
        assertThat(gpx).as("Plik testowy powinien istnieć w src/test/resources").isNotNull();

        GpxParseResult result = parser.parse(gpx, "Kuźnice – Kuźnice");

        // Nazwa trasy
        assertThat(result.getName()).contains("Kuźnice");

        // Dystans — pętla z Kuźnic powinna być ~10–20 km
        assertThat(result.getDistanceKm())
                .as("Dystans w km")
                .isBetween(5.0, 30.0);

        // Przewyższenie — trasa w Tatrach, minimum kilkaset metrów
        assertThat(result.getElevationGainM())
                .as("Przewyższenie w metrach")
                .isGreaterThan(100);

        // Wysokości — Kuźnice startują ok. 1000 m n.p.m.
        assertThat(result.getMinElevationM()).isGreaterThan(900);
        assertThat(result.getMaxElevationM()).isGreaterThan(result.getMinElevationM());

        // Czas — cały dzień, powinien być > 0
        assertThat(result.getDurationMinutes())
                .as("Czas trasy w minutach")
                .isNotNull()
                .isGreaterThan(0);

        // Punkt startowy w Tatrach
        assertThat(result.getStartLat()).isBetween(49.0, 50.0);
        assertThat(result.getStartLon()).isBetween(19.0, 21.0);

        // Bounding box
        assertThat(result.getBboxMinLat()).isLessThan(result.getBboxMaxLat());
        assertThat(result.getBboxMinLon()).isLessThan(result.getBboxMaxLon());

        // Liczba punktów
        assertThat(result.getTrackPointsCount()).isGreaterThan(100);

        System.out.printf("""
            === Wyniki parsowania GPX ===%n
            Nazwa:        %s%n
            Dystans:      %.2f km%n
            Podejście:    +%d m%n
            Zejście:      -%d m%n
            Max/Min:      %d / %d m n.p.m.%n
            Czas:         %d min%n
            Pkt trasy:    %d%n
            Start:        %.6f, %.6f%n
            """,
                result.getName(),
                result.getDistanceKm(),
                result.getElevationGainM(),
                result.getElevationLossM(),
                result.getMaxElevationM(),
                result.getMinElevationM(),
                result.getDurationMinutes(),
                result.getTrackPointsCount(),
                result.getStartLat(),
                result.getStartLon()
        );
    }

    @Test
    @DisplayName("Rzuca wyjątek dla pustego strumienia")
    void shouldThrowForEmptyStream() {
        InputStream empty = InputStream.nullInputStream();
        assertThatThrownBy(() -> parser.parse(empty, "test"))
                .isInstanceOf(RuntimeException.class);
    }
}
