package cinesmart.util;

import cinesmart.model.Admin;
import cinesmart.model.CastMember;
import cinesmart.model.Customer;
import cinesmart.model.Booking;
import cinesmart.model.FoodOrder;
import cinesmart.model.GiftCard;
import cinesmart.model.Locality;
import cinesmart.model.MenuItem;
import cinesmart.model.Movie;
import cinesmart.model.Notification;
import cinesmart.model.Offer;
import cinesmart.model.Review;
import cinesmart.model.SeatHold;
import cinesmart.model.ShowSchedule;
import cinesmart.model.ShowTime;
import cinesmart.model.Theatre;
import cinesmart.model.User;
import cinesmart.model.CinemaHall;
import cinesmart.model.CinemaVenue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.Random;
import java.util.function.Function;

/**
 * DataStore is the ONLY class that touches the hard disk.
 *
 * The rest of the program asks for objects ("give me the user called demo"), so the storage
 * technology can be replaced later: to move to SQLite you keep the same public methods and
 * swap the file reading code for JDBC statements. Nothing else in the project changes.
 *
 * Files created at run time (inside the project folder):
 *   data/users.txt            one user per line, password stored as salt + SHA-256 hash
 *   data/settings.properties  remember-me user, selected city, accent colour
 *   data/movies.txt           one film per line       (Stage 3 catalogue)
 *   data/theatres.txt         one cinema per line    (Stage 3 catalogue)
 *   data/showtimes.txt        one screening per line (Stage 3 catalogue)
 *   data/cinema_venues.txt    public OpenStreetMap cinema listings (informational)
 *   data/bookings.txt         one purchase per line  (Stage 5 + Stage 6)
 */
public final class DataStore {

    /** First line of users.txt - proves the file was written by us and is not truncated. */
    public static final String FILE_HEADER = "#CineSmart-Users-v1";
    /** Same idea for the Stage 3 catalogue files. */
    public static final String MOVIE_HEADER = "#CineSmart-Movies-v1";
    public static final String THEATRE_HEADER = "#CineSmart-Theatres-v1";
    public static final String SHOWTIME_HEADER = "#CineSmart-ShowTimes-v1";
    /** Stage 11: one schedule template per line, used by the rolling show generator. */
    public static final String SHOWSCHEDULE_HEADER = "#CineSmart-ShowSchedules-v1";
    /** Feature 3: one notification per line. */
    public static final String NOTIFICATION_HEADER = "#CineSmart-Notifications-v1";
    /** Feature 1: one temporary seat hold per line. */
    public static final String SEAT_HOLD_HEADER = "#CineSmart-SeatHolds-v1";
    /** Stage 5: one line per purchase, so a booking survives closing the program. */
    public static final String BOOKING_HEADER = "#CineSmart-Bookings-v1";
    /** Stage 10: the food menu of every hall, one line per item. */
    public static final String CANTEEN_HEADER = "#CineSmart-Canteen-v1";
    /** Stage 10: one line per food order the customer placed. */
    public static final String FOOD_ORDER_HEADER = "#CineSmart-FoodOrders-v1";
    /** The searchable catalogue of cities and areas, with the coordinates of each. */
    public static final String LOCALITY_HEADER = "#CineSmart-Localities-v1";
    /** One line per running offer, including which customers already used each code. */
    public static final String OFFER_HEADER = "#CineSmart-Offers-v1";
    /** One line per gift card ever sold. */
    public static final String GIFT_CARD_HEADER = "#CineSmart-GiftCards-v1";
    /** One line per credited person of a film: the actors and the crew, with a photo address. */
    public static final String CAST_HEADER = "#CineSmart-Cast-v1";
    /** One registered cinema hall per line. */
    public static final String HALL_HEADER = "#CineSmart-Halls-v1";
    /** One line per review of a film: customer comments and published critic reviews. */
    public static final String REVIEW_HEADER = "#CineSmart-Reviews-v1";
    /** Informational cinema listings sourced from OpenStreetMap. */
    public static final String CINEMA_VENUE_HEADER = "#CineSmart-OSM-Cinemas-v1";

    /**
     * A portrait for every seeded person we have a picture of, keyed by their name.
     *
     * Wikimedia is used for the same reason the posters come from there: the pictures are free
     * to redistribute and they are served over https, which is all {@link PosterLoader} needs.
     * They are downloaded once, cached in data/posters, and shown from the cache afterwards, so
     * the credits screen also works with no internet at all.
     *
     * Anyone missing from this map simply gets the tile with their initials painted on it - the
     * map exists to make the seeded data look right, not to be required for the screen to work.
     *
     * Declared BEFORE INSTANCE on purpose: the constructor seeds the credits, and a static field
     * initialised after INSTANCE would still be null at that moment.
     */
    private static final Map<String, String> PORTRAITS = Map.ofEntries(
                Map.entry("Aaron Eckhart", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Aaron_Eckhart_%2829830286295%29_%28cropped%29.jpg/330px-Aaron_Eckhart_%2829830286295%29_%28cropped%29.jpg"),
                Map.entry("Alia Bhatt", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Alia_Bhatt_attends_at_the_2026_Cannes_Film_Festival_%28cropped%29_%28cropped%29.jpg/330px-Alia_Bhatt_attends_at_the_2026_Cannes_Film_Festival_%28cropped%29_%28cropped%29.jpg"),
                Map.entry("Amritha Aiyer", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bf/Amritha_Aiyer_at_HanuMan_gratitude_meet_%281%29.jpg/330px-Amritha_Aiyer_at_HanuMan_gratitude_meet_%281%29.jpg"),
                Map.entry("Andrew Garfield", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/48/Andrew_Garfield_After_The_Hunt-22_%28cropped%29.jpg/330px-Andrew_Garfield_After_The_Hunt-22_%28cropped%29.jpg"),
                Map.entry("Anne Hathaway", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a5/AnneHathaway-byPhilipRomano-Crop.jpg/330px-AnneHathaway-byPhilipRomano-Crop.jpg"),
                Map.entry("Anushka Shetty", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Anushka_Shetty_first_look_Baahubali_2_%28cropped%29.jpg/330px-Anushka_Shetty_first_look_Baahubali_2_%28cropped%29.jpg"),
                Map.entry("Austin Butler", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/Austin_Butler_at_the_2025_Cannes_Film_Festival_02.jpg/330px-Austin_Butler_at_the_2025_Cannes_Film_Festival_02.jpg"),
                Map.entry("Benedict Cumberbatch", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Benedict_Cumberbatch-67555.jpg/330px-Benedict_Cumberbatch-67555.jpg"),
                Map.entry("Bob Persichetti", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Bob_Persichetti_by_Gage_Skidmore.jpg/330px-Bob_Persichetti_by_Gage_Skidmore.jpg"),
                Map.entry("Bonnie Hunt", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Bonnie_Hunt_in_2010.jpg/330px-Bonnie_Hunt_in_2010.jpg"),
                Map.entry("Bruce Willis", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c4/Bruce_Willis_by_Gage_Skidmore_3.jpg/330px-Bruce_Willis_by_Gage_Skidmore_3.jpg"),
                Map.entry("Chris Evans", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d5/Chris_Evans_at_the_2025_Toronto_International_Film_Festival_%28cropped%29.jpg/330px-Chris_Evans_at_the_2025_Toronto_International_Film_Festival_%28cropped%29.jpg"),
                Map.entry("Chris Hemsworth", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/86/Chris_Hemsworth_-_Crime_101.jpg/330px-Chris_Hemsworth_-_Crime_101.jpg"),
                Map.entry("Christian Bale", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0a/Christian_Bale-7837.jpg/330px-Christian_Bale-7837.jpg"),
                Map.entry("Christopher Markus", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f2/Christopher_Markus_%26_Stephen_McFeely_%2848385355446%29.jpg/330px-Christopher_Markus_%26_Stephen_McFeely_%2848385355446%29.jpg"),
                Map.entry("Christopher Nolan", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b2/ChristopherNolan-byPhilipRomano_%28cropped%29.jpg/330px-ChristopherNolan-byPhilipRomano_%28cropped%29.jpg"),
                Map.entry("Cillian Murphy", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ed/Cillian_Murphy_at_the_London_premier_of_Steve_in_September_2025_%28cropped%29.jpg/330px-Cillian_Murphy_at_the_London_premier_of_Steve_in_September_2025_%28cropped%29.jpg"),
                Map.entry("D. V. V. Danayya", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c0/Ramaiya_vastavaiya_audio_launch_photos_2442c23.jpg/330px-Ramaiya_vastavaiya_audio_launch_photos_2442c23.jpg"),
                Map.entry("David Morse", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/David_Morse_%282015%29.jpg/330px-David_Morse_%282015%29.jpg"),
                Map.entry("David S. Goyer", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/David_Goyer.jpg/330px-David_Goyer.jpg"),
                Map.entry("Denis Villeneuve", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/DVilleneuveRFH121024_%2812_of_23%29_%2854061976489%29_%28cropped%29.jpg/330px-DVilleneuveRFH121024_%2812_of_23%29_%2854061976489%29_%28cropped%29.jpg"),
                Map.entry("Elizabeth Olsen", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/ElizabethOlsen-TIFF2025-02_%28cropped%29.png/330px-ElizabethOlsen-TIFF2025-02_%28cropped%29.png"),
                Map.entry("Elliot Page", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3b/ElliotPage-byPhilipRomano2.jpg/330px-ElliotPage-byPhilipRomano2.jpg"),
                Map.entry("Emily Blunt", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/45/Emily_Blunt_at_WWD_Style_Awards_2026-02.jpg/330px-Emily_Blunt_at_WWD_Style_Awards_2026-02.jpg"),
                Map.entry("Florence Pugh", "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9e/Florence_Pugh_at_the_2024_Toronto_International_Film_Festival_13_%28cropped_2_%E2%80%93_color_adjusted%29.jpg/330px-Florence_Pugh_at_the_2024_Toronto_International_Film_Festival_13_%28cropped_2_%E2%80%93_color_adjusted%29.jpg"),
                Map.entry("Frank Darabont", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Frank_Darabont_at_the_PaleyFest_2011_-_The_Walking_Dead_panel.jpg/330px-Frank_Darabont_at_the_PaleyFest_2011_-_The_Walking_Dead_panel.jpg"),
                Map.entry("Gary Oldman", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Gary_Oldman_%2813925515511%29_%28cropped%29.jpg/330px-Gary_Oldman_%2813925515511%29_%28cropped%29.jpg"),
                Map.entry("Greig Fraser", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/78/Greig_Fraser.jpg/330px-Greig_Fraser.jpg"),
                Map.entry("Heath Ledger", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Heath_Ledger_%282%29.jpg/330px-Heath_Ledger_%282%29.jpg"),
                Map.entry("Jamie Foxx", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/TIFF_2019_jamie_foxx_%28cropped%29.jpg/330px-TIFF_2019_jamie_foxx_%28cropped%29.jpg"),
                Map.entry("Javier Bardem", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d0/Javier_Bardem_-_Bunker_-_TIFF_2026-12.jpg/330px-Javier_Bardem_-_Bunker_-_TIFF_2026-12.jpg"),
                Map.entry("Jeffrey Ford", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7e/Jeffrey_Ford_at_Readercon.jpg/330px-Jeffrey_Ford_at_Readercon.jpg"),
                Map.entry("Jessica Chastain", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/11/Jessica_Chastain-64631_%28cropped%29.jpg/330px-Jessica_Chastain-64631_%28cropped%29.jpg"),
                Map.entry("John Travolta", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7d/John_Travolta_in_2024_%28cropped%29.jpg/330px-John_Travolta_in_2024_%28cropped%29.jpg"),
                Map.entry("Jon Watts", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/Jon_Watts_by_Gage_Skidmore_2.jpg/330px-Jon_Watts_by_Gage_Skidmore_2.jpg"),
                Map.entry("Jonathan Nolan", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/Jonathan_Nolan_at_SXSW_2024.jpg/330px-Jonathan_Nolan_at_SXSW_2024.jpg"),
                Map.entry("Joseph Gordon-Levitt", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Joseph_Gordon_Levitt_Sundance_Film_Festival_2026_%28cropped%29.jpg/330px-Joseph_Gordon_Levitt_Sundance_Film_Festival_2026_%28cropped%29.jpg"),
                Map.entry("Ken Watanabe", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/Ken_Watanabe_20220615.jpg/330px-Ken_Watanabe_20220615.jpg"),
                Map.entry("Kenneth Branagh", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/Kenneth_Branagh_portrait_at_TIFF_2026.jpg/330px-Kenneth_Branagh_portrait_at_TIFF_2026.jpg"),
                Map.entry("Kevin Feige", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/Kevin_Feige_by_Gage_Skidmore.jpg/330px-Kevin_Feige_by_Gage_Skidmore.jpg"),
                Map.entry("Lawrence Bender", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/LawrenceBenderIBAug09.jpg/330px-LawrenceBenderIBAug09.jpg"),
                Map.entry("Leonardo DiCaprio", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/LeoPTABFI191125-28_%28cropped%29.jpg/330px-LeoPTABFI191125-28_%28cropped%29.jpg"),
                Map.entry("Mackenzie Foy", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Mackenzie_Foy.jpg/330px-Mackenzie_Foy.jpg"),
                Map.entry("Maggie Gyllenhaal", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/Maggie_Gyllenhaal_at_the_83rd_Venice_International_Film_Festival.jpg/330px-Maggie_Gyllenhaal_at_the_83rd_Venice_International_Film_Festival.jpg"),
                Map.entry("Marion Cotillard", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Marion_Cotillard-1061.jpg/330px-Marion_Cotillard-1061.jpg"),
                Map.entry("Mark Ruffalo", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/11/Mark_Ruffalo_%2836201774756%29_%28cropped%29.jpg/330px-Mark_Ruffalo_%2836201774756%29_%28cropped%29.jpg"),
                Map.entry("Matt Damon", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/MattDamon-byPhilipRomano2.jpg/330px-MattDamon-byPhilipRomano2.jpg"),
                Map.entry("Matthew McConaughey", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e2/Matthew_McConaughey_2022.jpg/330px-Matthew_McConaughey_2022.jpg"),
                Map.entry("Michael Caine", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/Michael_Caine_-_Viennale_2012_g_%28cropped%29.jpg/330px-Michael_Caine_-_Viennale_2012_g_%28cropped%29.jpg"),
                Map.entry("Michael Clarke Duncan", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/78/MClarkeDuncan021109-R106_%2850094589037%29.jpg/330px-MClarkeDuncan021109-R106_%2850094589037%29.jpg"),
                Map.entry("N. T. Rama Rao Jr.", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/NTR_Jr._%282026%29.jpg/330px-NTR_Jr._%282026%29.jpg"),
                Map.entry("Nayanthara", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b7/Nayanthara_during_toxic_album_launch_event.jpg/330px-Nayanthara_during_toxic_album_launch_event.jpg"),
                Map.entry("Prabhas", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/Prabhas_by_Gage_Skidmore.jpg/330px-Prabhas_by_Gage_Skidmore.jpg"),
                Map.entry("Quentin Tarantino", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/Quentin_Tarantino_by_Gage_Skidmore.jpg/330px-Quentin_Tarantino_by_Gage_Skidmore.jpg"),
                Map.entry("R. Madhavan", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/Madhavan_Saala_Khadoos_2_%28cropped%29.jpg/330px-Madhavan_Saala_Khadoos_2_%28cropped%29.jpg"),
                Map.entry("R. Parthiban", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/25/R._Parthiepan_62nd_Filmfare_Awards_South.jpg/330px-R._Parthiepan_62nd_Filmfare_Awards_South.jpg"),
                Map.entry("R. R. Venkat", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Rrvenkatproducer.jpg/330px-Rrvenkatproducer.jpg"),
                Map.entry("Ram Charan", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/Ram_Charan_at_Game_Changer_trailer_launch.jpg/330px-Ram_Charan_at_Game_Changer_trailer_launch.jpg"),
                Map.entry("Rana Daggubati", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/Rana_Daggubati_by_Gage_Skidmore.jpg/330px-Rana_Daggubati_by_Gage_Skidmore.jpg"),
                Map.entry("Rebecca Ferguson", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/Rebecca_Ferguson_A_House_of_Dynamite-67_%28cropped2%29.jpg/330px-Rebecca_Ferguson_A_House_of_Dynamite-67_%28cropped2%29.jpg"),
                Map.entry("Robert Downey Jr.", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/RobertDowneyJr-byPhilipRomano7_%28cropped%29.jpg/330px-RobertDowneyJr-byPhilipRomano7_%28cropped%29.jpg"),
                Map.entry("Roger Deakins", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/RogDeakinsBFI120921_%2815_of_17%29_%2851473305585%29-c%2Bedit.jpg/330px-RogDeakinsBFI120921_%2815_of_17%29_%2851473305585%29-c%2Bedit.jpg"),
                Map.entry("S. S. Rajamouli", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/SS_Rajamouli%2C_2021.jpg/330px-SS_Rajamouli%2C_2021.jpg"),
                Map.entry("Samuel L. Jackson", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f7/Samuel_L._Jackson_attending_the_World_premier_of_%22Argylle%22_in_London_%2C_January_2024_%28cropped2%29.jpg/330px-Samuel_L._Jackson_attending_the_World_premier_of_%22Argylle%22_in_London_%2C_January_2024_%28cropped2%29.jpg"),
                Map.entry("Scarlett Johansson", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Scarlett_Johansson-8588.jpg/330px-Scarlett_Johansson-8588.jpg"),
                Map.entry("Stephen McFeely", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f2/Christopher_Markus_%26_Stephen_McFeely_%2848385355446%29.jpg/330px-Christopher_Markus_%26_Stephen_McFeely_%2848385355446%29.jpg"),
                Map.entry("Sumanth", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Sumanth_kumar_.jpg/330px-Sumanth_kumar_.jpg"),
                Map.entry("Teja Sajja", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/be/Teja_Sajja_at_IIFA_Utsavam_%28cropped%29.jpg/330px-Teja_Sajja_at_IIFA_Utsavam_%28cropped%29.jpg"),
                Map.entry("Thelma Schoonmaker", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Thelma_Schoonmaker_%2853274434525%29_%28cropped%29.jpg/330px-Thelma_Schoonmaker_%2853274434525%29_%28cropped%29.jpg"),
                Map.entry("Tim Roth", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Tim_Roth_by_Gage_Skidmore_2.jpg/330px-Tim_Roth_by_Gage_Skidmore_2.jpg"),
                Map.entry("Timothy Hutton", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/TimothyHutton08TIFF.jpg/330px-TimothyHutton08TIFF.jpg"),
                Map.entry("Timothée Chalamet", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5c/Timoth%C3%A9e_Chalamet-63482_%28cropped%29.jpg/330px-Timoth%C3%A9e_Chalamet-63482_%28cropped%29.jpg"),
                Map.entry("Tom Hanks", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/39/TomHanksPrincEdw031223_%2811_of_41%29_%28cropped%29.jpg/330px-TomHanksPrincEdw031223_%2811_of_41%29_%28cropped%29.jpg"),
                Map.entry("Tom Hardy", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Tom_Hardy_Locke_Premiere.jpg/330px-Tom_Hardy_Locke_Premiere.jpg"),
                Map.entry("Tom Holland", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/TomHolland-byPhilipRomano.jpg/330px-TomHolland-byPhilipRomano.jpg"),
                Map.entry("Uma Thurman", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/68/UmaThurman-byPhilipRomano.jpg/330px-UmaThurman-byPhilipRomano.jpg"),
                Map.entry("Vijay Varma", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Vijay_Varma_at_the_Filmfare_Glamour_and_Style_Awards_2025_%28cropped%29.jpg/330px-Vijay_Varma_at_the_Filmfare_Glamour_and_Style_Awards_2025_%28cropped%29.jpg"),
                Map.entry("Ving Rhames", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5c/Ving_Rhames_2010_%284710601891%29_%28cropped%29.jpg/330px-Ving_Rhames_2010_%284710601891%29_%28cropped%29.jpg"),
                Map.entry("Wally Pfister", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/46th_ICFF_%E2%80%9CManaki_Brothers%E2%80%9D_-_Wally_Pfister_%28director_and_cinematographer%29.jpg/330px-46th_ICFF_%E2%80%9CManaki_Brothers%E2%80%9D_-_Wally_Pfister_%28director_and_cinematographer%29.jpg"),
                Map.entry("Willem Dafoe", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Willem_Dafoe_at_78th_Locarno_Film_Festival_%28cropped%29.jpg/330px-Willem_Dafoe_at_78th_Locarno_Film_Festival_%28cropped%29.jpg"),
                Map.entry("Zendaya", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Zendaya-byPhilipRomano.jpg/330px-Zendaya-byPhilipRomano.jpg")
            );

    /** Singleton: one object = one open view of the data (created when first used). */
    private static final DataStore INSTANCE = new DataStore();

    public static DataStore get() {
        return INSTANCE;
    }

    private final File dataFolder;
    private final File usersFile;
    private final File settingsFile;
    private final File moviesFile;
    private final File theatresFile;
    private final File showTimesFile;
    private final File showSchedulesFile;
    private final File seatHoldsFile;
    private final File notificationsFile;
    private final File bookingsFile;
    private final File canteenFile;
    private final File foodOrdersFile;
    private final File localitiesFile;
    private final File offersFile;
    private final File giftCardsFile;
    private final File castFile;
    private final File reviewsFile;
    private final File hallsFile;
    private final File cinemaVenuesFile;

    /** In-memory copy of the users (a Collection used for lookups, sorting and counting). */
    private final List<User> users = new ArrayList<>();


    /** In-memory copies of the Stage 3 catalogue. */
    private final List<Movie> movies = new ArrayList<>();
    private final List<Theatre> theatres = new ArrayList<>();
    private final List<ShowTime> showTimes = new ArrayList<>();
    /** Stage 11: schedule templates behind the rolling show generator. */
    private final List<ShowSchedule> showSchedules = new ArrayList<>();
    /** Feature 1: live temporary seat holds. */
    private final List<SeatHold> seatHolds = new ArrayList<>();
    /** Feature 3: in-app notifications. */
    private final List<Notification> notifications = new ArrayList<>();
    /** Stage 5/6: the purchases, newest first is the order the screens show them in. */
    private final List<Booking> bookings = new ArrayList<>();
    /** Stage 10: the menu items of every hall, and the food orders that were placed. */
    private final List<MenuItem> menuItems = new ArrayList<>();
    private final List<FoodOrder> foodOrders = new ArrayList<>();
    /** The place catalogue behind "detect my location" and the city picker. */
    private final List<Locality> localities = new ArrayList<>();
    /** The running offers, and the gift cards that were sold. */
    private final List<Offer> offers = new ArrayList<>();
    private final List<GiftCard> giftCards = new ArrayList<>();
    /** The credited people of every film: the cast rows and the crew rows of the details screen. */
    private final List<CastMember> castMembers = new ArrayList<>();
    /** What readers and critics wrote about each film. */
    private final List<Review> reviews = new ArrayList<>();
    /** Registered cinema halls awaiting approval or already approved. */
    private final List<CinemaHall> halls = new ArrayList<>();
    /** Informational public cinema directory; separate from bookable local theatre inventory. */
    private final List<CinemaVenue> cinemaVenues = new ArrayList<>();
    private final Properties settings = new Properties();

    private DataStore() {
        dataFolder = new File(System.getProperty("user.dir"), "data");
        usersFile = new File(dataFolder, "users.txt");
        settingsFile = new File(dataFolder, "settings.properties");
        moviesFile = new File(dataFolder, "movies.txt");
        theatresFile = new File(dataFolder, "theatres.txt");
        showTimesFile = new File(dataFolder, "showtimes.txt");
        showSchedulesFile = new File(dataFolder, "showschedules.txt");
        seatHoldsFile = new File(dataFolder, "seat_holds.txt");
        notificationsFile = new File(dataFolder, "notifications.txt");
        bookingsFile = new File(dataFolder, "bookings.txt");
        canteenFile = new File(dataFolder, "canteen.txt");
        foodOrdersFile = new File(dataFolder, "foodorders.txt");
        localitiesFile = new File(dataFolder, "localities.txt");
        offersFile = new File(dataFolder, "offers.txt");
        giftCardsFile = new File(dataFolder, "giftcards.txt");
        castFile = new File(dataFolder, "cast.txt");
        reviewsFile = new File(dataFolder, "reviews.txt");
        hallsFile = new File(dataFolder, "cinesmart_halls.txt");
        cinemaVenuesFile = new File(dataFolder, "cinema_venues.txt");
        createFolders();
        loadUsers();
        seedAccountsIfEmpty();
        printAdminSignInIds();
        loadSettings();
        loadCatalogue();
        loadShowSchedules();
        loadSeatHolds();
        loadNotifications();
        loadBookings();
        loadCanteen();
        loadFoodOrders();
        loadOffers();
        loadGiftCards();
        loadLocalities();
        loadCast();
        loadReviews();
        loadHalls();
        loadCinemaVenues();
        seedCatalogueIfEmpty();
        seedCanteenIfEmpty();
        seedLocalitiesIfEmpty();
        seedOffersIfEmpty();
        seedCastIfEmpty();
        seedReviewsIfEmpty();
    }
    private void loadHalls() {
        readCatalogFile(hallsFile, halls, CinemaHall::fromDataLine);
    }
    /* ------------------------------------------------------------------ *
     * User access used by UserService
     * ------------------------------------------------------------------ */

    /** All users. unmodifiable = callers cannot change our list by mistake. */
    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    /** Case-insensitive lookup, or null when the user name is not registered. */
    public User findUser(String userName) {
        String wanted = ValidationUtil.clean(userName).toLowerCase();
        for (User user : users) {
            if (user.getUserName().equals(wanted)) {
                return user;
            }
        }
        return null;
    }

    public boolean userNameExists(String userName) {
        return findUser(userName) != null;
    }

    /** E-mail uniqueness check (the current user's own record is ignored). */
    public boolean emailExists(String email, String ownUserName) {
        String wanted = ValidationUtil.clean(email).toLowerCase();
        String own = ValidationUtil.clean(ownUserName).toLowerCase();
        for (User user : users) {
            if (user.getEmail().equalsIgnoreCase(wanted)
                    && !user.getUserName().equals(own)) {
                return true;
            }
        }
        return false;
    }

    /** Adds a new user to the list and to the file. Returns false when it already exists. */
    public synchronized boolean addUser(User user) {
        if (user == null || userNameExists(user.getUserName())
                || emailExists(user.getEmail(), user.getUserName())) {
            return false;
        }
        if (user instanceof Customer) {
            for (User existing : users) {
                if (existing instanceof Customer
                        && existing.getPhone().equals(((Customer) user).getPhone())) {
                    return false;
                }
            }
        }
        if (user instanceof Admin) {
            for (User existing : users) {
                if (existing instanceof Admin && ((Admin) existing).getAdminId()
                        .equals(((Admin) user).getAdminId())) {
                    return false;
                }
            }
        }
        users.add(user);
        return saveUsers();
    }

    /** Replaces the stored copy of an existing user (used by Profile / Settings). */
    public synchronized boolean updateUser(User user) {
        if (user == null) {
            return false;
        }
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserName().equals(user.getUserName())) {
                users.set(i, user);
                return saveUsers();
            }
        }
        return false;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    /* ------------------------------------------------------------------ *
     * Settings (remember-me, city, accent colour)
     * ------------------------------------------------------------------ */

    public Properties settings() {
        return settings;
    }

    public String setting(String key, String fallback) {
        String value = settings.getProperty(key);
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    public synchronized void saveSettings() {
        try (FileWriter writer = new FileWriter(settingsFile)) {
            settings.store(writer, "CineSmart settings");
        } catch (Exception e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    private void loadSettings() {
        settings.setProperty("rememberMe", "false");
        settings.setProperty("rememberedUserName", "");
        settings.setProperty("city", Session.CITIES[0]);
        settings.setProperty("accentColor", "1B6EF3");
        try (FileReader reader = new FileReader(settingsFile)) {
            settings.load(reader); // missing file simply leaves the defaults above
        } catch (Exception e) {
            // First run: the file does not exist yet, so defaults are expected.
        }
        Session.setRememberMe(Boolean.parseBoolean(setting("rememberMe", "false")));
        Session.setRememberedUserName(setting("rememberedUserName", ""));
        Session.setSelectedCity(setting("city", Session.CITIES[0]));
        // The detected / chosen area, so the app opens where the customer left it.
        Session.setSavedLocalityId(setting("localityId", ""));
        Session.setSavedLocalityName(setting("locality", ""));
        applyAccentFromSettings();
    }

    /* ------------------------------------------------------------------ *
     * Catalogue access used by CatalogueService (Stage 2 + Stage 3)
     * ------------------------------------------------------------------ */

    public List<Movie> getMovies() {
        return Collections.unmodifiableList(movies);
    }

    /** The currently cached TMDb records. */
    public List<Movie> getRemoteMovies() {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie.getMovieId().startsWith("TMDB_")) {
                result.add(movie);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Replaces only the live TMDb slice, leaving manually managed films and their bookings intact. */
    public synchronized boolean replaceRemoteMovies(List<Movie> refreshedMovies) {
        if (refreshedMovies == null || refreshedMovies.isEmpty()) {
            return false;
        }
        Map<String, Integer> bookingsByMovie = new java.util.HashMap<>();
        for (Movie movie : movies) {
            if (movie.getMovieId().startsWith("TMDB_")) {
                bookingsByMovie.put(movie.getMovieId(), movie.getTimesBooked());
            }
        }
        movies.removeIf(movie -> movie.getMovieId().startsWith("TMDB_"));
        for (Movie movie : refreshedMovies) {
            if (movie != null && movie.getMovieId().startsWith("TMDB_")) {
                Integer previousBookings = bookingsByMovie.get(movie.getMovieId());
                if (previousBookings != null) {
                    movie.setTimesBooked(previousBookings);
                }
                movies.add(movie);
            }
        }
        return saveMovies();
    }

    public List<CinemaVenue> getCinemaVenues() {
        return Collections.unmodifiableList(cinemaVenues);
    }

    public List<CinemaVenue> cinemaVenuesIn(String city) {
        List<CinemaVenue> result = new ArrayList<>();
        for (CinemaVenue venue : cinemaVenues) {
            if (venue.getCity().equalsIgnoreCase(ValidationUtil.clean(city))) {
                result.add(venue);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Replaces the cached public directory after a successful complete OSM refresh. */
    public synchronized boolean replaceCinemaVenues(List<CinemaVenue> venues) {
        if (venues == null) {
            return false;
        }
        cinemaVenues.clear();
        Set<String> seen = new LinkedHashSet<>();
        for (CinemaVenue venue : venues) {
            if (venue != null && !venue.getVenueId().isEmpty() && seen.add(venue.getVenueId())) {
                cinemaVenues.add(venue);
            }
        }
        return saveCinemaVenues();
    }

    public List<Theatre> getTheatres() {
        return Collections.unmodifiableList(theatres);
    }

    public List<ShowTime> getShowTimes() {
        return Collections.unmodifiableList(showTimes);
    }

    /** Lookup by id, or null. The ids come from the data files, never from typed text. */
    public Movie findMovie(String movieId) {
        for (Movie movie : movies) {
            if (movie.getMovieId().equalsIgnoreCase(String.valueOf(movieId))) {
                return movie;
            }
        }
        return null;
    }

    public Theatre findTheatre(String theatreId) {
        for (Theatre theatre : theatres) {
            if (theatre.getTheatreId().equalsIgnoreCase(String.valueOf(theatreId))) {
                return theatre;
            }
        }
        return null;
    }

    public ShowTime findShowTime(String showId) {
        for (ShowTime showTime : showTimes) {
            if (showTime.getShowId().equalsIgnoreCase(String.valueOf(showId))) {
                return showTime;
            }
        }
        return null;
    }

    /* ---- Changes made by the admin screens (Stage 8) or by booking (Stage 4/5) ---- */

    public synchronized boolean addMovie(Movie movie) {
        if (movie == null || findMovie(movie.getMovieId()) != null) {
            return false;
        }
        movies.add(movie);
        return saveMovies();
    }

    public synchronized boolean updateMovie(Movie movie) {
        int at = indexOfMovie(movie == null ? null : movie.getMovieId());
        if (at < 0) {
            return false;
        }
        movies.set(at, movie);
        return saveMovies();
    }

    /**
     * Deleting a film also deletes its show times: that is called "referential integrity" and
     * it is the reason a show stores a movieId instead of a copy of the film.
     */
    public synchronized boolean deleteMovie(String movieId) {
        int at = indexOfMovie(movieId);
        if (at < 0) {
            return false;
        }
        movies.remove(at);
        removeShowTimesOf(movieId);
        removeCreditsOf(movieId);
        return saveMovies() && saveShowTimes();
    }

    public synchronized boolean addTheatre(Theatre theatre) {
        if (theatre == null || findTheatre(theatre.getTheatreId()) != null) {
            return false;
        }
        theatres.add(theatre);
        return saveTheatres();
    }

    public synchronized boolean updateTheatre(Theatre theatre) {
        int at = indexOfTheatre(theatre == null ? null : theatre.getTheatreId());
        if (at < 0) {
            return false;
        }
        theatres.set(at, theatre);
        return saveTheatres();
    }

    public synchronized boolean deleteTheatre(String theatreId) {
        int at = indexOfTheatre(theatreId);
        if (at < 0) {
            return false;
        }
        theatres.remove(at);
        for (int i = showTimes.size() - 1; i >= 0; i--) {
            if (showTimes.get(i).getTheatreId().equalsIgnoreCase(theatreId)) {
                showTimes.remove(i);
            }
        }
        return saveTheatres() && saveShowTimes();
    }

    public synchronized boolean addShowTime(ShowTime showTime) {
        if (showTime == null || findMovie(showTime.getMovieId()) == null
                || findTheatre(showTime.getTheatreId()) == null
                || findShowTime(showTime.getShowId()) != null) {
            return false; // a show must point at a film AND a cinema that really exist
        }
        showTimes.add(showTime);
        return saveShowTimes();
    }

    /** Used by Stage 4/5 when seats are taken: only the booked count changes. */
    public synchronized boolean updateShowTime(ShowTime showTime) {
        int at = indexOfShowTime(showTime == null ? null : showTime.getShowId());
        if (at < 0) {
            return false;
        }
        showTimes.set(at, showTime);
        return saveShowTimes();
    }

    /* ---- Stage 11: schedule templates (rolling show generator) ---- */

    public List<ShowSchedule> getShowSchedules() {
        return Collections.unmodifiableList(showSchedules);
    }

    public ShowSchedule findShowSchedule(String scheduleId) {
        for (ShowSchedule schedule : showSchedules) {
            if (schedule.getScheduleId().equalsIgnoreCase(String.valueOf(scheduleId))) {
                return schedule;
            }
        }
        return null;
    }

    public synchronized boolean addShowSchedule(ShowSchedule schedule) {
        if (schedule == null || findShowSchedule(schedule.getScheduleId()) != null) {
            return false;
        }
        showSchedules.add(schedule);
        return saveShowSchedules();
    }

    public synchronized boolean updateShowSchedule(ShowSchedule schedule) {
        if (schedule == null) {
            return false;
        }
        for (int i = 0; i < showSchedules.size(); i++) {
            if (showSchedules.get(i).getScheduleId().equalsIgnoreCase(schedule.getScheduleId())) {
                showSchedules.set(i, schedule);
                return saveShowSchedules();
            }
        }
        return false;
    }

    public synchronized boolean saveShowSchedules() {
        return writeCatalogFile(showSchedulesFile, SHOWSCHEDULE_HEADER, showSchedules,
                ShowSchedule::toDataLine);
    }

    public synchronized boolean saveCinemaVenues() {
        return writeCatalogFile(cinemaVenuesFile, CINEMA_VENUE_HEADER, cinemaVenues,
                CinemaVenue::toDataLine);
    }

    private void loadShowSchedules() {
        readCatalogFile(showSchedulesFile, showSchedules, ShowSchedule::fromDataLine);
    }

    private void loadCinemaVenues() {
        readCatalogFile(cinemaVenuesFile, cinemaVenues, CinemaVenue::fromDataLine);
    }

    /* ---- Feature 1: temporary seat holds ---- */

    public List<SeatHold> getSeatHolds() {
        return Collections.unmodifiableList(seatHolds);
    }

    /** Active (not expired) holds for one show. */
    public List<SeatHold> activeHoldsForShow(String showId) {
        List<SeatHold> mine = new ArrayList<>();
        for (SeatHold hold : seatHolds) {
            if (hold.getShowId().equalsIgnoreCase(showId) && hold.isActive()) {
                mine.add(hold);
            }
        }
        return mine;
    }

    /** Active holds for one seat on one show. */
    public SeatHold activeHoldForSeat(String showId, String seatNumber) {
        String wanted = seatNumber.toUpperCase();
        for (SeatHold hold : seatHolds) {
            if (hold.getShowId().equalsIgnoreCase(showId)
                    && hold.getSeatNumber().equalsIgnoreCase(wanted)
                    && hold.isActive()) {
                return hold;
            }
        }
        return null;
    }

    /** All active holds of one user (across all shows). */
    public List<SeatHold> activeHoldsForUser(String userName) {
        String wanted = userName.toLowerCase();
        List<SeatHold> mine = new ArrayList<>();
        for (SeatHold hold : seatHolds) {
            if (hold.getUserName().toLowerCase().equals(wanted) && hold.isActive()) {
                mine.add(hold);
            }
        }
        return mine;
    }

    public synchronized boolean addSeatHold(SeatHold hold) {
        if (hold == null || activeHoldForSeat(hold.getShowId(), hold.getSeatNumber()) != null) {
            return false; // seat already held
        }
        seatHolds.add(hold);
        return saveSeatHolds();
    }

    /** Removes a specific hold (payment success converts it instead, or user abandons). */
    public synchronized boolean removeSeatHold(SeatHold hold) {
        boolean removed = seatHolds.remove(hold);
        return removed && saveSeatHolds();
    }

    /** Release every active hold of one user (used when the payment page is abandoned). */
    public synchronized int releaseHoldsForUser(String userName) {
        String wanted = userName.toLowerCase();
        int released = 0;
        for (int i = seatHolds.size() - 1; i >= 0; i--) {
            SeatHold hold = seatHolds.get(i);
            if (hold.getUserName().toLowerCase().equals(wanted) && hold.isActive()) {
                seatHolds.remove(i);
                released++;
            }
        }
        if (released > 0) {
            saveSeatHolds();
        }
        return released;
    }

    /** Sweep called by the expiry timer: drop every hold whose expiresAt has passed. */
    public synchronized int expireHolds() {
        int released = 0;
        for (int i = seatHolds.size() - 1; i >= 0; i--) {
            if (!seatHolds.get(i).isActive()) {
                seatHolds.remove(i);
                released++;
            }
        }
        if (released > 0) {
            saveSeatHolds();
        }
        return released;
    }

    public synchronized boolean saveSeatHolds() {
        return writeCatalogFile(seatHoldsFile, SEAT_HOLD_HEADER, seatHolds, SeatHold::toDataLine);
    }

    private void loadSeatHolds() {
        readCatalogFile(seatHoldsFile, seatHolds, SeatHold::fromDataLine);
    }

    /* ---- Feature 3: in-app notifications ---- */

    public List<Notification> getNotificationsForUser(String userId) {
        String wanted = userId.toLowerCase();
        List<Notification> mine = new ArrayList<>();
        for (Notification n : notifications) {
            if (n.getUserId().toLowerCase().equals(wanted)) {
                mine.add(n);
            }
        }
        // newest first
        mine.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return mine;
    }

    public int unreadCountForUser(String userId) {
        String wanted = userId.toLowerCase();
        int count = 0;
        for (Notification n : notifications) {
            if (n.getUserId().toLowerCase().equals(wanted) && !n.isRead()) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean addNotification(Notification n) {
        if (n == null) {
            return false;
        }
        notifications.add(n);
        return saveNotifications();
    }

    public synchronized boolean markNotificationRead(String notificationId) {
        for (Notification n : notifications) {
            if (n.getNotificationId().equalsIgnoreCase(notificationId)) {
                n.setIsRead(true);
                return saveNotifications();
            }
        }
        return false;
    }

    public synchronized boolean markAllReadForUser(String userId) {
        String wanted = userId.toLowerCase();
        boolean changed = false;
        for (Notification n : notifications) {
            if (n.getUserId().toLowerCase().equals(wanted) && !n.isRead()) {
                n.setIsRead(true);
                changed = true;
            }
        }
        if (changed) {
            return saveNotifications();
        }
        return true;
    }

    public synchronized boolean saveNotifications() {
        return writeCatalogFile(notificationsFile, NOTIFICATION_HEADER,
                notifications, Notification::toDataLine);
    }

    private void loadNotifications() {
        readCatalogFile(notificationsFile, notifications, Notification::fromDataLine);
    }


    public synchronized boolean saveMovies() {
        return writeCatalogFile(moviesFile, MOVIE_HEADER, movies, Movie::toDataLine);
    }

    public synchronized boolean saveTheatres() {
        return writeCatalogFile(theatresFile, THEATRE_HEADER, theatres, Theatre::toDataLine);
    }

    public synchronized boolean saveShowTimes() {
        return writeCatalogFile(showTimesFile, SHOWTIME_HEADER, showTimes, ShowTime::toDataLine);
    }

    /* ------------------------------------------------------------------ *\
     * Bookings (Stage 5 payment, Stage 6 cancellation)
     * ------------------------------------------------------------------ */

    /** Every booking, newest first - that is the order both booking screens show them in. */
    public List<Booking> getBookings() {
        List<Booking> sorted = new ArrayList<>(bookings);
        Collections.sort(sorted); // Booking compares itself: newest first
        return Collections.unmodifiableList(sorted);
    }

    /** Lookup by booking id, or null. Ids come from the file, never from typed text. */
    public Booking findBooking(String bookingId) {
        int at = indexOfBooking(bookingId);
        return (at < 0) ? null : bookings.get(at);
    }

    /** The bookings of one customer, newest first; empty for a name that never booked. */
    public List<Booking> bookingsForUser(String userName) {
        String wanted = ValidationUtil.clean(userName).toLowerCase();
        List<Booking> mine = new ArrayList<>();
        for (Booking booking : getBookings()) {
            if (booking.getUserName().equals(wanted)) {
                mine.add(booking);
            }
        }
        return mine;
    }

    /** Stores a new booking. Refused when the id is already used, so ids stay unique. */
    public synchronized boolean addBooking(Booking booking) {
        if (booking == null || indexOfBooking(booking.getBookingId()) >= 0) {
            return false;
        }
        bookings.add(booking);
        return saveBookings();
    }

    /** Replaces the stored copy after a payment (Stage 5) or a cancellation (Stage 6). */
    public synchronized boolean updateBooking(Booking booking) {
        int at = indexOfBooking(booking == null ? null : booking.getBookingId());
        if (at < 0) {
            return false;
        }
        bookings.set(at, booking);
        return saveBookings();
    }

    /** Forgets a booking entirely; only used when a failed write has to be undone. */
    public synchronized boolean removeBooking(String bookingId) {
        int at = indexOfBooking(bookingId);
        if (at < 0) {
            return false;
        }
        bookings.remove(at);
        return saveBookings();
    }

    public synchronized boolean saveBookings() {
        return writeCatalogFile(bookingsFile, BOOKING_HEADER, bookings, Booking::toDataLine);
    }

    private void loadBookings() {
        readCatalogFile(bookingsFile, bookings, Booking::fromDataLine);
    }

    private int indexOfBooking(String bookingId) {
        String wanted = ValidationUtil.clean(bookingId).toLowerCase();
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).getBookingId().toLowerCase().equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    /* ------------------------------------------------------------------ *\
     * Stage 10: the food menu of every hall
     * ------------------------------------------------------------------ */

    /** Every menu item of every hall. The menu screens group these by hall themselves. */
    public List<MenuItem> getMenuItems() {
        return Collections.unmodifiableList(menuItems);
    }

    /** Lookup by item id, or null. This is how the basket re-prices itself before saving. */
    public MenuItem findMenuItem(String itemId) {
        String wanted = ValidationUtil.clean(itemId).toLowerCase();
        for (MenuItem item : menuItems) {
            if (item.getItemId().toLowerCase().equals(wanted)) {
                return item;
            }
        }
        return null;
    }

    /** The menu items of one hall, in file order. */
    public List<MenuItem> menuItemsOf(String theatreId) {
        String wanted = ValidationUtil.clean(theatreId).toLowerCase();
        List<MenuItem> mine = new ArrayList<>();
        for (MenuItem item : menuItems) {
            if (item.getTheatreId().toLowerCase().equals(wanted)) {
                mine.add(item);
            }
        }
        return mine;
    }

    /** Stores a new menu item. Refused when the id is already used, so ids stay unique. */
    public synchronized boolean addMenuItem(MenuItem item) {
        if (item == null || findMenuItem(item.getItemId()) != null) {
            return false;
        }
        menuItems.add(item);
        return saveCanteen();
    }

    /** Replaces a stored menu item after its price or name was edited. */
    public synchronized boolean updateMenuItem(MenuItem item) {
        if (item == null) {
            return false;
        }
        for (int i = 0; i < menuItems.size(); i++) {
            if (menuItems.get(i).getItemId().equalsIgnoreCase(item.getItemId())) {
                menuItems.set(i, item);
                return saveCanteen();
            }
        }
        return false;
    }

    public synchronized boolean saveCanteen() {
        return writeCatalogFile(canteenFile, CANTEEN_HEADER, menuItems, MenuItem::toDataLine);
    }

    private void loadCanteen() {
        readCatalogFile(canteenFile, menuItems, MenuItem::fromDataLine);
    }

    /* ------------------------------------------------------------------ *\
     * Stage 10: the food orders that were placed
     * ------------------------------------------------------------------ */

    /** Every food order, newest first. */
    public List<FoodOrder> getFoodOrders() {
        List<FoodOrder> sorted = new ArrayList<>(foodOrders);
        sorted.sort((first, second) -> second.getPlacedAt().compareTo(first.getPlacedAt()));
        return Collections.unmodifiableList(sorted);
    }

    public FoodOrder findFoodOrder(String orderId) {
        String wanted = ValidationUtil.clean(orderId).toLowerCase();
        for (FoodOrder order : foodOrders) {
            if (order.getOrderId().toLowerCase().equals(wanted)) {
                return order;
            }
        }
        return null;
    }

    /** The food orders of one customer, newest first. */
    public List<FoodOrder> foodOrdersForUser(String userName) {
        String wanted = ValidationUtil.clean(userName).toLowerCase();
        List<FoodOrder> mine = new ArrayList<>();
        for (FoodOrder order : getFoodOrders()) {
            if (order.getUserName().toLowerCase().equals(wanted)) {
                mine.add(order);
            }
        }
        return mine;
    }

    /** Stores a new food order. Refused when the id is already used, so ids stay unique. */
    public synchronized boolean addFoodOrder(FoodOrder order) {
        if (order == null || findFoodOrder(order.getOrderId()) != null) {
            return false;
        }
        foodOrders.add(order);
        return saveFoodOrders();
    }

    public synchronized boolean updateFoodOrder(FoodOrder order) {
        if (order == null) {
            return false;
        }
        for (int i = 0; i < foodOrders.size(); i++) {
            if (foodOrders.get(i).getOrderId().equalsIgnoreCase(order.getOrderId())) {
                foodOrders.set(i, order);
                return saveFoodOrders();
            }
        }
        return false;
    }

    public synchronized boolean saveFoodOrders() {
        return writeCatalogFile(foodOrdersFile, FOOD_ORDER_HEADER, foodOrders, FoodOrder::toDataLine);
    }

    private void loadFoodOrders() {
        readCatalogFile(foodOrdersFile, foodOrders, FoodOrder::fromDataLine);
    }

    /* ---- The generic file helpers used by the three save methods above ---- */

    /**
     * Reads one catalogue file. The method reference handed in (for example
     * Movie::fromDataLine) converts one text line into one object, so this loop works for
     * every catalogue type and is written exactly once. A broken line is skipped, never fatal.
     */
    private <T> void readCatalogFile(File file, List<T> target, Function<String, T> lineToObject) {
        target.clear();
        if (!file.exists()) {
            return;
        }
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue; // blank line, or our file header
                }
                try {
                    target.add(lineToObject.apply(text));
                } catch (Exception problem) {
                    System.err.println("Skipped " + file.getName() + " line " + lineNumber
                            + ": " + problem.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
    }

    /** The reverse of readCatalogFile: every object becomes one line of text. */
    private <T> boolean writeCatalogFile(File file, String header, List<T> items,
                                         Function<T, String> objectToLine) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            writer.println(header);
            for (T item : items) {
                writer.println(objectToLine.apply(item));
            }
            return true;
        } catch (Exception e) {
            System.err.println("Could not save " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void loadCatalogue() {
        readCatalogFile(moviesFile, movies, Movie::fromDataLine);
        readCatalogFile(theatresFile, theatres, Theatre::fromDataLine);
        readCatalogFile(showTimesFile, showTimes, ShowTime::fromDataLine);
    }

    private int indexOfMovie(String movieId) {
        for (int i = 0; i < movies.size(); i++) {
            if (movies.get(i).getMovieId().equalsIgnoreCase(String.valueOf(movieId))) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfTheatre(String theatreId) {
        for (int i = 0; i < theatres.size(); i++) {
            if (theatres.get(i).getTheatreId().equalsIgnoreCase(String.valueOf(theatreId))) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfShowTime(String showId) {
        for (int i = 0; i < showTimes.size(); i++) {
            if (showTimes.get(i).getShowId().equalsIgnoreCase(String.valueOf(showId))) {
                return i;
            }
        }
        return -1;
    }

    /** Used when a film is deleted, so no show is left pointing at a film that is gone. */
    private void removeShowTimesOf(String movieId) {
        for (int i = showTimes.size() - 1; i >= 0; i--) {
            if (showTimes.get(i).getMovieId().equalsIgnoreCase(movieId)) {
                showTimes.remove(i);
            }
        }
    }

    /**
     * Same idea for the credits and the reviews of a film: nobody should be left pointing at a
     * film that is gone. Both files are rewritten so the removal survives a restart.
     */
    private void removeCreditsOf(String movieId) {
        for (int i = castMembers.size() - 1; i >= 0; i--) {
            if (castMembers.get(i).getMovieId().equalsIgnoreCase(movieId)) {
                castMembers.remove(i);
            }
        }
        for (int i = reviews.size() - 1; i >= 0; i--) {
            if (reviews.get(i).getMovieId().equalsIgnoreCase(movieId)) {
                reviews.remove(i);
            }
        }
        saveCast();
        saveReviews();
    }

    /* ------------------------------------------------------------------ *
     * Reading / writing the user file
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------ *\
     * Localities, offers and gift cards used by the three new services
     * ------------------------------------------------------------------ */

    /** Every place in the catalogue. unmodifiable = callers cannot change our list by mistake. */
    public List<Locality> getLocalities() {
        return Collections.unmodifiableList(localities);
    }

    /** Lookup by id, or null. Ids come from the file, never from typed text. */
    public Locality findLocality(String localityId) {
        String wanted = ValidationUtil.clean(localityId).toLowerCase();
        for (Locality place : localities) {
            if (place.getLocalityId().toLowerCase().equals(wanted)) {
                return place;
            }
        }
        return null;
    }

    /* ---- Offers ---- */

    public List<Offer> getOffers() {
        return Collections.unmodifiableList(offers);
    }

    /** Lookup by code, ignoring case - this is what the customer types. */
    public Offer findOffer(String code) {
        String wanted = ValidationUtil.clean(code).toUpperCase();
        for (Offer offer : offers) {
            if (offer.getCode().equals(wanted)) {
                return offer;
            }
        }
        return null;
    }

    /**
     * Replaces the stored copy of an offer, which is how "used by this customer" is remembered.
     * Called only after a real purchase, never while a code is merely being typed.
     */
    public synchronized boolean updateOffer(Offer offer) {
        int at = -1;
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getOfferId().equals(offer.getOfferId())) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return false;
        }
        offers.set(at, offer);
        return saveOffers();
    }

    public synchronized boolean saveOffers() {
        return writeCatalogFile(offersFile, OFFER_HEADER, offers, Offer::toDataLine);
    }

    private void loadOffers() {
        readCatalogFile(offersFile, offers, Offer::fromDataLine);
    }

    /* ---- Gift cards ---- */

    public List<GiftCard> getGiftCards() {
        return Collections.unmodifiableList(giftCards);
    }

    public GiftCard findGiftCard(String cardId) {
        String wanted = ValidationUtil.clean(cardId).toLowerCase();
        for (GiftCard card : giftCards) {
            if (card.getCardId().toLowerCase().equals(wanted)) {
                return card;
            }
        }
        return null;
    }

    /** The cards of one customer, newest first - the wallet on the Gift cards screen. */
    public List<GiftCard> giftCardsForUser(String userName) {
        String wanted = ValidationUtil.clean(userName).toLowerCase();
        List<GiftCard> mine = new ArrayList<>();
        for (GiftCard card : getGiftCards()) {
            if (card.getOwnerName().equals(wanted)) {
                mine.add(card);
            }
        }
        mine.sort((a, b) -> b.getIssuedAt().compareTo(a.getIssuedAt()));
        return Collections.unmodifiableList(mine);
    }

    public synchronized boolean addGiftCard(GiftCard card) {
        if (card == null || findGiftCard(card.getCardId()) != null) {
            return false;
        }
        giftCards.add(card);
        return saveGiftCards();
    }

    /** Replaces the stored copy after a card was charged or refunded. */
    public synchronized boolean updateGiftCard(GiftCard card) {
        int at = -1;
        for (int i = 0; i < giftCards.size(); i++) {
            if (giftCards.get(i).getCardId().equals(card.getCardId())) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return false;
        }
        giftCards.set(at, card);
        return saveGiftCards();
    }

    public synchronized boolean saveGiftCards() {
        return writeCatalogFile(giftCardsFile, GIFT_CARD_HEADER, giftCards, GiftCard::toDataLine);
    }

    private void loadLocalities() {
        readCatalogFile(localitiesFile, localities, Locality::fromDataLine);
    }

    private void loadGiftCards() {
        readCatalogFile(giftCardsFile, giftCards, GiftCard::fromDataLine);
    }

    /* ------------------------------------------------------------------ *
     * Credits and reviews used by CastService and ReviewService
     * ------------------------------------------------------------------ */

    /** Every credited person of every film. unmodifiable, like every other list here. */
    public List<CastMember> getCastMembers() {
        return Collections.unmodifiableList(castMembers);
    }

    /** The credits of one film, in the order they were written. */
    public List<CastMember> castOf(String movieId) {
        List<CastMember> mine = new ArrayList<>();
        String wanted = ValidationUtil.clean(movieId).toLowerCase();
        for (CastMember member : castMembers) {
            if (member.getMovieId().toLowerCase().equals(wanted)) {
                mine.add(member);
            }
        }
        return Collections.unmodifiableList(mine);
    }

    public synchronized boolean saveCast() {
        return writeCatalogFile(castFile, CAST_HEADER, castMembers, CastMember::toDataLine);
    }

    private void loadCast() {
        readCatalogFile(castFile, castMembers, CastMember::fromDataLine);
    }

    /** Every review of every film, customer comments and critic reviews together. */
    public List<Review> getReviews() {
        return Collections.unmodifiableList(reviews);
    }

    /** The reviews of one film, in the order they were written. */
    public List<Review> reviewsOf(String movieId) {
        List<Review> mine = new ArrayList<>();
        String wanted = ValidationUtil.clean(movieId).toLowerCase();
        for (Review review : reviews) {
            if (review.getMovieId().toLowerCase().equals(wanted)) {
                mine.add(review);
            }
        }
        return Collections.unmodifiableList(mine);
    }

    /** Store a review someone just wrote and hand back the saved copy, or null when refused. */
    public synchronized Review addReview(Review review) {
        if (review == null || !review.isShowable() || findMovie(review.getMovieId()) == null) {
            return null; // a review must name a film that really exists
        }
        if (findReview(review.getReviewId()) != null) {
            review.setReviewId(IDGenerator.reviewId());
        }
        reviews.add(review);
        saveReviews();
        return review;
    }

    /** Lookup by id, or null. Ids come from the file or the generator, never from typed text. */
    public Review findReview(String reviewId) {
        String wanted = ValidationUtil.clean(reviewId).toLowerCase();
        for (Review review : reviews) {
            if (review.getReviewId().toLowerCase().equals(wanted)) {
                return review;
            }
        }
        return null;
    }

    /** Forgets a review that must not be shown any more. */
    public synchronized boolean deleteReview(String reviewId) {
        for (int i = 0; i < reviews.size(); i++) {
            if (reviews.get(i).getReviewId().equalsIgnoreCase(ValidationUtil.clean(reviewId))) {
                reviews.remove(i);
                return saveReviews();
            }
        }
        return false;
    }

    public synchronized boolean saveReviews() {
        return writeCatalogFile(reviewsFile, REVIEW_HEADER, reviews, Review::toDataLine);
    }

    private void loadReviews() {
        readCatalogFile(reviewsFile, reviews, Review::fromDataLine);
    }

    private void createFolders() {
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
        } catch (Exception e) {
            System.err.println("Could not create the data folder: " + e.getMessage());
        }
    }

    /** Loads every line of users.txt into memory. A broken line is skipped, never fatal. */
    private void loadUsers() {
        users.clear();
        if (!usersFile.exists()) {
            return;
        }
        int lineNumber = 0;
        boolean migratedAdminId = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue; // blank line, or a comment such as our file header
                }
                try {
                    User user = User.fromDataLine(text);
                    users.add(user);
                    if (user instanceof Admin) {
                        String[] columns = text.split(User.SEPARATOR_REGEX, -1);
                        if (columns.length < 12 || !columns[11].matches("[0-9]{12}")) {
                            migratedAdminId = true;
                        }
                        if (((Admin) user).getEmail().equalsIgnoreCase("admin@cinesmart.in")
                                && !AppConfig.ADMIN_EMAIL.equalsIgnoreCase("admin@cinesmart.in")
                                && ValidationUtil.isValidEmail(AppConfig.ADMIN_EMAIL)) {
                            user.setEmail(AppConfig.ADMIN_EMAIL);
                            migratedAdminId = true;
                        }
                    }
                } catch (Exception problem) {
                    System.err.println("Skipped line " + lineNumber + ": " + problem.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read " + usersFile + ": " + e.getMessage());
        }
        if (migratedAdminId) {
            saveUsers();
        }
    }

    /** Writes the whole list back to disk. Simple and safe for a college sized data set. */
    public synchronized boolean saveUsers() {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(usersFile)))) {
            writer.println(FILE_HEADER);
            for (User user : users) {
                writer.println(user.toDataLine());
            }
            return true;
        } catch (Exception e) {
            System.err.println("Could not save users: " + e.getMessage());
            return false;
        }
    }

    /**
     * A college project must always be runnable, so on the very first start two ready
     * accounts are created: admin/admin123 (Admin panel) and demo/demo123 (normal booking).
     */
    private void seedAccountsIfEmpty() {
        if (!users.isEmpty()) {
            return;
        }
        Admin admin = new Admin("admin", "CineSmart Admin", AppConfig.ADMIN_EMAIL,
                "9000000001", Session.CITIES[0]);
        admin.setPassword("admin123");

        Customer demo = new Customer("demo", "Demo Customer", "demo@cinesmart.in",
                "9000000002", Session.CITIES[0], "2003-05-14", "Male");
        demo.setPassword("demo123");

        users.add(admin);
        users.add(demo);
        saveUsers();
        System.out.println("First-run sample accounts created. Customer sign-in requires an "
                + "account email that can receive verification codes.");
    }

    private void printAdminSignInIds() {
        for (User user : users) {
            if (user instanceof Admin) {
                System.out.println("Admin sign-in ID: " + ((Admin) user).getAdminId());
            }
        }
    }

    /* ------------------------------------------------------------------ *
     * Sample cinema catalogue (Stage 3)
     * ------------------------------------------------------------------ */

    /**
     * A college project must always have something to show, so the first start fills the
     * three catalogue files with a small but realistic data set: ten films, ten cinemas in
     * every city of the combo box, and three days of show times.
     *
     * Nothing here is hard wired to a date: film release dates and show dates are written as
     * offsets from LocalDate.now(), so the demo still looks alive next month.
     */
    private void seedCatalogueIfEmpty() {
        boolean added = false;
        if (movies.isEmpty()) {
            movies.addAll(sampleMovies());
            saveMovies();
            added = true;
        }
        if (theatres.isEmpty()) {
            theatres.addAll(sampleTheatres());
            saveTheatres();
            added = true;
        }
        if (showTimes.isEmpty()) {
            showTimes.addAll(sampleShowTimes());
            saveShowTimes();
            added = true;
        }
        // Stage 11: one-time migration - if no schedules exist yet, extract one template
        // per unique (movie, theatre, screen, time, price) from the existing showtimes.
        if (showSchedules.isEmpty()) {
            migrateShowsToSchedules();
            added = true;
        }
        if (added) {
            System.out.println("Sample catalogue created: " + movies.size() + " movies, "
                    + theatres.size() + " theatres, " + showTimes.size() + " show times, "
                    + showSchedules.size() + " show schedules.");
        }
    }

    /** One-time migration: turn existing showtimes.txt entries into ShowSchedule templates,
     *  so the rolling generator can take over from the next start. Only runs when
     *  data/showschedules.txt is missing. */
    private void migrateShowsToSchedules() {
        Set<String> seen = new LinkedHashSet<>();
        String nextId = "SCH";
        int serial = 1;
        for (ShowTime show : showTimes) {
            String key = show.getMovieId() + "|" + show.getTheatreId() + "|"
                    + show.getScreenName() + "|" + show.getStartTime() + "|"
                    + show.getPrice();
            if (seen.add(key)) {
                ShowSchedule schedule = new ShowSchedule(
                        String.format("SCH%03d", serial++),
                        show.getMovieId(), show.getTheatreId(), show.getScreenName(),
                        show.getStartTime(), show.getPrice(), true, "", "");
                showSchedules.add(schedule);
            }
        }
        if (!showSchedules.isEmpty()) {
            saveShowSchedules();
            System.out.println("Stage 11 migration: created " + showSchedules.size()
                    + " show schedules from existing showtimes.");
        }
    }

    /**
     * The films. Release dates are relative to today so "Now showing" is never empty.
     *
     * These are real, well known films, and every posterUrl below was checked by hand: the address
     * was downloaded and the picture was looked at, so no card can ever show the wrong artwork.
     * Two sources are used because each covers films the other does not:
     *
     *   - image.tmdb.org - The Movie Database image CDN. It serves poster FILES with no API key,
     *     which is why the official search API (that one does need a key) is not used here.
     *   - upload.wikimedia.org - the Indian films, whose posters Wikipedia hosts. Wikimedia asks
     *     for a User-Agent, which PosterLoader always sends.
     *
     * A film with an empty posterUrl is deliberate in a couple of places: it shows that a missing
     * poster degrades to the artwork the card paints itself instead of leaving a blank tile.
     */
    private List<Movie> sampleMovies() {
        List<Movie> list = new ArrayList<>();
        list.add(new Movie("M001", "Inception", "English", "Sci-Fi", 148,
                "UA 16+", days(-6), 8.8, "Christopher Nolan",
                "Leonardo DiCaprio, Joseph Gordon-Levitt, Elliot Page",
                "A thief who steals corporate secrets through dream-sharing technology is given "
                        + "the inverse task: plant an idea in his target's mind.",
                "https://image.tmdb.org/t/p/w342/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg"));
        list.add(new Movie("M002", "Interstellar", "English", "Sci-Fi", 169,
                "UA", days(-11), 8.7, "Christopher Nolan",
                "Matthew McConaughey, Anne Hathaway, Jessica Chastain",
                "With Earth's crops failing, a former pilot leads a mission through a wormhole to "
                        + "find humanity a new home.",
                "https://image.tmdb.org/t/p/w342/xJHokMbljvjADYdit5fK5VQsXEG.jpg"));
        list.add(new Movie("M003", "The Dark Knight", "English", "Action", 152,
                "UA", days(-18), 9.0, "Christopher Nolan",
                "Christian Bale, Heath Ledger, Aaron Eckhart",
                "Batman faces the Joker, an agent of chaos who pushes Gotham and its defenders to "
                        + "the edge.",
                "https://image.tmdb.org/t/p/w342/qJ2tW6WMUDux911r6m7haRef0WH.jpg"));
        list.add(new Movie("M004", "Oppenheimer", "English", "Drama", 181,
                "UA 16+", days(-2), 8.6, "Christopher Nolan",
                "Cillian Murphy, Emily Blunt, Robert Downey Jr.",
                "The story of the physicist who led the Manhattan Project and the reckoning that "
                        + "followed him for the rest of his life.",
                "https://image.tmdb.org/t/p/w342/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"));
        list.add(new Movie("M005", "RRR", "Telugu", "Action", 187,
                "UA", days(-9), 7.8, "S. S. Rajamouli",
                "N. T. Rama Rao Jr., Ram Charan, R. Madhavan",
                "In 1920s India a young guardian of the kingdom and a rebel British officer set out "
                        + "to rescue a kidnapped girl.",
                "https://upload.wikimedia.org/wikipedia/en/d/d7/RRR_Poster.jpg"));
        list.add(new Movie("M006", "Avengers: Endgame", "English", "Action", 181,
                "UA", days(-14), 8.4, "Anthony Russo, Joe Russo",
                "Robert Downey Jr., Chris Evans, Scarlett Johansson",
                "The Avengers make one last, impossible attempt to undo the loss that left half the "
                        + "world in ruins.",
                "https://image.tmdb.org/t/p/w342/or06FN3Dka5tukK1e9sl16pB3iy.jpg"));
        list.add(new Movie("M007", "Baahubali 2: The Conclusion", "Telugu", "Action", 167,
                "UA", days(-8), 8.0, "S. S. Rajamouli",
                "Prabhas, Rana Daggubati, Anushka Shetty",
                "The epic clash between two brothers reaches its final battle, and a young woman "
                        + "walks into the war to change its course.",
                "https://upload.wikimedia.org/wikipedia/en/9/93/Baahubali_2_The_Conclusion_poster.jpg"));
        list.add(new Movie("M008", "Pulp Fiction", "English", "Thriller", 154,
                "A", days(-22), 8.9, "Quentin Tarantino",
                "John Travolta, Samuel L. Jackson, Uma Thurman",
                "Two hitmen, a boxer, a gangster's wife and a pair of diner bandits cross paths in "
                        + "four interlocking tales of violence and dark humour.",
                "https://image.tmdb.org/t/p/w342/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg"));
        list.add(new Movie("M009", "The Green Mile", "English", "Drama", 189,
                "UA 16+", days(-26), 8.6, "Frank Darabont",
                "Tom Hanks, Michael Clarke Duncan, David Morse",
                "Guards on a Depression-era death row discover that one of their prisoners has a "
                        + "miraculous gift.",
                "https://image.tmdb.org/t/p/w342/8VG8fDNiy50H4FedGwdSVUPoaJe.jpg"));
        list.add(new Movie("M010", "Spider-Man: No Way Home", "English", "Action", 148,
                "UA", days(-4), 8.2, "Jon Watts",
                "Tom Holland, Zendaya, Benedict Cumberbatch",
                "With his identity exposed, Peter Parker asks for help from the two villains who "
                        + "hunted him, and pays for it.",
                "https://image.tmdb.org/t/p/w342/1g0dhYtq4irTY1GPXvft6k4YLjm.jpg"));

        // The two announced films that fill the Coming Soon tab. They deliberately carry NO
        // posterUrl: they have not been released, so no artwork exists yet, and this is what
        // proves a film without a picture still gets a finished-looking card of its own colour.
        list.add(new Movie("M011", "Dune: Part Three", "English", "Sci-Fi", 0,
                "UA 16+", days(19), 0.0, "Denis Villeneuve",
                "Timothee Chalamet, Zendaya",
                "Paul Atreides walks the sands of Arrakis once more. Length and rating are "
                        + "announced later."));
        list.add(new Movie("M012", "Hanuman 2", "Telugu", "Action", 0,
                "UA", days(33), 0.0, "Prasanth Varma",
                "Teja Sajja, Amritha Aiyer",
                "Hanuman faces a threat that reaches beyond his own strength. Length and rating "
                        + "are announced later."));
        applySampleReception(list);
        return list;
    }

    /**
     * Fills the two audience-reception columns of the sample films.
     *
     * The numbers are illustrative - they are what a big release would collect in a year - and
     * they exist so the details screen shows a real "26.9K reviews" header and four working
     * sentiment chips instead of an empty section. They are kept out of the Movie constructor calls
     * above so the twelve film lines there still read as plain film data.
     */
    private void applySampleReception(List<Movie> list) {
        String[][] rows = {
            {"M001", "41200", "MindBending=24150;MustWatch=18900;Intense=15200;SuperDirection=9840"},
            {"M002", "38900", "Emotional=21400;MindBending=19200;MustWatch=15300;Family=9010"},
            {"M003", "52400",
                "SuperDirection=30120;Iconic=26200;Blockbuster=22400;MustWatch=18900"},
            {"M004", "26900",
                "SuperDirection=15387;GreatActing=15293;AwesomeStory=15268;Blockbuster=12308"},
            {"M005", "22100",
                "Blockbuster=12890;GreatDirection=11940;MustWatch=9820;Emotional=9130"},
            {"M006", "47300",
                "Emotional=29100;Blockbuster=27450;MustWatch=21300;Iconic=18800"},
            {"M007", "31500",
                "Blockbuster=18900;GreatDirection=16200;MustWatch=14100;Goosebumps=12750"},
            {"M008", "44800", "Iconic=26400;Classic=22800;MustWatch=17300;MindBending=12900"},
            {"M009", "26700", "Emotional=15300;MustWatch=12400;Classic=10780;Heavy=9430"},
            {"M010", "43600",
                "Blockbuster=25300;MustWatch=21100;Emotional=17400;Nostalgia=13200"},
            // The two announced films are left alone: nobody has seen them yet, so they have no
            // reviews and the details screen hides the whole section for them.
        };
        for (String[] row : rows) {
            // Looked up in the list being built, not in the store: the store is still empty at this
            // point, because the seed has not added these films to it yet.
            for (Movie movie : list) {
                if (movie.getMovieId().equals(row[0])) {
                    movie.setTotalReviews(Integer.parseInt(row[1]));
                    movie.setReviewTags(row[2]);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ *
     * Cast, crew and reviews of the sample catalogue
     * ------------------------------------------------------------------ */

    /** The portrait address of a seeded person, or an empty string when we have none. */
    private static String portraitOf(String name) {
        return PORTRAITS.getOrDefault(name, "");
    }

    /**
     * Adds one credit, giving it the next position inside its own group so the rows keep the order
     * they were written in.
     */
    private void seedCredit(String movieId, CastMember.Group group, String name, String role,
                            String character) {
        int position = 1;
        for (CastMember member : castMembers) {
            if (member.getMovieId().equalsIgnoreCase(movieId) && member.getGroup() == group) {
                position++;
            }
        }
        castMembers.add(new CastMember(movieId, group, position, name, role, character,
                portraitOf(name)));
    }

    /**
     * Reads one row of a film's credits table: "GROUP|Name|Role|Character name".
     *
     * A table rather than one call per person because it keeps a whole film's credits on a couple
     * of lines and makes the cast-to-crew split visible at a glance.
     */
    private void seedCredits(String movieId, String... rows) {
        for (String row : rows) {
            String[] parts = row.split("\\|", -1);
            if (parts.length < 3) {
                continue;
            }
            seedCredit(movieId, CastMember.Group.parse(parts[0]), parts[1], parts[2],
                    parts.length > 3 ? parts[3] : "");
        }
    }

    /**
     * The credits of the sample films - six actors and four crew names per film, which is what
     * fits on the two rows of the details screen.
     *
     * Written once, on a folder that has no data/cast.txt yet. A file that already exists is
     * never touched, so an admin screen may edit the credits and they survive the next start.
     */
    private void seedCastIfEmpty() {
        if (!castMembers.isEmpty()) {
            return;
        }
        seedCredits("M001",
                "CAST|Leonardo DiCaprio|Actor|Dom Cobb",
                "CAST|Joseph Gordon-Levitt|Actor|Arthur",
                "CAST|Elliot Page|Actor|Ariadne",
                "CAST|Tom Hardy|Actor|Eames",
                "CAST|Ken Watanabe|Actor|Saito",
                "CAST|Marion Cotillard|Actor|Mal",
                "CREW|Christopher Nolan|Director|",
                "CREW|Christopher Nolan|Writer|",
                "CREW|David S. Goyer|Writer|",
                "CREW|Wally Pfister|Cinematography|");
        seedCredits("M002",
                "CAST|Matthew McConaughey|Actor|Cooper",
                "CAST|Anne Hathaway|Actor|Brand",
                "CAST|Jessica Chastain|Actor|Murph",
                "CAST|Michael Caine|Actor|Professor Brand",
                "CAST|Matt Damon|Actor|Dr. Mann",
                "CAST|Mackenzie Foy|Actor|Young Murph",
                "CREW|Christopher Nolan|Director|",
                "CREW|Jonathan Nolan|Writer|",
                "CREW|Emma Thomas|Producer|",
                "CREW|Wally Pfister|Cinematography|");
        seedCredits("M003",
                "CAST|Christian Bale|Actor|Bruce Wayne",
                "CAST|Heath Ledger|Actor|The Joker",
                "CAST|Aaron Eckhart|Actor|Harvey Dent",
                "CAST|Michael Caine|Actor|Alfred",
                "CAST|Maggie Gyllenhaal|Actor|Rachel Dawes",
                "CAST|Gary Oldman|Actor|Commissioner Gordon",
                "CREW|Christopher Nolan|Director|",
                "CREW|Jonathan Nolan|Writer|",
                "CREW|Emma Thomas|Producer|",
                "CREW|Wally Pfister|Cinematography|");
        seedCredits("M004",
                "CAST|Cillian Murphy|Actor|J. Robert Oppenheimer",
                "CAST|Emily Blunt|Actor|Katherine Oppenheimer",
                "CAST|Robert Downey Jr.|Actor|Lewis Strauss",
                "CAST|Matt Damon|Actor|Leslie Groves",
                "CAST|Kenneth Branagh|Actor|Niels Bohr",
                "CAST|Florence Pugh|Actor|Jean Tatlock",
                "CREW|Christopher Nolan|Director|",
                "CREW|Christopher Nolan|Writer|",
                "CREW|Emma Thomas|Producer|",
                "CREW|Greig Fraser|Cinematography|");
        seedCredits("M005",
                "CAST|N. T. Rama Rao Jr.|Actor|Allu Arjun",
                "CAST|Ram Charan|Actor|Charan",
                "CAST|R. Madhavan|Actor|Scott",
                "CAST|Alia Bhatt|Actor|Janaki",
                "CAST|Vijay Varma|Actor|Villain",
                "CREW|S. S. Rajamouli|Director|",
                "CREW|S. S. Rajamouli|Writer|",
                "CREW|D. V. V. Danayya|Writer|",
                "CREW|K. V. Guhan|Producer|");
        seedCredits("M006",
                "CAST|Robert Downey Jr.|Actor|Tony Stark",
                "CAST|Chris Evans|Actor|Steve Rogers",
                "CAST|Scarlett Johansson|Actor|Natasha Romanoff",
                "CAST|Chris Hemsworth|Actor|Thor",
                "CAST|Mark Ruffalo|Actor|Bruce Banner",
                "CAST|Elizabeth Olsen|Actor|Wanda Maximoff",
                "CREW|Anthony Russo|Director|",
                "CREW|Joe Russo|Director|",
                "CREW|Christopher Markus|Writer|",
                "CREW|Kevin Feige|Producer|");
        seedCredits("M007",
                "CAST|Prabhas|Actor|Amarendra Baahubali",
                "CAST|Rana Daggubati|Actor|Bhallaladeva",
                "CAST|Anushka Shetty|Actor|Devasena",
                "CAST|Sumanth|Actor|Bijja",
                "CAST|Nayanthara|Actor|Kausalya",
                "CREW|S. S. Rajamouli|Director|",
                "CREW|S. S. Rajamouli|Writer|",
                "CREW|R. R. Venkat|Editor|",
                "CREW|Shobu Yarlagadda|Producer|");
        seedCredits("M008",
                "CAST|John Travolta|Actor|Vincent Vega",
                "CAST|Samuel L. Jackson|Actor|Jules Winnfield",
                "CAST|Uma Thurman|Actor|Mia Wallace",
                "CAST|Bruce Willis|Actor|Butch Coolidge",
                "CAST|Ving Rhames|Actor|Marsellus Wallace",
                "CREW|Quentin Tarantino|Director|",
                "CREW|Quentin Tarantino|Writer|",
                "CREW|Thelma Schoonmaker|Editor|",
                "CREW|Lawrence Bender|Producer|");
        seedCredits("M009",
                "CAST|Tom Hanks|Actor|Paul Edgecomb",
                "CAST|Michael Clarke Duncan|Actor|John Coffey",
                "CAST|David Morse|Actor|Head Brutus Howell",
                "CAST|Bonnie Hunt|Actor|Jan Cairns",
                "CAST|Timothy Hutton|Actor|Harry Terwilliger",
                "CREW|Frank Darabont|Director|",
                "CREW|Frank Darabont|Writer|",
                "CREW|David Valdes|Producer|",
                "CREW|Maryann Brandon|Editor|");
        seedCredits("M010",
                "CAST|Tom Holland|Actor|Peter Parker",
                "CAST|Zendaya|Actor|Michelle Jones",
                "CAST|Benedict Cumberbatch|Actor|Doctor Strange",
                "CAST|Willem Dafoe|Actor|Norman Osborn",
                "CAST|Jamie Foxx|Actor|Max Dillon",
                "CAST|Andrew Garfield|Actor|Peter Parker",
                "CREW|Jon Watts|Director|",
                "CREW|Erik Sommers|Writer|",
                "CREW|Christina Nolan|Writer|",
                "CREW|Bob Persichetti|Editor|");
        seedCredits("M011",
                "CAST|Timothée Chalamet|Actor|Paul Atreides",
                "CAST|Zendaya|Actor|Chani",
                "CAST|Rebecca Ferguson|Actor|Lady Jessica",
                "CAST|Javier Bardem|Actor|Stilgar",
                "CREW|Denis Villeneuve|Director|",
                "CREW|Jon Spaihts|Writer|",
                "CREW|Greig Fraser|Cinematography|");
        seedCredits("M012",
                "CAST|Teja Sajja|Actor|Hanuman",
                "CAST|Amritha Aiyer|Actor|Anjaneya",
                "CREW|Prasanth Varma|Director|",
                "CREW|Prasanth Varma|Writer|",
                "CREW|Prasad Devineni|Producer|");
        saveCast();
    }

    /**
     * Adds one seeded review. The date is written as "days ago" so the "2 days ago" line stays
     * sensible whenever the project is run, and the id is generated from a running counter so the
     * file always has stable, readable review ids.
     */
    private void seedReview(String movieId, Review.Kind kind, String author, int rating, int likes,
                            int dislikes, int daysAgo, String tags, String headline, String body) {
        reviews.add(new Review(String.format("RS%03d", reviews.size() + 1), movieId, kind, author,
                rating, likes, dislikes, days(daysAgo), tags, headline, body));
    }

    /**
     * The seeded reviews: a few customer comments and a couple of critic reviews for every film
     * that is playing. The two announced films get none, because nobody has seen them yet - which
     * is also what proves the screen hides a section it has nothing to show.
     */
    private void seedReviewsIfEmpty() {
        if (!reviews.isEmpty()) {
            return;
        }
        seedReview("M001", Review.Kind.AUDIENCE, "Arjun Menon", 10, 846, 0, -2,
                "MindBending, MustWatch", "Watched it twice",
                "The corridor fight is still the best ten seconds in action cinema. Sound design "
                        + "does half the acting in this film and the toppling city in zero gravity "
                        + "feels like a dream you have actually had.");
        seedReview("M001", Review.Kind.AUDIENCE, "Divya Suresh", 9, 566, 12, -4,
                "GreatActing", "Perfect second watch",
                "Leonardo DiCaprio finally gets a role that suits the way he speaks. A little "
                        + "long in the middle but the ending lands hard.");
        seedReview("M001", Review.Kind.AUDIENCE, "Karthik Raman", 8, 312, 21, -9,
                "MustWatch", "Clever but cold",
                "Loved the ideas, missed the warmth. If you like puzzles you will love it, if you "
                        + "like films about people you may find it clinical.");
        seedReview("M001", Review.Kind.AUDIENCE, "Meera Iyer", 10, 271, 4, -16,
                "Intense", "The best of the decade",
                "Rewatched on IMAX with the sound turned up. Cobb and Mal's conversation in the "
                        + "flying dream is one of the most honest things about loss ever filmed.");
        seedReview("M002", Review.Kind.AUDIENCE, "Nikhil Varma", 10, 1180, 18, -1,
                "Emotional, MustWatch", "Cried in a public hall",
                "The docking scene had the whole row shouting. A film about a father and a daughter "
                        + "that somehow also explains relativity without you noticing.");
        seedReview("M002", Review.Kind.AUDIENCE, "Sneha Pillai", 9, 742, 31, -5,
                "MindBending", "Worth the IMAX ticket",
                "The first half asks for patience and repays it completely in the last hour. The "
                        + "water planet and the dust storm visuals are on another level.");
        seedReview("M002", Review.Kind.AUDIENCE, "Rahul Anand", 9, 389, 9, -11,
                "Emotional", "Science that feels like poetry",
                "Black holes, relativity and a farewell scene that actually hurts. Matthew "
                        + "McConaughey carries the entire film on very little dialogue.");
        seedReview("M003", Review.Kind.AUDIENCE, "Vikram Shetty", 10, 2140, 27, -3,
                "SuperDirection, Iconic", "Still the benchmark",
                "Heath Ledger's Joker is the reason this film matters. Nothing else in the genre "
                        + "has come close to how tense the interrogation scene stays after "
                        + "twenty-five years of rewatches.");
        seedReview("M003", Review.Kind.AUDIENCE, "Anjali Prakash", 10, 1320, 15, -7,
                "Blockbuster", "The perfect film",
                "There is no filler in this. The jump cuts in the hospital, the Batpod chase and "
                        + "the final montage could each be a film on their own.");
        seedReview("M003", Review.Kind.AUDIENCE, "Imran Qureshi", 9, 604, 42, -14,
                "MustWatch", "Nolan at his tightest",
                "Two hours and twenty minutes with not one scene you would cut. The score does a "
                        + "lot of the suspense work and the dialogue keeps pace with it.");
        seedReview("M004", Review.Kind.AUDIENCE, "Peter Fernandes", 10, 902, 11, -2,
                "SuperDirection", "A three hour film with no filler",
                "Nolan finally made the history film nobody asked for and it works. The Trinity "
                        + "sequence is the loudest cinema has ever been.");
        seedReview("M004", Review.Kind.AUDIENCE, "Lakshmi Narayanan", 9, 612, 8, -6,
                "GreatActing", "Murphy carries it",
                "Cillian Murphy does not shout once and still makes you understand a man the whole "
                        + "world finds hard to like. Long, but you never want it to end.");
        seedReview("M004", Review.Kind.AUDIENCE, "Gaurav Menon", 8, 298, 64, -12,
                "AwesomeStory", "Heavy going in the back half",
                "The politics and the paperwork are as interesting as the bomb. My only complaint "
                        + "is the run time, which even for a biopic is a lot.");
        seedReview("M005", Review.Kind.AUDIENCE, "Sai Kumar", 9, 1480, 22, -3,
                "Blockbuster, GreatDirection", "Every frame is an event",
                "Rajamouli gives us a lesson in scale without losing the emotion. The interval "
                        + "fight had the entire theatre on its feet and shouting.");
        seedReview("M005", Review.Kind.AUDIENCE, "Priyanka Rao", 8, 664, 30, -8,
                "MustWatch", "Long, but worth it",
                "Three hours is a lot, but there is no scene you would cut. The British period "
                        + "drama in the second half is a surprise.");
        seedReview("M005", Review.Kind.AUDIENCE, "Manish Bhat", 9, 418, 15, -13,
                "Blockbuster", "RRR is the reason cinemas still exist",
                "Sound, scale and a hero who fights for someone other than himself. The best "
                        + "Indian action film of the decade by a distance.");
        seedReview("M006", Review.Kind.AUDIENCE, "Arvind Krishnan", 9, 1910, 45, -4,
                "Emotional, Blockbuster", "The best ending they could have written",
                "Eleven years of build-up paid off in one long shot. I cried in a multiplex, which "
                        + "is not something I say often about a superhero film.");
        seedReview("M006", Review.Kind.AUDIENCE, "Farah Siddiqui", 8, 830, 38, -10,
                "MustWatch", "A very long goodbye",
                "Three hours of setup for forty minutes of payoff. The final battle is worth the "
                        + "wait, though the middle certainly tests you.");
        seedReview("M006", Review.Kind.AUDIENCE, "Suresh Babu", 10, 512, 9, -18,
                "Iconic", "Perfect send-off",
                "Every character gets the ending they earned and nobody dies for no reason. That "
                        + "was the whole promise of the series and they kept it.");
        seedReview("M007", Review.Kind.AUDIENCE, "Deepak Reddy", 10, 1720, 26, -2,
                "Blockbuster, GreatDirection", "The interval scene of my life",
                "The war on elephants and the rain fight in the fort are two of the greatest "
                        + "sequences ever made in this country. Prabhas and Rana Daggubati are "
                        + "both enormous.");
        seedReview("M007", Review.Kind.AUDIENCE, "Anusha Pillai", 9, 690, 11, -7,
                "MustWatch", "Grand and completely shameless",
                "Pure spectacle with a genuinely moving family story underneath. Anushka Shetty "
                        + "gets the film's best scene and deserves a film of her own.");
        seedReview("M007", Review.Kind.AUDIENCE, "Kiran Thomas", 8, 356, 20, -15,
                "Goosebumps", "Two hours of spectacle",
                "A little over the top and I mean that as a compliment. The choreography in the "
                        + "final battle is on another level.");
        seedReview("M008", Review.Kind.AUDIENCE, "Rebecca Thomas", 10, 1560, 34, -5,
                "Iconic, Classic", "Still perfect",
                "Not one scene has aged badly. The diner, the twist, the briefcase and the "
                        + "foot massage conversation are all as sharp as they were.");
        seedReview("M008", Review.Kind.AUDIENCE, "Aditya Kapoor", 9, 748, 17, -9,
                "Classic", "The dialogue snaps",
                "Tarantino writes for actors the way other directors write shots. John Travolta "
                        + "and Samuel L. Jackson have never been better matched.");
        seedReview("M008", Review.Kind.AUDIENCE, "Neha Grover", 9, 502, 12, -17,
                "MindBending", "Structure as storytelling",
                "Four stories that only make sense together. Watch it twice, because the order you "
                        + "watch it in completely changes the reading.");
        seedReview("M009", Review.Kind.AUDIENCE, "Thomas Mathew", 10, 1290, 19, -6,
                "Emotional, MustWatch", "The ending will destroy you",
                "Michael Clarke Duncan plays a man the film refuses to reduce to a mystery, and "
                        + "that choice is what makes the last twenty minutes hurt.");
        seedReview("M009", Review.Kind.AUDIENCE, "Snehalatha Rao", 9, 634, 14, -12,
                "Classic", "Darabont at his kindest",
                "Slow, patient and completely fair. Tom Hanks is wonderful with almost nothing to "
                        + "do, which is a harder performance than shouting.");
        seedReview("M009", Review.Kind.AUDIENCE, "Vivek Anand", 9, 388, 16, -20,
                "Heavy", "Not really a thriller",
                "It is really a film about people being good to each other. The magic is the "
                        + "mystery around it, but the heart is the friendship.");
        seedReview("M010", Review.Kind.AUDIENCE, "Rohan Desai", 10, 1740, 24, -2,
                "Blockbuster, Nostalgia", "Three Spideys, one screen",
                "The most generous film ever made for fans. It gives every version of the "
                        + "character a proper ending and earns every one of them.");
        seedReview("M010", Review.Kind.AUDIENCE, "Kritika Menon", 9, 806, 19, -6,
                "MustWatch", "Loud, silly, wonderful",
                "Two and a half hours of noise and it works because the heart is real. Willem "
                        + "Dafoe gets the best lines in the film.");
        seedReview("M010", Review.Kind.AUDIENCE, "Abhishek Rao", 8, 440, 41, -11,
                "Nostalgia", "Great fanservice, thin plot",
                "You will be grinning the whole time as long as you do not stop to ask why "
                        + "anything is happening. Bring the popcorn.");
        seedReview("M001", Review.Kind.CRITIC, "The Indian Express", 8, 410, 22, -3,
                "", "Inception",
                "Christopher Nolan's most disciplined puzzle-box since Memento keeps its dream logic "
                        + "just legible enough, and turns a caper about ideas into something quietly "
                        + "moving about grief.");
        seedReview("M001", Review.Kind.CRITIC, "Film Companion", 9, 356, 9, -8,
                "", "Inception",
                "A thriller that respects the audience and a science-fiction film that never explains "
                        + "itself twice. Cobb and Mal's small, stupid conversation is the heart of it.");
        seedReview("M001", Review.Kind.CRITIC, "The Hindu", 8, 298, 14, -14,
                "", "Inception",
                "Technically flawless and very nearly emotionally cold. When the film finally lets "
                        + "itself be sentimental, the effect is powerful precisely because it was "
                        + "withheld for so long.");
        seedReview("M002", Review.Kind.CRITIC, "The Indian Express", 9, 502, 11, -4,
                "", "Interstellar",
                "Blockbuster filmmaking with a doctoral thesis inside it. The docking sequence is the "
                        + "best sustained set piece of the decade and the finale earns its tears.");
        seedReview("M002", Review.Kind.CRITIC, "Variety India", 8, 341, 19, -11,
                "", "Interstellar",
                "The first hour asks for real patience and the last hour repays every minute. A "
                        + "father-and-daughter story that happens to contain a fair amount of "
                        + "relativity.");
        seedReview("M003", Review.Kind.CRITIC, "The Guardian", 10, 872, 6, -2,
                "", "The Dark Knight",
                "The film that proved a comic-book picture could be taken seriously, and the "
                        + "reason the genre still chases its standards. Heath Ledger's Joker is the "
                        + "performance the decade is measured against.");
        seedReview("M003", Review.Kind.CRITIC, "Roger Ebert", 9, 764, 15, -9,
                "", "The Dark Knight",
                "Heath Ledger is terrifying not because of makeup but because he seems to be having "
                        + "a wonderful time. Everything around him is beautifully controlled.");
        seedReview("M004", Review.Kind.CRITIC, "Money Control", 7, 386, 44, -2,
                "", "Oppenheimer",
                "Cillian Murphy's calculating calm and the authority he keeps on the screen keep the "
                        + "tension real, though the three-hour run time is not always justified and "
                        + "the answers do not always convince.");
        seedReview("M004", Review.Kind.CRITIC, "The Indian Express", 9, 448, 12, -6,
                "", "Oppenheimer",
                "Terrific performances give the conclusion it deserved. Nolan turns a committee "
                        + "hearing into a thriller and makes a physicist's conscience feel like the "
                        + "most dangerous thing in the room.");
        seedReview("M004", Review.Kind.CRITIC, "Film Companion", 8, 312, 17, -13,
                "", "Oppenheimer",
                "The loudest film of the year and the quietest in its head. The Trinity sequence is "
                        + "showmanship with a point of view, and the Harry S. Truman cameo is a joke "
                        + "worth the whole ticket.");
        seedReview("M005", Review.Kind.CRITIC, "The Hindu", 9, 588, 21, -3,
                "", "RRR",
                "S. S. Rajamouli makes a period action epic that is really about a man who discovers "
                        + "he has something worth fighting for. The interval battle is cinema at its "
                        + "most joyful.");
        seedReview("M005", Review.Kind.CRITIC, "Indian Express Weekend", 8, 394, 26, -10,
                "", "RRR",
                "Enormous, generous and a little deaf to its own length. The British period material "
                        + "surprises, and the emotional turn lands because the scale was earned.");
        seedReview("M006", Review.Kind.CRITIC, "The Verge", 8, 1042, 63, -4,
                "", "Avengers: Endgame",
                "The improbable, quiet opening hour earns the crowd-pleasing final act. Everyone gets "
                        + "an ending, almost nobody gets a needless death, and that restraint is the "
                        + "whole point of the film.");
        seedReview("M006", Review.Kind.CRITIC, "Film Companion", 8, 618, 38, -12,
                "", "Avengers: Endgame",
                "Eleven years of storytelling resolved in three hours, and the directors do not waste "
                        + "a single character. Slightly too long, entirely worth it.");
        seedReview("M007", Review.Kind.CRITIC, "The Hindu", 9, 712, 18, -2,
                "", "Baahubali 2",
                "Rajamouli balances two epic battles and a love story with surprising ease. The "
                        + "fort siege is a landmark of Indian action cinema and Devasena gets the "
                        + "film's best scene.");
        seedReview("M007", Review.Kind.CRITIC, "Indian Express Weekend", 8, 486, 24, -9,
                "", "Baahubali 2",
                "Grand, unembarrassed and beautifully cut. The interval reveal is one of the great "
                        + "surprises in recent Indian cinema, even if you have seen the trailer.");
        seedReview("M008", Review.Kind.CRITIC, "Film Companion", 10, 934, 28, -5,
                "", "Pulp Fiction",
                "Structure as storytelling, dialogue as rhythm and violence as punctuation. The film "
                        + "that proved a low-life crime story could be as precise as a sonnet, and "
                        + "nothing since has aged it.");
        seedReview("M008", Review.Kind.CRITIC, "The Guardian", 9, 802, 24, -13,
                "", "Pulp Fiction",
                "Tarantino's funniest and most frightening film. Travolta and Jackson are given lines "
                        + "that have to be read out of context to be believed, and they are entirely "
                        + "believable.");
        seedReview("M009", Review.Kind.CRITIC, "The Indian Express", 9, 524, 13, -6,
                "", "The Green Mile",
                "Frank Darabont's second film is as long as his first and twice as brave. A murder "
                        + "mystery that takes four hours to unfold and turns, in its last twenty "
                        + "minutes, into a story about grace.");
        seedReview("M009", Review.Kind.CRITIC, "Roger Ebert", 8, 468, 20, -14,
                "", "The Green Mile",
                "Michael Clarke Duncan's gentle, enormous performance carries a film that asks the "
                        + "audience to hold its breath for three hours and then rewards them with "
                        + "something sad.");
        seedReview("M010", Review.Kind.CRITIC, "The Verge", 8, 936, 44, -3,
                "", "Spider-Man: No Way Home",
                "A generous, frequently overstuffed anniversary gift. Treating three generations of "
                        + "the same hero as equals, rather than nostalgia bait, is the film's real "
                        + "achievement.");
        seedReview("M010", Review.Kind.CRITIC, "Film Companion", 8, 726, 36, -11,
                "", "Spider-Man: No Way Home",
                "Chaotic, loud and unashamed. Willem Dafoe turns up as the best thing in the room, "
                        + "and the final act lands because Tom Holland plays it as a person who is "
                        + "genuinely frightened.");
        saveReviews();
    }

    /** Convenience used by the sample data: "the date that is n days from today". */
    private static String days(int offset) {
        return LocalDate.now().plusDays(offset).toString();
    }

    /**
     * Ten cinemas - every city offered in the header combo box gets at least one.
     *
     * The two coordinates on each line are REAL: they were looked up on OpenStreetMap for the area
     * the cinema is in, which is what lets {@link cinesmart.util.GeoUtil} measure a real distance
     * and sort the halls nearest-first. They are the centre of the neighbourhood rather than the
     * exact gate of the building - accurate to a kilometre or so, which is all "how far away is
     * this hall" ever needs.
     */
    private List<Theatre> sampleTheatres() {
        List<Theatre> list = new ArrayList<>();
        list.add(theatreAt("T001", "PVR Phoenix Marketcity", "Bengaluru", "Whitefield",
                6, 240, "Dolby Atmos, Recliner seats, Food court", 12.995743, 77.757949));
        list.add(theatreAt("T002", "PVR Orion", "Bengaluru", "Koramangala",
                5, 210, "Dolby 7.1, Cafe, Parking", 12.935737, 77.624081));
        list.add(theatreAt("T003", "Sathyam Cinemas", "Chennai", "Thousand Lights",
                4, 190, "Premium seating, Parking", 13.058516, 80.258783));
        list.add(theatreAt("T004", "AMB Theatre", "Hyderabad", "Banjara Hills",
                7, 260, "IMAX, 4DX, Recliner seats", 17.417746, 78.439901));
        list.add(theatreAt("T005", "INOX Phoenix", "Mumbai", "Lower Parel",
                8, 320, "IMAX, Lounge access, Food court", 18.995680, 72.830276));
        list.add(theatreAt("T006", "Carnival Cinemas", "Mumbai", "Andheri West",
                5, 180, "Dolby 7.1, Cafe", 19.117250, 72.833968));
        list.add(theatreAt("T007", "Director Cut Cinema", "Delhi NCR", "Saket",
                6, 280, "Dolby Atmos, Recliner seats", 28.523490, 77.209631));
        list.add(theatreAt("T008", "Q Cinemas", "Kochi", "Marine Drive",
                4, 200, "4DX, Food court", 9.978478, 76.275773));
        list.add(theatreAt("T009", "PVR Bund Garden", "Pune", "Kalyani Nagar",
                5, 220, "Dolby 7.1, Cafe", 18.548138, 73.902551));
        list.add(theatreAt("T010", "INOX Quest", "Kolkata", "Cambridge Gate",
                6, 230, "IMAX, Lounge access", 22.543283, 88.352087));
        return list;
    }

    /** Builds one hall and puts it on the map. */
    private static Theatre theatreAt(String id, String name, String city, String area, int screens,
                                     int price, String amenities, double latitude, double longitude) {
        Theatre theatre = new Theatre(id, name, city, area, screens, price, amenities);
        theatre.setLatitude(latitude);
        theatre.setLongitude(longitude);
        return theatre;
    }
/* ------------------------------------------------------------------ *\
     * Stage 10 sample food menus
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------ *\
     * Sample locality catalogue (the offline stand-in for a GPS)
     * ------------------------------------------------------------------ */

    /**
     * Writes the place catalogue the first time the program runs.
     *
     * Every row is a real place with real coordinates, so "detect my location" and the "search for
     * your city" box have something true to work with. Each city gets a city-level row (the area
     * column empty, which the picker renders as just the city name) plus the neighbourhoods the halls
     * sit in, so the catalogue always covers both "which city" and "which part of it".
     *
     * The area coordinates match the matching row of data/theatres.txt, which is what makes the
     * distance shown on the canteen screen agree with the distance shown on the location screen.
     */
    private void seedLocalitiesIfEmpty() {
        if (!localities.isEmpty()) {
            return;
        }
        addLocality("LOC001", "Bengaluru", "", 12.971599, 77.594566);
        addLocality("LOC002", "Bengaluru", "Whitefield", 12.995743, 77.757949);
        addLocality("LOC003", "Bengaluru", "Koramangala", 12.935737, 77.624081);
        addLocality("LOC004", "Bengaluru", "MG Road", 12.975197, 77.606600);
        addLocality("LOC005", "Chennai", "", 13.082700, 80.270700);
        addLocality("LOC006", "Chennai", "Thousand Lights", 13.058516, 80.258783);
        addLocality("LOC007", "Chennai", "Anna Nagar", 13.085000, 80.210100);
        addLocality("LOC008", "Hyderabad", "", 17.385000, 78.486700);
        addLocality("LOC009", "Hyderabad", "Banjara Hills", 17.417746, 78.439901);
        addLocality("LOC010", "Hyderabad", "Gachibowli", 17.440100, 78.348900);
        addLocality("LOC011", "Mumbai", "", 19.076000, 72.877700);
        addLocality("LOC012", "Mumbai", "Lower Parel", 18.995680, 72.830276);
        addLocality("LOC013", "Mumbai", "Andheri West", 19.117250, 72.833968);
        addLocality("LOC014", "Mumbai", "Bandra", 19.059600, 72.829500);
        addLocality("LOC015", "Delhi NCR", "", 28.613900, 77.209000);
        addLocality("LOC016", "Delhi NCR", "Saket", 28.523490, 77.209631);
        addLocality("LOC017", "Delhi NCR", "Dwarka", 28.592100, 77.046000);
        addLocality("LOC018", "Kochi", "", 9.931200, 76.267300);
        addLocality("LOC019", "Kochi", "Marine Drive", 9.978478, 76.275773);
        addLocality("LOC020", "Kochi", "Kakkanad", 10.015900, 76.341000);
        addLocality("LOC021", "Pune", "", 18.520400, 73.856700);
        addLocality("LOC022", "Pune", "Kalyani Nagar", 18.548138, 73.902551);
        addLocality("LOC023", "Pune", "Baner", 18.559000, 73.786800);
        addLocality("LOC024", "Kolkata", "", 22.572600, 88.363900);
        addLocality("LOC025", "Kolkata", "Cambridge Gate", 22.543283, 88.352087);
        addLocality("LOC026", "Kolkata", "Salt Lake", 22.580000, 88.420000);
        saveLocalities();
    }

    private void addLocality(String id, String city, String area, double lat, double lon) {
        localities.add(new Locality(id, city, area, lat, lon));
    }

    private void saveLocalities() {
        writeCatalogFile(localitiesFile, LOCALITY_HEADER, localities, Locality::toDataLine);
    }

    /* ------------------------------------------------------------------ *\
     * Sample offers
     * ------------------------------------------------------------------ */

    /**
     * Writes the running offers the first time the program runs.
     *
     * Between them they show every kind of rule the {@link Offer} model knows: a flat discount, a
     * percentage with a cap, a minimum order value, a city restriction, a tickets-only code, a
     * food-only code and an already expired one. Having all of them on the first screen is what makes
     * the Offers tab useful to look at rather than just a decoration.
     */
    private void seedOffersIfEmpty() {
        if (!offers.isEmpty()) {
            return;
        }
        offers.add(new Offer("OFF01", "FIRST50", "Flat Rs 50 off your first booking",
                "New to CineSmart? Take Rs 50 off any booking above Rs 300.",
                Offer.Kind.FLAT, 50, 300, 0, "", days(120), Offer.Scope.ANY));
        offers.add(new Offer("OFF02", "CINE20", "20% off, up to Rs 200",
                "The everyday offer: a fifth off every booking, capped so a long film stays cheap.",
                Offer.Kind.PERCENT, 20, 500, 200, "", days(60), Offer.Scope.TICKETS));
        offers.add(new Offer("OFF03", "SNACK75", "Flat Rs 75 off at the food counter",
                "Popcorn is better when it is discounted. Valid on food orders above Rs 400.",
                Offer.Kind.FLAT, 75, 400, 0, "", days(45), Offer.Scope.FOOD));
        offers.add(new Offer("OFF04", "KOCHI100", "Rs 100 off in Kochi",
                "A local offer for the Kochi halls only.",
                Offer.Kind.FLAT, 100, 400, 0, "Kochi", days(30), Offer.Scope.ANY));
        offers.add(new Offer("OFF05", "WEEKEND15", "15% off weekend shows",
                "A fifteenth off, capped at Rs 150, on any booking.",
                Offer.Kind.PERCENT, 15, 600, 150, "", days(90), Offer.Scope.TICKETS));
        offers.add(new Offer("OFF06", "BIGSCREEN30", "Flat Rs 30 off IMAX and 4DX",
                "A small thank you for the big screens.",
                Offer.Kind.FLAT, 30, 250, 0, "", days(75), Offer.Scope.TICKETS));
        offers.add(new Offer("OFF07", "COMBO200", "Rs 200 off a ticket and a snack",
                "Our best saving when the food is ordered together with the ticket.",
                Offer.Kind.FLAT, 200, 900, 0, "", days(21), Offer.Scope.ANY));
        // Expired on purpose: it proves the Offers screen and the payment screen both refuse it.
        offers.add(new Offer("OFF08", "EXPIRED50", "Flat Rs 50 off (ended)",
                "This code ran last month and is kept to show the expiry rule working.",
                Offer.Kind.FLAT, 50, 300, 0, "", days(-5), Offer.Scope.ANY));
        saveOffers();
    }

    /**
     * Writes a food menu the first time the program runs, so the canteen screen is never empty.
    }
     *
     * Every hall gets its OWN menu, not a shared one: the items and prices are deliberately different
     * between halls (PVR Orion sells a bigger bucket and a cold brew, Q Cinemas in Kochi sells the
     * Malabar snacks). That is what makes the "respective cinema hall" part real rather than
     * decoration - switching halls genuinely changes what you can order.
     *
     * Prices are rupees, in the same ballpark as real cinema food counters.
     */
    private void seedCanteenIfEmpty() {
        if (!menuItems.isEmpty()) {
            return;
        }
        seedMenu("T001", new String[][] {
            {"PVR01", "Salted Butter Popcorn (Large)", "Popcorn", "340", "true"},
            {"PVR02", "Cheese Burst Popcorn (Large)", "Popcorn", "380", "true"},
            {"PVR03", "Caramel Popcorn (Large)", "Popcorn", "360", "true"},
            {"PVR04", "Masala Peanuts", "Snacks", "90", "true"},
            {"PVR05", "Cheese Nachos with Salsa", "Snacks", "320", "true"},
            {"PVR06", "Peri Peri French Fries", "Snacks", "260", "true"},
            {"PVR07", "Chicken Tikka Sandwich", "Snacks", "340", "false"},
            {"PVR08", "Cold Coffee", "Beverages", "180", "true"},
            {"PVR09", "Masala Chai", "Beverages", "90", "true"},
            {"PVR10", "Fresh Lime Soda", "Beverages", "150", "true"},
            {"PVR11", "Choco Brownie", "Desserts", "180", "true"},
            {"PVR12", "Popcorn + 2 Cold Coffees", "Combos", "620", "true"},
        });
        seedMenu("T002", new String[][] {
            {"ORION1", "Extra Large Popcorn", "Popcorn", "380", "true"},
            {"ORION2", "Truffle Popcorn", "Popcorn", "450", "true"},
            {"ORION3", "Burnt Butter Popcorn", "Popcorn", "340", "true"},
            {"ORION4", "Cold Brew Coffee", "Beverages", "220", "true"},
            {"ORION5", "Blueberry Smoothie", "Beverages", "260", "true"},
            {"ORION6", "Masala Chai", "Beverages", "90", "true"},
            {"ORION7", "Paneer Tikka Roll", "Snacks", "290", "true"},
            {"ORION8", "Chicken Cheese Roll", "Snacks", "320", "false"},
            {"ORION9", "Salted Fries", "Snacks", "230", "true"},
            {"ORION10", "Belgian Chocolate Waffle", "Desserts", "280", "true"},
            {"ORION11", "Truffle Popcorn + Cold Brew", "Combos", "640", "true"},
        });
        seedMenu("T003", new String[][] {
            {"SAT01", "Buttered Popcorn", "Popcorn", "260", "true"},
            {"SAT02", "Pepper Corn Popcorn", "Popcorn", "290", "true"},
            {"SAT03", "Filter Coffee", "Beverages", "70", "true"},
            {"SAT04", "Badam Milk", "Beverages", "90", "true"},
            {"SAT05", "Fresh Lime Juice", "Beverages", "120", "true"},
            {"SAT06", "Idli Fries", "Snacks", "180", "true"},
            {"SAT07", "Veg Samosa (2 pcs)", "Snacks", "140", "true"},
            {"SAT08", "Chicken 65", "Snacks", "320", "false"},
            {"SAT09", "Mysore Pak", "Desserts", "150", "true"},
            {"SAT10", "Popcorn + Filter Coffee", "Combos", "310", "true"},
        });
        seedMenu("T004", new String[][] {
            {"AMB01", "Large Popcorn", "Popcorn", "330", "true"},
            {"AMB02", "Cheese Corn Popcorn", "Popcorn", "370", "true"},
            {"AMB03", "4DX Nachos Combo", "Snacks", "420", "true"},
            {"AMB04", "Chicken Popcorn", "Snacks", "390", "false"},
            {"AMB05", "Momos (Steamed)", "Snacks", "200", "true"},
            {"AMB06", "Masala Chai", "Beverages", "80", "true"},
            {"AMB07", "Fresh Lime Soda", "Beverages", "140", "true"},
            {"AMB08", "Oreo Shake", "Beverages", "230", "true"},
            {"AMB09", "Chocolate Muffin", "Desserts", "170", "true"},
            {"AMB10", "Popcorn + 2 Beverages", "Combos", "580", "true"},
        });
        seedMenu("T005", new String[][] {
            {"INOX01", "Lounge Popcorn (Large)", "Popcorn", "420", "true"},
            {"INOX02", "Lobster Popcorn", "Popcorn", "520", "false"},
            {"INOX03", "Garlic Bread", "Snacks", "260", "true"},
            {"INOX04", "Chicken Wings", "Snacks", "480", "false"},
            {"INOX05", "Paneer Tikka Puff", "Snacks", "230", "true"},
            {"INOX06", "Single Malt Shake", "Beverages", "320", "true"},
            {"INOX07", "Espresso Coffee", "Beverages", "180", "true"},
            {"INOX08", "Fresh Lime Soda", "Beverages", "160", "true"},
            {"INOX09", "Belgian Waffle", "Desserts", "300", "true"},
            {"INOX10", "Lounge Dinner Combo", "Combos", "980", "false"},
        });
        seedMenu("T006", new String[][] {
            {"CAR01", "Regular Popcorn", "Popcorn", "250", "true"},
            {"CAR02", "Caramel Popcorn", "Popcorn", "290", "true"},
            {"CAR03", "Salted French Fries", "Snacks", "200", "true"},
            {"CAR04", "Veg Burger", "Snacks", "250", "true"},
            {"CAR05", "Chicken Burger", "Snacks", "290", "false"},
            {"CAR06", "Masala Chai", "Beverages", "70", "true"},
            {"CAR07", "Fresh Lime Soda", "Beverages", "130", "true"},
            {"CAR08", "Lemon Iced Tea", "Beverages", "150", "true"},
            {"CAR09", "Chocolate Cookies", "Desserts", "140", "true"},
            {"CAR10", "Popcorn + Iced Tea", "Combos", "370", "true"},
        });
        seedMenu("T007", new String[][] {
            {"DIR01", "Director's Cut Popcorn", "Popcorn", "360", "true"},
            {"DIR02", "Truffle Parmesan Popcorn", "Popcorn", "430", "true"},
            {"DIR03", "Paneer Tikka Sandwich", "Snacks", "300", "true"},
            {"DIR04", "Chicken Seekh Kebab", "Snacks", "420", "false"},
            {"DIR05", "Chilli Garlic Fries", "Snacks", "270", "true"},
            {"DIR06", "Masala Chai", "Beverages", "90", "true"},
            {"DIR07", "Cold Coffee", "Beverages", "190", "true"},
            {"DIR08", "Fresh Lime Soda", "Beverages", "150", "true"},
            {"DIR09", "Chocolate Brownie", "Desserts", "190", "true"},
            {"DIR10", "Popcorn + Cold Coffee", "Combos", "510", "true"},
        });
        seedMenu("T008", new String[][] {
            {"QCIN01", "Popcorn (Regular)", "Popcorn", "240", "true"},
            {"QCIN02", "Popcorn (Large)", "Popcorn", "300", "true"},
            {"QCIN03", "Chicken Popcorn", "Popcorn", "370", "false"},
            {"QCIN04", "Beef Empanada", "Snacks", "220", "false"},
            {"QCIN05", "Paneer Roll", "Snacks", "180", "true"},
            {"QCIN06", "Tea", "Beverages", "50", "true"},
            {"QCIN07", "Fresh Lime Soda", "Beverages", "120", "true"},
            {"QCIN08", "Cold Coffee", "Beverages", "170", "true"},
            {"QCIN09", "Unniappam", "Desserts", "110", "true"},
            {"QCIN10", "Popcorn + Tea", "Combos", "280", "true"},
        });
        seedMenu("T009", new String[][] {
            {"BUN01", "Popcorn (Regular)", "Popcorn", "250", "true"},
            {"BUN02", "Cheese Popcorn", "Popcorn", "310", "true"},
            {"BUN03", "Vada Pav (2 pcs)", "Snacks", "120", "true"},
            {"BUN04", "Misal Pav", "Snacks", "180", "true"},
            {"BUN05", "Paneer Momos", "Snacks", "200", "true"},
            {"BUN06", "Masala Chai", "Beverages", "70", "true"},
            {"BUN07", "Fresh Lime Soda", "Beverages", "130", "true"},
            {"BUN08", "Buttermilk", "Beverages", "60", "true"},
            {"BUN09", "Ice Cream Tub", "Desserts", "150", "true"},
            {"BUN10", "Popcorn + Chai", "Combos", "310", "true"},
        });
        seedMenu("T010", new String[][] {
            {"QU01", "Popcorn (Large)", "Popcorn", "300", "true"},
            {"QU02", "Truffle Popcorn", "Popcorn", "390", "true"},
            {"QU03", "Kathi Roll", "Snacks", "220", "false"},
            {"QU04", "Veg Frankie", "Snacks", "190", "true"},
            {"QU05", "Chilli Garlic Fries", "Snacks", "230", "true"},
            {"QU06", "Masala Chai", "Beverages", "60", "true"},
            {"QU07", "Fresh Lime Soda", "Beverages", "120", "true"},
            {"QU08", "Cold Coffee", "Beverages", "160", "true"},
            {"QU09", "Mishti Doi", "Desserts", "110", "true"},
            {"QU10", "Popcorn + Cold Coffee", "Combos", "430", "true"},
        });
        saveCanteen();
        System.out.println("Sample food menus created: " + menuItems.size() + " items across "
                + theatres.size() + " cinema halls.");
    }

    /** Turns one block of sample rows (id, name, category, price, vegetarian) into menu items. */
    private void seedMenu(String theatreId, String[][] rows) {
        for (String[] row : rows) {
            menuItems.add(new MenuItem(row[0], theatreId, row[1], row[2],
                    Integer.parseInt(row[3]), Boolean.parseBoolean(row[4])));
        }
    }
    /**
     * Builds three days x four slots of screenings for every cinema. The film for a slot is
     * chosen with a shifting index so the same film does not repeat in one cinema, and a fixed
     * Random seed keeps the demo data identical on every machine.
     */
    private List<ShowTime> sampleShowTimes() {
        List<ShowTime> list = new ArrayList<>();
        List<Movie> playing = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie.isReleased()) {
                playing.add(movie); // coming-soon films cannot be booked yet
            }
        }
        if (playing.isEmpty() || theatres.isEmpty()) {
            return list;
        }

        String[] slots = {"09:00", "14:30", "21:00"};
        int[] surcharge = {0, 0, 30, 70}; // the evening show costs more, like a real cinema
        Random random = new Random(20260401L);
        int serial = 1;

        for (int day = 0; day < ShowTime.DAYS_AHEAD; day++) {
            String date = days(day);
            for (int t = 0; t < theatres.size(); t++) {
                Theatre theatre = theatres.get(t);
                for (int s = 0; s < slots.length; s++) {
                    Movie film = playing.get((day * 3 + t + s) % playing.size());
                    boolean bigFormat = theatre.getAmenities().contains("IMAX") && s == 3;
                    String screen = bigFormat ? "IMAX"
                            : "Screen " + ((s % theatre.getScreens()) + 1);
                    int totalSeats = bigFormat ? 220 : 100 + (s * 10);
                    int price = theatre.getBasePrice() + surcharge[s] + (bigFormat ? 100 : 0);
                    int booked = random.nextInt(totalSeats + 1);
                    if (serial % 17 == 0) {
                        booked = totalSeats; // one sold-out show: the UI must handle that too
                    }
                    list.add(new ShowTime(String.format("S%04d", serial), film.getMovieId(),
                            theatre.getTheatreId(), date, slots[s], screen, price,
                            totalSeats, booked));
                    serial++;
                }
            }
        }
        return list;
    }

    /** Reads the saved accent colour (hex such as "1B6EF3") into the Theme. */
    private void applyAccentFromSettings() {
        try {
            String hex = setting("accentColor", "1B6EF3");
            Theme.setAccent(new java.awt.Color(Integer.parseInt(hex, 16)));
        } catch (Exception e) {
            Theme.setAccent(Theme.BLUE); // bad value inside the file -> back to brand blue
        }
    }

    public List<CinemaHall> getHalls() { return java.util.Collections.unmodifiableList(halls); }
    public CinemaHall findHallById(String hallId) {
        for (CinemaHall h : halls) if (h.getHallId().equalsIgnoreCase(hallId)) return h; return null;
    }
    public CinemaHall findHallByRegistrationNumber(String regNum) {
        for (CinemaHall h : halls) if (h.getRegistrationNumber().equalsIgnoreCase(regNum)) return h; return null;
    }
    public List<CinemaHall> getHallsByStatus(CinemaHall.Status status) {
        List<CinemaHall> res = new ArrayList<>(); for (CinemaHall h : halls) if (h.getStatus() == status) res.add(h); return res;
    }
    public synchronized boolean addHall(CinemaHall hall) {
        if (hall == null || findHallByRegistrationNumber(hall.getRegistrationNumber()) != null) {
            return false;
        }
        if (hall.getHallId() == null || hall.getHallId().trim().isEmpty()
                || findHallById(hall.getHallId()) != null) {
            List<String> ids = new ArrayList<>();
            for (CinemaHall existing : halls) {
                ids.add(existing.getHallId());
            }
            hall.setHallId(IDGenerator.nextHallId(ids));
        }
        halls.add(hall); return saveHalls();
    }
    public synchronized boolean updateHall(CinemaHall hall) {
        for (int i = 0; i < halls.size(); i++) { if (halls.get(i).getHallId().equalsIgnoreCase(hall.getHallId())) { halls.set(i, hall); return saveHalls(); } }
        return false;
    }
    public synchronized boolean saveHalls() {
        return writeCatalogFile(hallsFile, HALL_HEADER, halls, CinemaHall::toDataLine);
    }
}
