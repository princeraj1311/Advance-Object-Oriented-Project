package cinesmart.model;


import cinesmart.util.UiUtil;

/**
 * CastMember is ONE person on the credits of one film - an actor, or a member of the crew.
 *
 * Why a class of its own when Movie already stores a `cast` string? Because a credits screen shows
 * a row of picture cards, and a picture card needs a name, a role and a photograph - three fields
 * per person. Squeezing that into the film line would mean parsing a comma separated string on
 * every repaint, and it would grow without a limit. A film has many people, so the people are
 * their own file (data/cast.txt), exactly like a film has many shows and the shows are theirs.
 *
 * The film's own `cast` and `director` fields are untouched and still used everywhere else, so a
 * movies.txt written before this stage keeps loading and the other screens read the same words.
 *
 * Data line layout (7 columns, all required):
 * M001|CAST|1|Leonardo DiCaprio|Actor|Dom Cobb|https://upload.wikimedia.org/....jpg
 * M001|CREW|1|Christopher Nolan|Director||https://upload.wikimedia.org/....jpg
 *
 * The character name is empty for the crew and for an actor whose part was never named, and the
 * photo address is empty for anyone we have no picture of - {@link #initials()} is drawn then.
 */
public class CastMember {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** How many columns a cast record must have. */
    public static final int FIELD_COUNT = 7;

    /** Which half of the credits a person appears in. */
    public enum Group {
        /** Someone who appears in the film. */
        CAST("Cast"),
        /** Someone who made it: director, producer, writer, cinematographer... */
        CREW("Crew");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Unknown text in a file falls back to CAST rather than breaking the load. */
        public static Group parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Group group : values()) {
                if (group.name().equalsIgnoreCase(wanted)) {
                    return group;
                }
            }
            return CAST;
        }
    }
private String movieId;
    private Group group;
    /** 1 is the first name printed; the credits are shown in this order. */
    private int position;
    private String name;
    /** The job on the film: "Actor", "Director", "Producer"... */
    private String role;
    /** The part an actor plays; empty for the crew and for unnamed parts. */
    private String character;
    /** Photograph address, may be empty - then the tile paints initials. */
    private String photoUrl;

    /** Empty constructor used while a credit is built step by step. */
    public CastMember() {
        this.movieId = "";
        this.group = Group.CAST;
        this.position = 0;
        this.name = "";
        this.role = "";
        this.character = "";
        this.photoUrl = "";
    }

    /** Constructor used by the seeded credits. */
    public CastMember(String movieId, Group group, int position, String name, String role,
                      String character, String photoUrl) {
        this();
        setMovieId(movieId);
        setGroup(group);
        setPosition(position);
        setName(name);
        setRole(role);
        setCharacter(character);
        setPhotoUrl(photoUrl);
    }

    /** Constructor used when one line is read back from data/cast.txt. */
    public CastMember(String[] parts) {
        this();
        setMovieId(part(parts, 0));
        setGroup(Group.parse(part(parts, 1)));
        setPosition(toInt(part(parts, 2), 0));
        setName(part(parts, 3));
        setRole(part(parts, 4));
        setCharacter(part(parts, 5));
        setPhotoUrl(part(parts, 6));
    }
/* ------------------------------------------------------------------ *
     * Behaviour
     * ------------------------------------------------------------------ */

    /** "Leonardo DiCaprio" - a person with no name at all is never shown. */
    public boolean isNamed() {
        return !name.isEmpty();
    }

    /** True when we have an address to fetch, so the tile knows to ask for a photograph. */
    public boolean hasPhoto() {
        return !photoUrl.isEmpty();
    }

    /** True for the actors, false for the crew - what the two credits rows are built from. */
    public boolean isCast() {
        return group == Group.CAST;
    }

    /**
     * The line under the name on the credits tile.
     *
     * An actor is credited with the part they play ("as Dom Cobb"); the crew is credited with the
     * job they did ("Director"). This is the one place that decides, so the home screen, the
     * details dialog and anything added later all read the same words.
     */
    public String subtitle() {
        if (group == Group.CAST && !character.isEmpty()) {
            return "as " + character;
        }
        return role.isEmpty() ? (group == Group.CAST ? "Actor" : group.label()) : role;
    }

    /**
     * Two letters for the placeholder portrait, e.g. "Leonardo DiCaprio" -> "LD".
     *
     * The first two words are used, so an initial-heavy Indian name still reads well
     * ("N. T. Rama Rao Jr." -> "NT"), and a single word falls back to its first two letters.
     */
    public String initials() {
        String clean = name.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        String[] words = clean.split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase();
    }

    /** Credits are shown in this order and the position is what orders them. */
    public int compareTo(CastMember other) {
        return Integer.compare(position, (other == null) ? 0 : other.position);
    }

    @Override
    public String toString() {
        return name + " (" + subtitle() + ")";
    }

    /* ------------------------------------------------------------------ *
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, movieId, group.name(), String.valueOf(position), name,
                role, character, photoUrl);
    }

    /** Factory method: one line of data/cast.txt becomes a CastMember object. */
    public static CastMember fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty cast line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a cast line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new CastMember(parts);
    }
/* ------------------------------------------------------------------ *
     * Getters and setters (the setters clean or repair the input)
     * ------------------------------------------------------------------ */

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = UiUtil.clean(movieId);
    }

    public Group getGroup() {
        return group;
    }

    public final void setGroup(Group group) {
        this.group = (group == null) ? Group.CAST : group;
    }

    public int getPosition() {
        return position;
    }

    public final void setPosition(int position) {
        this.position = Math.max(0, position);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = UiUtil.clean(name);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = UiUtil.clean(role);
    }

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = UiUtil.clean(character);
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    /**
     * Only http and https addresses are kept, exactly like Movie.setPosterUrl, so a typo in the
     * file can never send the downloader somewhere unexpected.
     */
    public void setPhotoUrl(String photoUrl) {
        String clean = UiUtil.clean(photoUrl);
        this.photoUrl = (clean.isEmpty() || !clean.startsWith("http")) ? "" : clean;
    }

    /* ------------------------------------------------------------------ *
     * Small, safe conversions: a damaged file must never crash the program
     * ------------------------------------------------------------------ */

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
