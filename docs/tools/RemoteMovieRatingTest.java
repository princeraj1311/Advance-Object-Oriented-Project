package cinesmart.service;

/** Ensures the movie refresh prioritizes strong ratings with a meaningful vote floor. */
public class RemoteMovieRatingTest {

    public static void main(String[] args) {
        check("high-tier movie qualifies",
                RemoteMovieDataService.meetsRatingTier(7.4, 125, 6.5, 20));
        check("high-tier rating floor is applied",
                !RemoteMovieDataService.meetsRatingTier(6.4, 125, 6.5, 20));
        check("high-tier vote floor is applied",
                !RemoteMovieDataService.meetsRatingTier(8.0, 19, 6.5, 20));
        check("fallback still requires a 6.0 rating and five votes",
                RemoteMovieDataService.meetsRatingTier(6.0, 5, 6.0, 5));
        check("fallback rejects low ratings",
                !RemoteMovieDataService.meetsRatingTier(5.9, 500, 6.0, 5));
        System.out.println("RemoteMovieRatingTest: ALL CHECKS PASSED");
    }

    private static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }
}
