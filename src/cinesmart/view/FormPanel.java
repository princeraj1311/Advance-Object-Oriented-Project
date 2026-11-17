package cinesmart.view;


import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * FormPanel lays components out as "label on the left, field on the right" rows, which is
 * what every form in CineSmart needs. GridBagLayout is used because it keeps the labels
 * aligned even when the window is resized.
 *
 * LoginPanel and RegisterPanel both reuse this panel, so the two screens look identical -
 * that is the advantage of writing a small layout class once.
 */
public class FormPanel extends JPanel {

    /** Label column width, so the input boxes of every screen start at the same x. */
    private static final int LABEL_WIDTH = 150;

    private int row = 0;

    public FormPanel() {
        super(new GridBagLayout());
        setBackground(Theme.SURFACE);
    }

    /** Adds one "label + input" row and returns the row number that was used. */
    public int addRow(String labelText, JComponent field) {
        return addRow(UiUtil.fieldLabel(labelText), field);
    }

    /** Adds one row with a reusable label, useful when the field label changes by role. */
    public int addRow(javax.swing.JLabel label, JComponent field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        labelConstraints.insets = new Insets(5, 0, 5, 14);
        add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.anchor = GridBagConstraints.WEST;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.insets = new Insets(5, 0, 5, 0);
        if (field.getPreferredSize().width == 0) {
            field.setPreferredSize(new Dimension(240, 34));
        }
        add(field, fieldConstraints);
        return row++;
    }

    /** Adds a component that uses the whole width (buttons, check boxes, messages). */
    public void addWide(JComponent component, boolean centered) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.anchor = centered ? GridBagConstraints.CENTER : GridBagConstraints.WEST;
        constraints.fill = centered ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 0, 5, 0);
        add(component, constraints);
        row++;
    }

    /** Empty vertical space between two sections of the same form. */
    public void addGap(int pixels) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(10, pixels));
        add(spacer, constraints);
        row++;
    }

    /** Keeps the label column a fixed width on every screen. */
    public int labelColumnWidth() {
        return LABEL_WIDTH;
    }
}
