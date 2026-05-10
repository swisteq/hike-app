# Górskie Wyprawy

Aplikacja Spring Boot do wyszukiwania, udostępniania i planowania wypraw górskich.

## Wymagania

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

## Uruchomienie lokalne

### 1. Uruchom bazę danych PostgreSQL

```bash
docker-compose up postgres -d
```

### 2. Zbuduj i uruchom aplikację

```bash
mvn spring-boot:run
```

Lub przez Docker Compose (cały stack):

```bash
mvn package -DskipTests
docker-compose up --build
```

Aplikacja dostępna pod: http://localhost:8080

---

## Testy

```bash
mvn test
```

---

## API (Faza 1)

### Import trasy z GPX

```bash
curl -X POST http://localhost:8080/api/trails/import \
  -F "file=@route-f46ddc62.gpx" \
  -F "locationTags=Kuźnice" \
  -F "locationTags=Tatry Zachodnie"
```

### Lista tras

```bash
curl http://localhost:8080/api/trails
```

### Szczegóły trasy

```bash
curl http://localhost:8080/api/trails/1
```

---

## Struktura projektu

```
src/main/java/pl/gorskie/wyprawy/
├── GorskieWyprawyApplication.java   # Główna klasa Spring Boot
├── controller/
│   └── TrailController.java         # REST API
├── model/
│   └── Trail.java                   # Encja JPA
├── repository/
│   └── TrailRepository.java         # Queries + filtrowanie
├── service/
│   ├── TrailService.java            # Logika biznesowa
│   └── gpx/
│       ├── GpxParserService.java    # Parser GPX (JPX)
│       └── GpxParseException.java
└── dto/
    └── GpxParseResult.java          # DTO wyników parsowania
```

---

## Model danych Trail

| Pole                | Typ        | Opis                                      |
|---------------------|------------|-------------------------------------------|
| `distanceKm`        | Double     | Dystans w km (haversine)                  |
| `elevationGainM`    | Integer    | Suma podejść w metrach                    |
| `elevationLossM`    | Integer    | Suma zejść w metrach                      |
| `maxElevationM`     | Integer    | Max wysokość n.p.m.                       |
| `minElevationM`     | Integer    | Min wysokość n.p.m.                       |
| `durationMinutes`   | Integer    | Czas z tagów `<time>` w GPX               |
| `locationTags`      | List       | Nazwy szczytów/dolin — dodawane ręcznie   |
| `gpxFilePath`       | String     | Ścieżka do pliku GPX na dysku             |

---

## Następne fazy

- **Faza 2** — REST API z filtrowaniem i sortowaniem tras
- **Faza 3** — Użytkownicy, auth (Spring Security + JWT), planowanie wypraw
- **Faza 4** — Frontend (lista tras + mapa Leaflet.js)
- **Faza 5** — Testy integracyjne (Testcontainers) + wdrożenie
