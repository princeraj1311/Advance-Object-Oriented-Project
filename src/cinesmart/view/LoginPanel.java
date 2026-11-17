package cinesmart.view;


import cinesmart.model.Admin;
import cinesmart.model.Customer;
import cinesmart.service.AuthResult;
import cinesmart.service.AuthService;
import cinesmart.service.LoginChallengeResult;
import cinesmart.util.Logo;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * LoginPanel is the first screen the user sees. It only SHOWS things and reports what the
 * user typed: deciding whether the login is correct is AuthService' job, and deciding what
 * happens next is MainFrame' job. That is the Model-View-Controller split in practice.
 */
public final class LoginPanel extends JPanel {

    private final MainFrame owner;
    private final AuthService authService;

    private final JTextField identifierField = UiUtil.textField(18);
    private final JTextField fullNameField = UiUtil.textField(18);
    private final JTextField emailField = UiUtil.textField(18);
    private final JTextField codeField = UiUtil.textField(18);
    private final JComboBox<String> roleCombo = new JComboBox<>(
            new String[] {Customer.ROLE, Admin.ROLE});
    private final JLabel identifierLabel = UiUtil.fieldLabel("Phone Number");
    private final JLabel fullNameLabel = UiUtil.fieldLabel("Customer Name");
    private final JLabel codeLabel = UiUtil.fieldLabel("Email Code");
    private final JLabel messageLabel = UiUtil.hint(" ");
    private final JButton sendCodeButton = UiUtil.primaryButton("Send Email Code",
            event -> requestCode());
    private final JButton verifyCodeButton = UiUtil.outlineButton("Verify Code",
            event -> verifyCode());
    private String pendingIdentifier;
    private String pendingFullName;
    private String pendingEmail;
    private String pendingRole;

    public LoginPanel(MainFrame owner, AuthService authService) {
        this.owner = owner;
        this.authService = authService;
        buildUi();
        wireEvents();
    }

    /* ------------------------------------------------------------------ *
     * Building the screen
     * ------------------------------------------------------------------ */

    private void buildUi() {
        setLayout(new BorderLayout());
        setBackground(Theme.WINDOW_BG);

        JPanel centre = new JPanel(new GridBagLayout());
        centre.setBackground(Theme.WINDOW_BG);
        centre.add(buildCard());
        add(centre, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setOpaque(false);
        footer.add(UiUtil.centered("Enter your details and press Enter to request an email code"));
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildCard() {
        FormPanel form = new FormPanel();
        form.setBackground(Theme.SURFACE);
        form.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(26, 30, 26, 30)));

        // Logo first, then the title: the card now looks like a brand card, not a plain form.
        form.addWide(new JLabel(Logo.badge(58)), true);
        form.addGap(4);
        form.addWide(UiUtil.heading("Login to CineSmart"), false);
        form.addWide(UiUtil.muted("Book seats, track tickets and get movie suggestions"), false);
        form.addGap(16);

        form.addRow(identifierLabel, identifierField);
        form.addRow(fullNameLabel, fullNameField);
        form.addRow("Account Email", emailField);
        form.addRow("Login As", roleCombo);
        codeField.setToolTipText("Enter the six-digit email verification code");
        codeLabel.setVisible(false);
        codeField.setVisible(false);
        form.addRow(codeLabel, codeField);

        form.addGap(6);
        form.addWide(messageLabel, false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        verifyCodeButton.setEnabled(false);
        buttons.add(sendCodeButton);
        buttons.add(verifyCodeButton);
        buttons.add(UiUtil.outlineButton("Register Now", event -> owner.showRegister()));
        form.addGap(8);
        form.addWide(buttons, false);
        return form;
    }

    private void wireEvents() {
        identifierField.addActionListener(event -> requestCode());
        fullNameField.addActionListener(event -> requestCode());
        emailField.addActionListener(event -> requestCode());
        codeField.addActionListener(event -> verifyCode());
        DocumentListener detailsListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                detailsChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                detailsChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                detailsChanged();
            }
        };
        identifierField.getDocument().addDocumentListener(detailsListener);
        fullNameField.getDocument().addDocumentListener(detailsListener);
        emailField.getDocument().addDocumentListener(detailsListener);
        roleCombo.addActionListener(event -> {
            detailsChanged();
            updateRoleLabels();
        });
    }

    private void detailsChanged() {
        boolean challengeWasActive = pendingIdentifier != null || codeField.isVisible();
        clearPendingChallenge();
        UiUtil.markInvalid(identifierField, false);
        UiUtil.markInvalid(fullNameField, false);
        UiUtil.markInvalid(emailField, false);
        UiUtil.setHint(messageLabel, challengeWasActive
                ? "Details changed. Request a new email code." : " ", true);
    }

    private void clearPendingChallenge() {
        if (pendingIdentifier == null && !codeField.isVisible()
                && !verifyCodeButton.isEnabled()) {
            return;
        }
        pendingIdentifier = null;
        pendingFullName = null;
        pendingEmail = null;
        pendingRole = null;
        codeField.setText("");
        codeField.setVisible(false);
        codeLabel.setVisible(false);
        verifyCodeButton.setEnabled(false);
        revalidate();
        repaint();
    }

    private void updateRoleLabels() {
        boolean admin = Admin.ROLE.equals(roleCombo.getSelectedItem());
        identifierLabel.setText(admin ? "Admin ID (12 digits)" : "Phone Number (10 digits)");
        fullNameLabel.setText(admin ? "Admin Name" : "Customer Name");
        identifierField.setToolTipText(admin ? "Enter the 12-digit admin ID"
                : "Enter the 10-digit customer phone number");
    }

    /* ------------------------------------------------------------------ *
     * Actions
     * ------------------------------------------------------------------ */

    private void requestCode() {
        clearPendingChallenge();
        UiUtil.markInvalid(identifierField, false);
        UiUtil.markInvalid(fullNameField, false);
        UiUtil.markInvalid(emailField, false);
        String identifier = UiUtil.textOf(identifierField);
        String fullName = UiUtil.textOf(fullNameField);
        String email = UiUtil.textOf(emailField);
        String role = (String) roleCombo.getSelectedItem();
        sendCodeButton.setEnabled(false);
        UiUtil.setHint(messageLabel, "Checking account and sending email code...", true);

        new SwingWorker<LoginChallengeResult, Void>() {
            @Override
            protected LoginChallengeResult doInBackground() {
                return authService.requestLoginCode(identifier, fullName, email, role);
            }

            @Override
            protected void done() {
                sendCodeButton.setEnabled(true);
                try {
                    LoginChallengeResult result = get();
                    if (!identifier.equals(UiUtil.textOf(identifierField))
                            || !fullName.equals(UiUtil.textOf(fullNameField))
                            || !email.equals(UiUtil.textOf(emailField))
                            || !role.equals(roleCombo.getSelectedItem())) {
                        UiUtil.setHint(messageLabel,
                                "Details changed. Request a new code for the current account.",
                                false);
                        return;
                    }
                    UiUtil.setHint(messageLabel, result.getMessage(), result.isSuccess());
                    if (result.isAccountDetailsMismatch()) {
                        UiUtil.markInvalid(identifierField, true);
                        UiUtil.markInvalid(fullNameField, true);
                        UiUtil.markInvalid(emailField, true);
                    }
                    if (!result.isSuccess()) {
                        return;
                    }
                    UiUtil.setHint(messageLabel, "Code sent to " + maskEmail(email)
                            + ". Enter it below to finish signing in.", true);
                    pendingIdentifier = identifier;
                    pendingFullName = fullName;
                    pendingEmail = email;
                    pendingRole = role;
                    codeLabel.setVisible(true);
                    codeField.setVisible(true);
                    verifyCodeButton.setEnabled(true);
                    codeField.setText("");
                    revalidate();
                    repaint();
                } catch (Exception problem) {
                    UiUtil.setHint(messageLabel, "Could not request the email code: "
                            + problem.getMessage(), false);
                }
            }
        }.execute();
    }

    private void verifyCode() {
        String code = UiUtil.textOf(codeField);
        if (!code.matches("[0-9]{6}")) {
            UiUtil.markInvalid(codeField, true);
            UiUtil.setHint(messageLabel, "Enter the six-digit code from your email.", false);
            return;
        }
        if (pendingIdentifier == null) {
            UiUtil.setHint(messageLabel, "Request an email code before verifying.", false);
            return;
        }
        AuthResult result = authService.verifyLoginCode(pendingIdentifier, pendingFullName,
                pendingEmail, pendingRole, code);
        UiUtil.setHint(messageLabel, result.getMessage(), result.isSuccess());
        if (result.isSuccess()) {
            pendingIdentifier = null;
            pendingFullName = null;
            pendingEmail = null;
            pendingRole = null;
            owner.loginSucceeded(result);
        }
    }

    private static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return email == null ? "" : email;
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}

