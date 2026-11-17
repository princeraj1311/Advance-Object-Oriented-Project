package cinesmart.service;

import cinesmart.model.CinemaVenue;
import cinesmart.model.Locality;
import cinesmart.util.AppConfig;
import cinesmart.util.DataStore;
import cinesmart.util.JsonParser;
import cinesmart.util.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads named cinema venues from OpenStreetMap's public Overpass API. */
public final class OpenStreetMapCinemaService {

    private static final double SEARCH_RADIUS_METERS = 25_000;
    private static final int MAX_PER_CITY = 4;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<CinemaVenue> fetchCinemaListings(DataStore store)
            throws IOException, InterruptedException {
        Map<String, Locality> centers = cityCenters(store);
        if (centers.isEmpty()) {
            throw new IOException("No supported city coordinates are available for the cinema "
                    + "search.");
        }
        String query = buildQuery(centers);
        HttpRequest request = HttpRequest.newBuilder(URI.create(AppConfig.OSM_OVERPASS_URL))
                .timeout(Duration.ofSeconds(50))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", "CineSmart/1.0 (student project; OpenStreetMap directory)")
                .POST(HttpRequest.BodyPublishers.ofString("data=" + encode(query),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("OpenStreetMap cinema lookup failed (HTTP "
                    + response.statusCode() + "). Try again later.");
        }

        Object parsed;
        try {
            parsed = JsonParser.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IOException("OpenStreetMap returned invalid JSON.", e);
        }
        Map<?, ?> root = object(parsed);
        Object rawElements = root.get("elements");
        if (!(rawElements instanceof List<?>)) {
            throw new IOException("OpenStreetMap response did not contain a venue list.");
        }
        Map<String, List<CinemaVenue>> byCity = new HashMap<>();
        for (Object raw : (List<?>) rawElements) {
            Map<?, ?> element = object(raw);
            Map<?, ?> tags = object(element.get("tags"));
            String name = text(tags.get("name"));
            if (name.isEmpty()) {
                name = text(tags.get("name:en"));
            }
            if (name.isEmpty()) {
                continue;
            }
            double latitude = coordinate(element, "lat");
            double longitude = coordinate(element, "lon");
            if ((latitude == 0 && longitude == 0) && element.get("center") != null) {
                Map<?, ?> center = object(element.get("center"));
                latitude = coordinate(center, "lat");
                longitude = coordinate(center, "lon");
            }
            Locality city = nearestCity(centers, latitude, longitude);
            if (city == null || distanceMeters(city.getLatitude(), city.getLongitude(),
                    latitude, longitude) > SEARCH_RADIUS_METERS) {
                continue;
            }
            String type = text(element.get("type"));
            String id = text(element.get("id"));
            if (type.isEmpty() || id.isEmpty()) {
                continue;
            }
            String venueId = type + "_" + id;
            String sourceUrl = "https://www.openstreetmap.org/" + type + "/" + id;
            CinemaVenue venue = new CinemaVenue(venueId, city.getCity(), name,
                    address(tags), latitude, longitude, sourceUrl);
            byCity.computeIfAbsent(city.getCity(), ignored -> new ArrayList<>()).add(venue);
        }

        List<CinemaVenue> venues = new ArrayList<>();
        for (String city : Session.CITIES) {
            List<CinemaVenue> entries = byCity.getOrDefault(city, new ArrayList<>());
            entries.sort(Comparator.comparing(CinemaVenue::getName,
                    String.CASE_INSENSITIVE_ORDER));
            for (int index = 0; index < Math.min(MAX_PER_CITY, entries.size()); index++) {
                venues.add(entries.get(index));
            }
        }
        return venues;
    }

    private static Map<String, Locality> cityCenters(DataStore store) {
        Map<String, Locality> centers = new LinkedHashMap<>();
        for (String city : Session.CITIES) {
            for (Locality locality : store.getLocalities()) {
                if (locality.getCity().equalsIgnoreCase(city) && locality.isCityOnly()
                        && locality.hasPosition()) {
                    centers.put(city, locality);
                    break;
                }
            }
        }
        return centers;
    }

    private static String buildQuery(Map<String, Locality> centers) {
        StringBuilder query = new StringBuilder("[out:json][timeout:40];(");
        for (Locality center : centers.values()) {
            String around = "(around:" + (int) SEARCH_RADIUS_METERS + ","
                    + center.getLatitude() + "," + center.getLongitude() + ")";
            query.append("node[\"amenity\"=\"cinema\"]").append(around).append(';')
                    .append("way[\"amenity\"=\"cinema\"]").append(around).append(';')
                    .append("relation[\"amenity\"=\"cinema\"]").append(around).append(';');
        }
        return query.append(");out center tags;").toString();
    }

    private static Locality nearestCity(Map<String, Locality> centers, double latitude,
                                        double longitude) {
        Locality nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Locality center : centers.values()) {
            double distance = distanceMeters(center.getLatitude(), center.getLongitude(),
                    latitude, longitude);
            if (distance < nearestDistance) {
                nearest = center;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String address(Map<?, ?> tags) {
        List<String> parts = new ArrayList<>();
        add(parts, tags.get("addr:housenumber"));
        add(parts, tags.get("addr:street"));
        if (parts.isEmpty()) {
            add(parts, tags.get("addr:suburb"));
            add(parts, tags.get("addr:neighbourhood"));
            add(parts, tags.get("addr:city"));
        }
        return String.join(", ", parts);
    }

    private static void add(List<String> values, Object value) {
        String text = text(value);
        if (!text.isEmpty() && !values.contains(text)) {
            values.add(text);
        }
    }

    private static double coordinate(Map<?, ?> values, String key) {
        Object value = values.get(key);
        try {
            return value instanceof Number ? ((Number) value).doubleValue()
                    : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<?, ?>) value : Map.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
