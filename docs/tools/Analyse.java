import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Reads the captured PNGs back and measures them, to prove they are real renderings. */
public class Analyse {

    public static void main(String[] args) throws Exception {
        File folder = new File("/Users/princeraj/Downloads/Movie Ticket AOOP/docs/screenshots");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".png"));
        java.util.Arrays.sort(files);
        for (File file : files) {
            report(file);
        }
    }

    private static void report(File file) throws Exception {
        BufferedImage image = ImageIO.read(file);
        int width = image.getWidth();
        int height = image.getHeight();
        int blue = 0;
        int saturated = 0;
        int nonWhite = 0;
        int contentRows = 0;
        int blueRows = 0;
        for (int y = 0; y < height; y++) {
            int rowNonWhite = 0;
            int rowBlue = 0;
            for (int x = 0; x < width; x += 2) {
                Color colour = new Color(image.getRGB(x, y));
                if (!colour.equals(Color.WHITE)) {
                    nonWhite++;
                    rowNonWhite++;
                }
                if (near(colour, 27, 110, 243) || near(colour, 16, 78, 178)) {
                    blue++;
                    rowBlue++;
                }
                if (isSaturated(colour)) {
                    saturated++;
                }
            }
            if (rowNonWhite > width / 6) {
                contentRows++;
            }
            if (rowBlue > width / 4) {
                blueRows++;
            }
        }
        int sampled = (height * width) / 2;
        System.out.printf("%-26s %4dx%-4d nonWhite=%5.1f%%  blue=%5d  vivid=%5.1f%%"
                        + "  rowsWithContent=%d/%d  blueHeaderRows=%d%n",
                file.getName(), width, height,
                100.0 * nonWhite / sampled, blue, 100.0 * saturated / sampled,
                contentRows, height, blueRows);
    }

    private static boolean near(Color colour, int r, int g, int b) {
        return Math.abs(colour.getRed() - r) < 26
                && Math.abs(colour.getGreen() - g) < 26
                && Math.abs(colour.getBlue() - b) < 26;
    }

    /** Poster gradients, green/red/orange status lines - anything with real colour in it. */
    private static boolean isSaturated(Color colour) {
        int max = Math.max(colour.getRed(), Math.max(colour.getGreen(), colour.getBlue()));
        int min = Math.min(colour.getRed(), Math.min(colour.getGreen(), colour.getBlue()));
        return max - min > 70;
    }
}
