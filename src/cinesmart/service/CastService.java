package cinesmart.service;


import cinesmart.model.CastMember;
import cinesmart.model.Movie;
import cinesmart.util.DataStore;
import cinesmart.util.UiUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CastService answers "who is in this film, and who made it" - the two rows of picture tiles on
 * the movie details screen.
 *
 * It is a service and not a loop inside the panel for the same reason {@link CatalogueService} is:
 * the order of the credits, the fallback when a film has no credits file yet, and the rule about
 * which name counts as "the director" are business rules. A panel that sorted them itself would
 * give a different answer on a second screen.
 *
 * Constructor injection, exactly like the other services, so a test can hand in its own store.
 */
public class CastService {

    private final DataStore store;

    public CastService() {
        this(DataStore.get());
    }

    public CastService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *
     * The two credits rows
     * ------------------------------------------------------------------ */

    /** The actors of a film, in credit order. Never null - an empty list when unknown. */
    public List<CastMember> castOf(Movie movie) {
        return groupOf(movie, CastMember.Group.CAST);
    }

    /** The crew of a film, in credit order: director, producer, writer, cinematographer... */
    public List<CastMember> crewOf(Movie movie) {
        return groupOf(movie, CastMember.Group.CREW);
    }

    /**
     * One half of the credits of one film.
     *
     * An unknown film gives an empty list rather than null, so a screen never has to null-check,
     * and a null movie is treated as "no credits" instead of throwing.
     */
    public List<CastMember> groupOf(Movie movie, CastMember.Group group) {
        List<CastMember> found = new ArrayList<>();
        if (movie == null || group == null) {
            return found;
        }
        String movieId = movie.getMovieId();
        for (CastMember member : store.getCastMembers()) {
            if (member.getGroup() == group && member.getMovieId().equalsIgnoreCase(movieId)
                    && member.isNamed()) {
                found.add(member);
            }
        }
        found.sort((first, second) -> first.compareTo(second));
        return Collections.unmodifiableList(found);
    }

    /** The first {@code limit} credits of a group - how many tiles fit on one row. */
    public List<CastMember> firstOf(Movie movie, CastMember.Group group, int limit) {
        List<CastMember> all = groupOf(movie, group);
        if (limit > 0 && all.size() > limit) {
            return Collections.unmodifiableList(new ArrayList<>(all.subList(0, limit)));
        }
        return all;
    }

    /* ------------------------------------------------------------------ *
     * Reading one name off the credits
     * ------------------------------------------------------------------ */

    /**
     * The director of a film, from the crew row when we have one.
     *
     * The film's own director field is the fallback, so a film whose credits were never seeded
     * still shows the right name - it simply shows no photograph and no job card.
     */
    public String directorOf(Movie movie) {
        if (movie == null) {
            return "";
        }
        for (CastMember member : crewOf(movie)) {
            if ("director".equalsIgnoreCase(member.getRole())) {
                return member.getName();
            }
        }
        return UiUtil.clean(movie.getDirector());
    }

    /** The producer of a film, or an empty string when the credits do not name one. */
    public String producerOf(Movie movie) {
        for (CastMember member : crewOf(movie)) {
            if ("producer".equalsIgnoreCase(member.getRole())) {
                return member.getName();
            }
        }
        return "";
    }

    /** Every other film this person appears in - what a tile tooltip shows. */
    public List<Movie> otherMoviesOf(CastMember member, Movie except) {
        List<Movie> films = new ArrayList<>();
        if (member == null) {
            return films;
        }
        String wanted = UiUtil.clean(member.getName()).toLowerCase();
        if (wanted.isEmpty()) {
            return films;
        }
        for (Movie movie : store.getMovies()) {
            boolean skip = (except != null) && except.getMovieId().equals(movie.getMovieId());
            if (!skip && (movie.getCast().toLowerCase().contains(wanted)
                    || movie.getDirector().toLowerCase().contains(wanted))) {
                films.add(movie);
            }
        }
        return Collections.unmodifiableList(films);
    }
}
