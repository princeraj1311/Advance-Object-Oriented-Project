package cinesmart.view;


import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * WrapLayout is FlowLayout with an honest preferred size.
 *
 * The plain FlowLayout says "I need one row that fits everything", even though at run time it
 * breaks the components over several rows. Inside a JScrollPane that is a real bug and not a
 * cosmetic one: the scroll pane sizes the poster grid for ONE row, so the second row of films is
 * drawn outside the view and the scroll bar never reaches it. The same lie clips the filter row
 * when the window is made narrow.
 *
 * This subclass counts the rows exactly the way FlowLayout will lay them out, for the width the
 * container really has, so preferredLayoutSize grows with the number of rows. The poster grid
 * and the filter row both use it, which is why the whole application can be resized from 960 px
 * to full screen without hiding anything.
 */
public class WrapLayout extends FlowLayout {

    private static final long serialVersionUID = 1L;

    public WrapLayout(int alignment, int horizontalGap, int verticalGap) {
        super(alignment, horizontalGap, verticalGap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return rowSize(target, false);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        // The smallest usable width is the widest single component: never claim one whole row.
        return rowSize(target, true);
    }

    /**
     * @param widestComponentOnly true for the minimum size (one component per row), false for
     *                            the preferred size (as many components per row as fit)
     */
    private Dimension rowSize(Container target, boolean widestComponentOnly) {
        synchronized (target.getTreeLock()) {
            int targetWidth = measuredWidth(target);
            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int available = targetWidth - insets.left - insets.right;

            int rowWidth = 0;
            int rowHeight = 0;
            int widestRow = 0;
            int totalHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component member = target.getComponent(i);
                if (!member.isVisible()) {
                    continue;
                }
                Dimension size = member.getPreferredSize();
                int needed = (rowWidth == 0) ? size.width : hgap + size.width;
                boolean startsNewRow = (rowWidth > 0)
                        && (widestComponentOnly || rowWidth + needed > available);
                if (startsNewRow) {
                    widestRow = Math.max(widestRow, rowWidth);
                    totalHeight += rowHeight + vgap;
                    rowWidth = size.width;
                    rowHeight = size.height;
                } else {
                    rowWidth += needed;
                    rowHeight = Math.max(rowHeight, size.height);
                }
            }

            widestRow = Math.max(widestRow, rowWidth);
            totalHeight += rowHeight; // the last row carries no trailing gap
            return new Dimension(widestRow + insets.left + insets.right,
                    totalHeight + insets.top + insets.bottom);
        }
    }

    /**
     * The width the rows will really be laid out in. Before the first layout the container has
     * no width yet, so the width of whatever holds it is used; if nothing is known the layout
     * falls back to a single row, exactly like FlowLayout.
     */
    private int measuredWidth(Container target) {
        if (target.getWidth() > 0) {
            return target.getWidth();
        }
        Container parent = target.getParent();
        if (parent != null && parent.getWidth() > 0) {
            return parent.getWidth();
        }
        return Integer.MAX_VALUE;
    }
}
