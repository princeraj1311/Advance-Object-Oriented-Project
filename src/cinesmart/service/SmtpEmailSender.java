package cinesmart.service;

import cinesmart.util.AppConfig;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Minimal SMTP client for sending sign-in codes without an additional library dependency. */
final class SmtpEmailSender {

    private static final int TIMEOUT_MILLIS = 12_000;

    private SmtpEmailSender() {
    }

    static void send(String recipient, String subject, String body) throws IOException {
        if (!AppConfig.isSmtpConfigured()) {
            throw new IOException("Email sign-in is not configured. Set smtpUser and smtpPass "
                    + "in the local .env file.");
        }
        if (AppConfig.SMTP_HOST.contains("@") || AppConfig.SMTP_HOST.contains("://")) {
            throw new IOException("smtpHost must be an SMTP server name, not an email address. "
                    + "For Gmail, set smtpHost=smtp.gmail.com.");
        }
        String smtpPassword = AppConfig.SMTP_PASS.replaceAll("\\s+", "");
        if ("smtp.gmail.com".equalsIgnoreCase(AppConfig.SMTP_HOST)
                && smtpPassword.length() != 16) {
            throw new IOException("Gmail smtpPass must be a 16-character Google App Password "
                    + "created for the smtpUser account. Do not use your regular Gmail password.");
        }
        String to = cleanHeader(recipient);
        if (to.isEmpty() || !to.contains("@")) {
            throw new IOException("The account email address is invalid.");
        }
        String from = AppConfig.SMTP_USER;
        String fromHeader = cleanHeader(AppConfig.SMTP_FROM);
        if (fromHeader.isEmpty()) {
            fromHeader = from;
        }

        Socket socket = new Socket();
        SSLSocketFactory sslFactory =
                (SSLSocketFactory) SSLSocketFactory.getDefault();
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            socket.connect(new InetSocketAddress(AppConfig.SMTP_HOST, AppConfig.SMTP_PORT),
                    TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);

            if (AppConfig.SMTP_PORT == 465) {
                socket = (SSLSocket) sslFactory.createSocket(socket, AppConfig.SMTP_HOST,
                        AppConfig.SMTP_PORT, true);
                ((SSLSocket) socket).startHandshake();
            }
            reader = reader(socket);
            writer = writer(socket);
            expect(readReply(reader), 220, "SMTP greeting");
            command(writer, reader, "EHLO cinesmart.local", 250, "SMTP hello");

            if (AppConfig.SMTP_PORT == 587) {
                command(writer, reader, "STARTTLS", 220, "SMTP TLS");
                socket = (SSLSocket) sslFactory.createSocket(socket, AppConfig.SMTP_HOST,
                        AppConfig.SMTP_PORT, true);
                ((SSLSocket) socket).startHandshake();
                reader = reader(socket);
                writer = writer(socket);
                command(writer, reader, "EHLO cinesmart.local", 250, "SMTP hello after TLS");
            } else if (AppConfig.SMTP_PORT != 465) {
                throw new IOException("Use Gmail SMTP port 587 (STARTTLS) or 465 (SSL).");
            }

            command(writer, reader, "AUTH LOGIN", 334, "SMTP authentication");
            command(writer, reader, encode(AppConfig.SMTP_USER), 334, "SMTP username");
            command(writer, reader, encode(smtpPassword), 235, "SMTP password");
            command(writer, reader, "MAIL FROM:<" + cleanAddress(from) + ">", 250,
                    "SMTP sender");
            command(writer, reader, "RCPT TO:<" + cleanAddress(to) + ">", 250,
                    "SMTP recipient");
            command(writer, reader, "DATA", 354, "SMTP message");
            writeLine(writer, "From: " + fromHeader);
            writeLine(writer, "To: " + to);
            writeLine(writer, "Subject: " + cleanHeader(subject));
            writeLine(writer, "MIME-Version: 1.0");
            writeLine(writer, "Content-Type: text/plain; charset=UTF-8");
            writeLine(writer, "Content-Transfer-Encoding: 8bit");
            writeLine(writer, "");
            for (String line : (body == null ? "" : body).split("\\r?\\n", -1)) {
                writeLine(writer, line.startsWith(".") ? "." + line : line);
            }
            writeLine(writer, ".");
            expect(readReply(reader), 250, "SMTP message delivery");
            writeLine(writer, "QUIT");
        } finally {
            close(reader);
            close(writer);
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connection is already being closed.
            }
        }
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.UTF_8));
    }

    private static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                StandardCharsets.UTF_8));
    }

    private static void command(BufferedWriter writer, BufferedReader reader, String command,
                               int expectedCode, String step) throws IOException {
        writeLine(writer, command);
        expect(readReply(reader), expectedCode, step);
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    private static String readReply(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.length() < 3) {
            throw new IOException("SMTP server closed the connection unexpectedly.");
        }
        StringBuilder reply = new StringBuilder(line);
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("SMTP server closed the connection unexpectedly.");
            }
            reply.append(' ').append(line);
        }
        return reply.toString();
    }

    private static void expect(String reply, int expectedCode, String step) throws IOException {
        int actualCode;
        try {
            actualCode = Integer.parseInt(reply.substring(0, 3));
        } catch (RuntimeException e) {
            throw new IOException(step + " failed: invalid SMTP response.", e);
        }
        if (actualCode != expectedCode) {
            if ((step.equals("SMTP username") || step.equals("SMTP password"))
                    && (actualCode == 534 || actualCode == 535)) {
                throw new IOException("Gmail rejected the SMTP credentials. Set smtpUser to "
                        + "the full Gmail address and smtpPass to a current Google App Password "
                        + "created for that same account. Do not use the regular Gmail password.");
            }
            throw new IOException(step + " failed: " + reply);
        }
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String cleanHeader(String text) {
        return text == null ? "" : text.replace("\r", "").replace("\n", "").trim();
    }

    private static String cleanAddress(String text) {
        String clean = cleanHeader(text);
        int open = clean.indexOf('<');
        int close = clean.indexOf('>');
        return open >= 0 && close > open ? clean.substring(open + 1, close) : clean;
    }

    private static void close(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Closing a completed SMTP stream is best-effort.
            }
        }
    }
}
