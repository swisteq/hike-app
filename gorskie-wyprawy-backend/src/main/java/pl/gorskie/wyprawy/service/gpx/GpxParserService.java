package pl.gorskie.wyprawy.service.gpx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pl.gorskie.wyprawy.dto.GpxParseResult;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GpxParserService {

    private static final double ELEVATION_NOISE_THRESHOLD = 2.0;

    public List<double[]> parseTrackPoints(InputStream inputStream) {
        try {
            Document doc = buildDocument(inputStream);
            NodeList trkpts = doc.getElementsByTagName("trkpt");
            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < trkpts.getLength(); i++) {
                Element pt = (Element) trkpts.item(i);
                double lat = Double.parseDouble(pt.getAttribute("lat"));
                double lon = Double.parseDouble(pt.getAttribute("lon"));
                points.add(new double[]{lat, lon});
            }
            return points;
        } catch (Exception e) {
            throw new GpxParseException("Blad odczytu punktow GPX: " + e.getMessage(), e);
        }
    }

    public GpxParseResult parse(InputStream inputStream, String fallbackName) {
        try {
            Document doc = buildDocument(inputStream);
            String name = extractName(doc, fallbackName);

            NodeList trkpts = doc.getElementsByTagName("trkpt");
            if (trkpts.getLength() == 0) {
                throw new IllegalArgumentException("Plik GPX nie zawiera punktow trasy (<trkpt>)");
            }

            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < trkpts.getLength(); i++) {
                Element pt = (Element) trkpts.item(i);
                double lat = Double.parseDouble(pt.getAttribute("lat"));
                double lon = Double.parseDouble(pt.getAttribute("lon"));
                double ele = getDouble(pt, "ele", 0);
                long time = parseTime(pt);
                points.add(new double[]{lat, lon, ele, time});
            }

            double distanceKm = calculateDistance(points);
            int[] elevations = calculateElevations(points);
            Integer durationMinutes = calculateDuration(points);
            double[] bbox = calculateBbox(points);

            List<String> waypointNames = extractWaypointNames(doc);

            GpxParseResult result = GpxParseResult.builder()
                    .name(name)
                    .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
                    .elevationGainM(elevations[0])
                    .elevationLossM(elevations[1])
                    .maxElevationM(elevations[2])
                    .minElevationM(elevations[3])
                    .durationMinutes(durationMinutes)
                    .startLat(points.get(0)[0])
                    .startLon(points.get(0)[1])
                    .bboxMinLat(bbox[0])
                    .bboxMaxLat(bbox[1])
                    .bboxMinLon(bbox[2])
                    .bboxMaxLon(bbox[3])
                    .startWaypointName(waypointNames.isEmpty() ? null : waypointNames.get(0))
                    .endWaypointName(waypointNames.isEmpty() ? null : waypointNames.get(waypointNames.size() - 1))
                    .build();

            log.info("Sparsowano GPX: '{}' - {} km, +{}m, {}min, {} pkt",
                    name, result.getDistanceKm(), elevations[0], durationMinutes, points.size());

            return result;

        } catch (GpxParseException e) {
            throw e;
        } catch (Exception e) {
            throw new GpxParseException("Blad parsowania pliku GPX: " + e.getMessage(), e);
        }
    }

    private List<String> extractWaypointNames(Document doc) {
        List<String> names = new ArrayList<>();
        NodeList wpts = doc.getElementsByTagName("wpt");
        for (int i = 0; i < wpts.getLength(); i++) {
            Element wpt = (Element) wpts.item(i);
            NodeList nameNodes = wpt.getElementsByTagName("name");
            if (nameNodes.getLength() > 0) {
                String n = nameNodes.item(0).getTextContent().trim();
                if (!n.isBlank()) names.add(n);
            }
        }
        // Fallback: route points (<rtept>) jeśli brak <wpt>
        if (names.isEmpty()) {
            NodeList rtepts = doc.getElementsByTagName("rtept");
            for (int i = 0; i < rtepts.getLength(); i++) {
                Element pt = (Element) rtepts.item(i);
                NodeList nameNodes = pt.getElementsByTagName("name");
                if (nameNodes.getLength() > 0) {
                    String n = nameNodes.item(0).getTextContent().trim();
                    if (!n.isBlank()) names.add(n);
                }
            }
        }
        return names;
    }

    private String extractName(Document doc, String fallbackName) {
        NodeList nameNodes = doc.getElementsByTagName("name");
        if (nameNodes.getLength() > 0) {
            String n = nameNodes.item(0).getTextContent().trim();
            if (!n.isBlank()) return n;
        }
        NodeList nNodes = doc.getElementsByTagName("n");
        if (nNodes.getLength() > 0) {
            String n = nNodes.item(0).getTextContent().trim();
            if (!n.isBlank()) return n;
        }
        return fallbackName;
    }

    private double calculateDistance(List<double[]> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineKm(points.get(i - 1), points.get(i));
        }
        return total;
    }

    private double haversineKm(double[] a, double[] b) {
        final double R = 6371.0;
        double lat1 = Math.toRadians(a[0]), lat2 = Math.toRadians(b[0]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    private int[] calculateElevations(List<double[]> points) {
        double gain = 0, loss = 0;
        double maxEle = Double.MIN_VALUE, minEle = Double.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            double prev = points.get(i - 1)[2];
            double curr = points.get(i)[2];
            double diff = curr - prev;
            if (Math.abs(diff) >= ELEVATION_NOISE_THRESHOLD) {
                if (diff > 0) gain += diff;
                else loss += Math.abs(diff);
            }
            maxEle = Math.max(maxEle, curr);
            minEle = Math.min(minEle, curr);
        }
        return new int[]{
                (int) Math.round(gain),
                (int) Math.round(loss),
                (int) Math.round(maxEle == Double.MIN_VALUE ? 0 : maxEle),
                (int) Math.round(minEle == Double.MAX_VALUE ? 0 : minEle)
        };
    }

    private Integer calculateDuration(List<double[]> points) {
        long first = (long) points.get(0)[3];
        long last = (long) points.get(points.size() - 1)[3];
        if (first == 0 || last == 0) return null;
        return (int) Math.abs(Duration.between(
                Instant.ofEpochSecond(first),
                Instant.ofEpochSecond(last)
        ).toMinutes());
    }

    private double[] calculateBbox(List<double[]> points) {
        double minLat = points.stream().mapToDouble(p -> p[0]).min().orElse(0);
        double maxLat = points.stream().mapToDouble(p -> p[0]).max().orElse(0);
        double minLon = points.stream().mapToDouble(p -> p[1]).min().orElse(0);
        double maxLon = points.stream().mapToDouble(p -> p[1]).max().orElse(0);
        return new double[]{minLat, maxLat, minLon, maxLon};
    }

    private double getDouble(Element el, String tag, double defaultVal) {
        NodeList nodes = el.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return defaultVal;
        try {
            return Double.parseDouble(nodes.item(0).getTextContent().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private Document buildDocument(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document doc = factory.newDocumentBuilder().parse(inputStream);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private long parseTime(Element pt) {
        NodeList nodes = pt.getElementsByTagName("time");
        if (nodes.getLength() == 0) return 0;
        try {
            return Instant.parse(nodes.item(0).getTextContent().trim()).getEpochSecond();
        } catch (Exception e) {
            return 0;
        }
    }
}
