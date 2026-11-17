package cinesmart.service;

import cinesmart.model.Movie;
import cinesmart.util.AppConfig;
import cinesmart.util.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Downloads up to 50 highly rated 2026 titles from TMDb's discover API. */
public final class RemoteMovieDataService {

    private static final String API_ROOT = "https://api.themoviedb.org/3";
    private static final int MOVIE_LIMIT = 50;
    private static final int PAGES_PER_TIER = 4;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<Movie> fetchLatest2026Movies() throws IOException, InterruptedException {
        if (AppConfig.TMDB_API_KEY.isBlank()) {
            throw new IOException("TMDb API key is missing. Set tmdbApiKey in .env to load "
                    + "the live 2026 movie catalogue.");
        }
        Map<Integer, String> genres = fetchGenres();
        Map<Integer, Movie> movies = new LinkedHashMap<>();
        collectMovies(movies, genres, 6.5, 20, 3);
        if (movies.size() < MOVIE_LIMIT) {
            collectMovies(movies, genres, 6.0, 5, PAGES_PER_TIER);
        }
        if (movies.isEmpty()) {
            throw new IOException("TMDb returned no eligible high-rated 2026 movies.");
        }
        List<Movie> ranked = new ArrayList<>(movies.values());
        ranked.sort(Comparator.comparingDouble(Movie::getRating).reversed()
                .thenComparing(Comparator.comparingInt(Movie::getTotalReviews).reversed())
                .thenComparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER));
        if (ranked.size() > MOVIE_LIMIT) {
            ranked = new ArrayList<>(ranked.subList(0, MOVIE_LIMIT));
        }
        return ranked;
    }

    private void collectMovies(Map<Integer, Movie> movies, Map<Integer, String> genres,
                               double minimumRating, int minimumVotes, int maxPages)
            throws IOException, InterruptedException {
        for (int page = 1; page <= maxPages && movies.size() < MOVIE_LIMIT; page++) {
            String url = API_ROOT + "/discover/movie?api_key=" + encode(AppConfig.TMDB_API_KEY)
                    + "&primary_release_year=2026&sort_by=vote_average.desc"
                    + "&vote_average.gte=" + minimumRating + "&vote_count.gte=" + minimumVotes
                    + "&include_adult=false&language=en-US&page=" + page;
            Map<?, ?> response = requestObject(url);
            Object results = response.get("results");
            if (!(results instanceof List<?>)) {
                throw new IOException("TMDb response did not contain a movie list.");
            }
            for (Object item : (List<?>) results) {
                Map<?, ?> row = object(item);
                int id = integer(row.get("id"), -1);
                String releaseDate = text(row.get("release_date"));
                double rating = decimal(row.get("vote_average"), 0);
                int votes = integer(row.get("vote_count"), 0);
                if (id <= 0 || !is2026Date(releaseDate) || movies.containsKey(id)
                        || !meetsRatingTier(rating, votes, minimumRating, minimumVotes)) {
                    continue;
                }
                movies.put(id, toMovie(id, row, genres));
                if (movies.size() == MOVIE_LIMIT) {
                    break;
                }
            }

            if (((List<?>) results).isEmpty()) {
                break;
            }
        }
    }

    static boolean meetsRatingTier(double rating, int votes, double minimumRating,
                                   int minimumVotes) {
        return Double.isFinite(rating) && rating >= minimumRating && rating <= 10
                && votes >= minimumVotes;
    }

    private Map<Integer, String> fetchGenres() throws IOException, InterruptedException {
        String url = API_ROOT + "/genre/movie/list?api_key=" + encode(AppConfig.TMDB_API_KEY)
                + "&language=en-US";
        Map<?, ?> response = requestObject(url);
        Map<Integer, String> genres = new LinkedHashMap<>();
        Object list = response.get("genres");
        if (list instanceof List<?>) {
            for (Object item : (List<?>) list) {
                Map<?, ?> row = object(item);
                int id = integer(row.get("id"), -1);
                String name = text(row.get("name"));
                if (id > 0 && !name.isEmpty()) {
                    genres.put(id, name);
                }
            }
        }
        return genres;
    }

    private Map<?, ?> requestObject(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "CineSmart/1.0 (student movie booking app)")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("TMDb request failed (HTTP " + response.statusCode()
                    + "). Check the API key and internet connection.");
        }
        Object parsed;
        try {
            parsed = JsonParser.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IOException("TMDb returned invalid JSON.", e);
        }
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IOException("TMDb returned an unexpected response.");
        }
        return (Map<?, ?>) parsed;
    }

    private static Movie toMovie(int id, Map<?, ?> row, Map<Integer, String> genres) {
        String title = safeField(text(row.get("title")));
        String language = languageName(text(row.get("original_language")));
        String genre = genreNames(row.get("genre_ids"), genres);
        String releaseDate = text(row.get("release_date"));
        String posterPath = text(row.get("poster_path"));
        String poster = posterPath.isEmpty() ? ""
                : "https://image.tmdb.org/t/p/w500" + posterPath;
        Movie movie = new Movie("TMDB_" + id, title, language, genre, 0, "NR", releaseDate,
                decimal(row.get("vote_average"), 0), "", "", safeField(text(row.get("overview"))),
                poster);
        movie.setTotalReviews(integer(row.get("vote_count"), 0));
        return movie;
    }

    private static String genreNames(Object value, Map<Integer, String> genres) {
        if (!(value instanceof List<?>)) {
            return "Not listed";
        }
        List<String> names = new ArrayList<>();
        for (Object idValue : (List<?>) value) {
            int id = integer(idValue, -1);
            String name = genres.get(id);
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? "Not listed" : String.join(", ", names);
    }

    private static String languageName(String code) {
        switch (code.toLowerCase()) {
            case "en": return "English";
            case "hi": return "Hindi";
            case "ta": return "Tamil";
            case "te": return "Telugu";
            case "kn": return "Kannada";
            case "ml": return "Malayalam";
            case "ko": return "Korean";
            case "ja": return "Japanese";
            default: return code.isEmpty() ? "Unknown" : code.toUpperCase();
        }
    }

    private static boolean is2026Date(String date) {
        try {
            return LocalDate.parse(date).getYear() == 2026;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String safeField(String text) {
        return text.replace('|', '/').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<?, ?>) value : Map.of();
    }

    private static String text(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static int integer(Object value, int fallback) {
        try {
            return value instanceof Number ? ((Number) value).intValue()
                    : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double decimal(Object value, double fallback) {
        try {
            return value instanceof Number ? ((Number) value).doubleValue()
                    : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
