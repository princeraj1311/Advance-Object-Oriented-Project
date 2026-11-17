package cinesmart.view;


import cinesmart.model.Customer;
import cinesmart.service.AuthResult;
import cinesmart.service.AuthService;
import cinesmart.util.Logo;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.AdjustmentEvent;
import java.beans.PropertyChangeListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * RegisterPanel collects everything needed to create a Customer account.
 *
 * The form is long, so it lives inside a JScrollPane (one of the project requirements).
 * Two Swing details are worth pointing out: the Terms check box stays disabled until the
 * user has scrolled the terms to the bottom, and every rule is checked by AuthService so
 * the same logic can be tested without opening a window.
 */
public final class RegisterPanel extends JPanel {

    /** Short version of the rules; a real product would load this text from a file. */
    private static final String TERMS_HTML = "<html><body style='font-size:12px'>"
            + "<b>CineSmart terms of use</b><br>"
            + "1. Tickets once booked may be cancelled up to 60 minutes before the show.<br>"
            + "2. Please reach the auditorium 10 minutes before the show time.<br>"
            + "3. Outside food and beverages are not allowed inside the auditorium.<br>"
            + "4. Mobile phones must be silenced during the screening.<br>"
            + "5. Your account details are stored with an encrypted password.<br>"
            + "6. Misuse of the refund system may lead to account suspension.<br>"
            + "7. Booking is subject to theatre capacity and the CBFC certificate.<br>"
            + "8. CineSmart is a student project and no real payment is processed.<br>"
            + "</body></html>";

    private final MainFrame owner;
    private final AuthService authService;

    private final JTextField userNameField = UiUtil.textField(18);
    private final JTextField fullNameField = UiUtil.textField(18);
    private final JTextField emailField = UiUtil.textField(18);
    private final JFormattedTextField mobileField = UiUtil.mobileField();
    private final JComboBox<String> cityCombo = new JComboBox<>(Session.CITIES);
    private final JFormattedTextField dobField = UiUtil.dateField();
    private final JComboBox<String> seatCombo = new JComboBox<>(Session.SEAT_PREFERENCES);
    private final JPasswordField passwordField = UiUtil.passwordField(18);
    private final JPasswordField confirmPasswordField = UiUtil.passwordField(18);

    private final ButtonGroup genderGroup = new ButtonGroup();
    private final JRadioButton maleRadio = new JRadioButton("Male");
    private final JRadioButton femaleRadio = new JRadioButton("Female");
    private final JRadioButton otherRadio = new JRadioButton("Other");

    private final StrengthBar strengthBar = new StrengthBar();
    private final JEditorPane termsPane = new JEditorPane("text/html", TERMS_HTML);
    private final JScrollPane termsScroll = new JScrollPane(termsPane);
    private final JCheckBox termsCheck =
            new JCheckBox("I have read and accepted the Terms & Conditions");
    private final JLabel userHint = UiUtil.hint("User name: 3-15 letters, digits or _");
    private final JLabel matchHint = UiUtil.hint("Use at least one letter and one digit");

    /** Set to true once the terms have been scrolled to the end. */
    private boolean termsRead = false;

    public RegisterPanel(MainFrame owner, AuthService authService) {
        this.owner = owner;
        this.authService = authService;
        buildUi();
        wireEvents();
    }

    /* ------------------------------------------------------------------ *
     * Layout
     * ------------------------------------------------------------------ */

    private void buildUi() {
        setLayout(new BorderLayout());
        setBackground(Theme.WINDOW_BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(UiUtil.padded(18, 26, 8, 26));
        // Badge next to the title so the register screen carries the same branding.
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(Logo.badge(38)));
        brand.add(UiUtil.heading("Create your CineSmart account"));
        header.add(brand, BorderLayout.WEST);
        header.add(UiUtil.muted("Already registered? Use the Login screen"), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        FormPanel form = new FormPanel();
        form.setBackground(Theme.SURFACE);
        form.setBorder(UiUtil.padded(8, 26, 24, 26));

        form.addRow("User Name", userNameField);
        form.addWide(userHint, false);
        form.addRow("Full Name", fullNameField);
        form.addRow("E-mail Address", emailField);
        form.addRow("Mobile Number", mobileField);
        form.addRow("City", cityCombo);
        form.addRow("Date of Birth", dobField);
        form.addRow("Gender", genderPanel());
        form.addRow("Seat Preference", seatCombo);
        form.addRow("Password", passwordField);
        form.addWide(strengthBar, false);
        form.addRow("Confirm Password", confirmPasswordField);
        form.addWide(matchHint, false);
        form.addGap(10);

        termsPane.setEditable(false);
        termsPane.setBackground(Theme.SURFACE_SOFT);
        termsScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        termsScroll.setPreferredSize(new Dimension(430, 130));
        form.addRow("Terms & Conditions", termsScroll);

        termsCheck.setEnabled(false); // enabled only after the terms have been scrolled
        form.addWide(termsCheck, false);

        JScrollPane scroller = new JScrollPane(form, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getViewport().setBackground(Theme.SURFACE);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        add(scroller, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        footer.setOpaque(false);
        footer.setBorder(UiUtil.padded(0, 26, 0, 26));
        footer.add(UiUtil.primaryButton("Register", event -> attemptRegister()));
        footer.add(UiUtil.outlineButton("Clear Form", event -> clearForm()));
        footer.add(UiUtil.outlineButton("Back to Login", event -> owner.showLogin()));
        add(footer, BorderLayout.SOUTH);
    }

    /** Radio buttons inside a ButtonGroup: only one gender can be selected. */
    private JPanel genderPanel() {
        genderGroup.add(maleRadio);
        genderGroup.add(femaleRadio);
        genderGroup.add(otherRadio);
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        panel.add(maleRadio);
        panel.add(femaleRadio);
        panel.add(otherRadio);
        return panel;
    }

    /* ------------------------------------------------------------------ *
     * Live feedback while typing
     * ------------------------------------------------------------------ */

    private void wireEvents() {
        // Editable combo: the user may also type a city that is not in the list yet.
        cityCombo.setEditable(true);
        cityCombo.setSelectedItem(Session.selectedCity());

        onTextChanged(userNameField, this::checkUserName);
        onTextChanged(passwordField, () -> {
            strengthBar.evaluate(UiUtil.textOf(passwordField));
            checkPasswordMatch();
        });
        onTextChanged(confirmPasswordField, this::checkPasswordMatch);

        // The check box unlocks only once the terms have been read to the end.
        termsScroll.getVerticalScrollBar().addAdjustmentListener(this::revealTermsIfScrolled);
    }

    /** Reusable "run this when the text changes" wiring (one place, three listeners). */
    private void onTextChanged(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        });
    }

    private void checkUserName() {
        String name = UiUtil.textOf(userNameField);
        if (name.isEmpty()) {
            UiUtil.setHint(userHint, "User name: 3-15 letters, digits or _", true);
        } else if (authService.isUserNameTaken(name)) {
            UiUtil.setHint(userHint, "That user name is already taken", false);
        } else {
            UiUtil.setHint(userHint, "Good - that user name is still free", true);
        }
    }

    private void checkPasswordMatch() {
        String first = UiUtil.textOf(passwordField);
        String second = UiUtil.textOf(confirmPasswordField);
        if (second.isEmpty()) {
            UiUtil.setHint(matchHint, "Type the same password once more", true);
        } else if (first.equals(second)) {
            UiUtil.setHint(matchHint, "Passwords match", true);
        } else {
            UiUtil.setHint(matchHint, "Passwords do not match yet", false);
        }
    }

    private void revealTermsIfScrolled(AdjustmentEvent event) {
        if (termsRead) {
            return;
        }
        JScrollBar bar = termsScroll.getVerticalScrollBar();
        // JScrollBar calls the visible window "visible amount" - there is no getExtent().
        boolean reachedBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 2;
        if (reachedBottom) {
            termsRead = true;
            termsCheck.setEnabled(true);
            termsCheck.setText("I accept the Terms & Conditions (thanks for reading!)");
        }
    }

    /* ------------------------------------------------------------------ *
     * Turning the screen into an object, then asking the service to save it
     * ------------------------------------------------------------------ */

    /** Builds a Customer exactly as typed - validation happens in AuthService. */
    private Customer collectDraft() {
        Customer draft = new Customer(UiUtil.textOf(userNameField), UiUtil.textOf(fullNameField),
                UiUtil.textOf(emailField), UiUtil.formattedDigits(mobileField),
                String.valueOf(cityCombo.getSelectedItem()), textOf(dobField), selectedGender());
        draft.setSeatPreference(String.valueOf(seatCombo.getSelectedItem()));
        return draft;
    }

    private String selectedGender() {
        if (maleRadio.isSelected()) {
            return "Male";
        }
        if (femaleRadio.isSelected()) {
            return "Female";
        }
        if (otherRadio.isSelected()) {
            return "Other";
        }
        return "";
    }

    /** Reads a JFormattedTextField after committing the typed text to its formatter. */
    private static String textOf(JFormattedTextField field) {
        try {
            field.commitEdit();
        } catch (Exception ignored) {
            // Incomplete text simply stays as it is and fails the validation rule below.
        }
        return UiUtil.clean(field.getText());
    }

    /* ------------------------------------------------------------------ *
     * Actions
     * ------------------------------------------------------------------ */

    private void attemptRegister() {
        Customer draft = collectDraft();
        String password = UiUtil.textOf(passwordField);
        String confirmation = UiUtil.textOf(confirmPasswordField);
        boolean accepted = termsCheck.isSelected();

        java.util.List<String> problems =
                authService.registrationProblems(draft, password, confirmation, accepted);
        if (!problems.isEmpty()) {
            UiUtil.showProblems(this, "Please correct " + problems.size() + " field(s):", problems);
            return;
        }

        AuthResult result = authService.register(draft, password, confirmation, accepted);
        if (result.isSuccess()) {
            clearForm();
            owner.registerCompleted(result);
        } else {
            UiUtil.error(this, result.getMessage());
        }
    }

    /** Puts the screen back to its starting state after a successful registration. */
    private void clearForm() {
        for (JTextField field : new JTextField[] {userNameField, fullNameField, emailField,
                mobileField, dobField, passwordField, confirmPasswordField}) {
            field.setText("");
        }
        genderGroup.clearSelection();
        cityCombo.setSelectedItem(Session.CITIES[0]);
        seatCombo.setSelectedItem(Session.SEAT_PREFERENCES[0]);
        strengthBar.evaluate("");
        checkUserName();
        checkPasswordMatch();
    }
}