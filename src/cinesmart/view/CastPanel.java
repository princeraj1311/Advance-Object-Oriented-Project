package cinesmart.view;


import cinesmart.model.CastMember;
import cinesmart.model.Movie;
import cinesmart.service.CastService;
import cinesmart.util.PosterLoader;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * CastPanel is the "Cast" and "Crew" block of the movie details screen: two rows of picture tiles,
 * each one a portrait, the person's name and what they did.
 *
 * The tiles never wait for the network. {@link PosterLoader} is asked for the photograph and, while
 * it is still downloading (or for ever if it never arrives), the tile paints the same initials
 * avatar it would paint with no picture at all - so the row is complete on the very first frame,
 * which is what keeps the dialog from opening half empty.
 *
 * The panel owns no rule: CastService decides which names belong to the film and in what order,
 * and CastMember decides what the line under each name says.
 */
public final class CastPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** One tile: the size that fits five across a 700 px dialog. */
    public static final int TILE_WIDTH = 132;
    public static final int AVATAR_SIZE = 76;
    /**
     * Room below the portrait for the name and the role. Both are allowed two lines because a long
     * Indian name is common, and a height that fits one line would cut the second in half.
     */
    private static final int TEXT_HEIGHT = 66;

    private final Movie movie;
    private final CastService credits;

    public CastPanel(Movie movie, CastService credits) {
        this.movie = movie;
        this.credits = (credits == null) ? new CastService() : credits;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        add(row(CastMember.Group.CAST, "Cast", 6));
        add(UiUtil.gap(14));
        add(row(CastMember.Group.CREW, "Crew", 5));
        // A BoxLayout stretches its children to the full height; this panel is only ever as tall as
        // the two rows need, and the dialog decides the rest.
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    /**
     * One caption plus one row of tiles.
     *
     * The tiles live in a {@link WrapLayout}, so on a narrow dialog they wrap onto a second row
     * instead of being clipped - the same rule the poster grid follows.
     */
    private JComponent row(CastMember.Group group, String caption, int limit) {
        JPanel holder = new JPanel();
        holder.setOpaque(false);
        holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel heading = UiUtil.fieldLabel(caption);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.add(heading);
        holder.add(UiUtil.gap(8));

        List<CastMember> people = credits.firstOf(movie, group, limit);
        if (people.isEmpty()) {
            JLabel none = UiUtil.muted(group == CastMember.Group.CAST
                    ? "Cast not announced yet."
                    : "Crew not announced yet.");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            holder.add(none);
            holder.add(UiUtil.verticalGlue());
            return holder;
        }

        JPanel tiles = new JPanel(new WrapLayout(WrapLayout.LEFT, 14, 10));
        tiles.setOpaque(false);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (CastMember person : people) {
            tiles.add(new PersonTile(person));
        }
        holder.add(tiles);
        holder.add(UiUtil.verticalGlue());
        return holder;
    }

    /**
     * PersonTile is one portrait card: a round picture, the name underneath and the part.
     *
     * It paints itself rather than stacking three labels, because the circle with the photo inside
     * is what makes the row read as a credits list; a plain label row would look like a table.
     */
    private static final class PersonTile extends JPanel {

        private static final long serialVersionUID = 1L;

        private final CastMember person;
        private final Portrait portrait;

        PersonTile(CastMember person) {
            this.person = person;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(TILE_WIDTH, AVATAR_SIZE + TEXT_HEIGHT));
            setMaximumSize(new Dimension(TILE_WIDTH, AVATAR_SIZE + TEXT_HEIGHT));
            setMinimumSize(new Dimension(TILE_WIDTH, AVATAR_SIZE + TEXT_HEIGHT));

            portrait = new Portrait(person);
            portrait.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(portrait);
            add(UiUtil.gap(6));

            // The name and the role each wrap to two lines, because a long Indian name is common and
            // a fixed height would cut it in half. A read-only text area is used rather than a
            // JLabel with HTML: a JLabel reports the width of its whole text as its preferred
            // size, so "Maggie Gyllenhaal" and "Christopher Nolan" would be painted past the
            // edge of their tile and clipped instead of wrapping.
            UiUtil.Paragraph name = UiUtil.paragraph(person.getName(),
                    Theme.font(java.awt.Font.BOLD, 13), Theme.TEXT, TILE_WIDTH - 12);
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(name);

            UiUtil.Paragraph role = UiUtil.paragraph(person.subtitle(),
                    Theme.smallFont(), Theme.TEXT_MUTED, TILE_WIDTH - 12);
            role.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(role);

            String tip = person.getName() + " - " + person.subtitle();
            setToolTipText(tip);
            addMouseListener(new MouseAdapter() {
                private static final long serialVersionUID = 1L;

                @Override
                public void mouseClicked(MouseEvent event) {
                    UiUtil.info(PersonTile.this, tip);
                }
            });
            requestPhoto();
        }

        /** Ask for the portrait now; the tile shows initials until it lands. */
        private void requestPhoto() {
            if (!person.hasPhoto()) {
                return;
            }
            BufferedImage ready = PosterLoader.cached(person.getPhotoUrl());
            if (ready != null) {
                portrait.setImage(ready);
                return;
            }
            PosterLoader.load(person.getPhotoUrl(), image -> {
                if (image != null) {
                    portrait.setImage(image);
                }
            });
        }
    }
/**
     * Portrait is the round picture at the top of a tile.
     *
     * It clips the photo to a circle so a square portrait file still looks like a head shot, and
     * when there is no photo it paints a coloured disc with the person's initials, which is the
     * same idea every social app uses - so a crew member we have no picture of still looks like a
     * person rather than a blank hole.
     */
    private static final class Portrait extends JComponent {

        private static final long serialVersionUID = 1L;

        private final CastMember person;
        private BufferedImage image;

        Portrait(CastMember person) {
            this.person = person;
            Dimension size = new Dimension(AVATAR_SIZE, AVATAR_SIZE);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        /** The downloaded picture arrives off the Event Dispatch Thread; repaint on it. */
        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight());
            java.awt.Shape circle = new java.awt.geom.Ellipse2D.Float(
                    (getWidth() - size) / 2f, (getHeight() - size) / 2f, size, size);

            java.awt.Shape oldClip = g2.getClip();
            g2.setClip(circle);
            if (image != null) {
                PosterLoader.paintCover(g2, image, (getWidth() - size) / 2,
                        (getHeight() - size) / 2, size, size);
            } else {
                Color base = Theme.posterColor(person.getName().hashCode());
                g2.setPaint(Theme.gradient(base, base.darker(), false));
                g2.fill(circle);
                g2.setFont(Theme.titleFont(size / 3));
                g2.setColor(Color.WHITE);
                String initials = person.initials();
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(initials, (getWidth() - metrics.stringWidth(initials)) / 2,
                        getHeight() / 2 + metrics.getAscent() / 2 - 2);
            }
            g2.setClip(oldClip);
            // A hairline ring keeps a light photograph visible against the white card.
            g2.setColor(Theme.BORDER);
            g2.draw(circle);
            g2.dispose();
        }
    }
}
