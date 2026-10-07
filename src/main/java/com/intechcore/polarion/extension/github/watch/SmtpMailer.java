package com.intechcore.polarion.extension.github.watch;

import jakarta.activation.CommandInfo;
import jakarta.activation.CommandMap;
import jakarta.activation.DataContentHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.handlers.text_html;
import org.eclipse.angus.mail.handlers.text_plain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Sends mail through the SMTP server Polarion uses for its own notifications: the properties
 * {@code announcer.smtp.*} of {@code polarion.properties}, read as {@code mail.smtp.*}.
 */
public class SmtpMailer implements Mailer {

    private static final String ANNOUNCER_PREFIX = "announcer.";

    private final Properties properties;
    private final String from;

    /**
     * @param system the system properties of Polarion
     * @param from   the sender, or null for the SMTP user
     */
    public SmtpMailer(@NotNull Properties system, @Nullable String from) {
        properties = new Properties();
        system.forEach((key, value) -> {
            if (key.toString().startsWith(ANNOUNCER_PREFIX)) {
                properties.put("mail." + key.toString().substring(ANNOUNCER_PREFIX.length()), value);
            }
        });
        String user = properties.getProperty("mail.smtp.user");
        this.from = from != null && !from.isBlank() ? from.trim() : user;
    }

    /** The SMTP server and the sender to use, or the reason the mail cannot go. */
    public @Nullable String problem() {
        if (properties.getProperty("mail.smtp.host") == null) {
            return "No SMTP server: Polarion needs announcer.smtp.host in polarion.properties";
        }
        if (from == null || !from.contains("@")) {
            return "No sender: set the property mail.from of the extension, or announcer.smtp.user to an address";
        }
        return null;
    }

    @Override
    public void send(@NotNull List<String> recipients, @NotNull String subject, @NotNull String html) {
        String reason = problem();
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
        TextHandlers.register();
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(properties, authenticator()));
            message.setFrom(new InternetAddress(from));
            for (String recipient : recipients) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setContent(html, "text/html; charset=UTF-8");
            Transport.send(message, message.getAllRecipients());
        } catch (MessagingException e) {
            throw new IllegalStateException("The mail to " + String.join(", ", recipients) + " failed: " + e.getMessage(), e);
        }
    }

    private @Nullable Authenticator authenticator() {
        String user = properties.getProperty("mail.smtp.user");
        String password = properties.getProperty("mail.smtp.password");
        if (user == null || password == null) {
            return null;
        }
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        };
    }

    /**
     * Polarion 2606 ships Jakarta Activation without its provider, so the mail library finds no handler
     * for a text or HTML body ("no object DCH for MIME type text/html"). This map supplies the two the
     * mails need, from Polarion's jakarta.mail bundle, and leaves every other type to the map it wraps.
     * The SBB mailworkflow extension solves the same gap the same way.
     */
    static final class TextHandlers extends CommandMap {

        private final CommandMap delegate;

        private TextHandlers(CommandMap delegate) {
            this.delegate = delegate;
        }

        static synchronized void register() {
            CommandMap current = CommandMap.getDefaultCommandMap();
            if (!(current instanceof TextHandlers)) {
                CommandMap.setDefaultCommandMap(new TextHandlers(current));
            }
        }

        @Override
        public DataContentHandler createDataContentHandler(String mimeType) {
            String base = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            Map<String, DataContentHandler> handlers = Map.of("text/html", new text_html(), "text/plain", new text_plain());
            DataContentHandler handler = handlers.get(base);
            return handler != null ? handler : delegate.createDataContentHandler(mimeType);
        }

        @Override
        public CommandInfo[] getPreferredCommands(String mimeType) {
            return delegate.getPreferredCommands(mimeType);
        }

        @Override
        public CommandInfo[] getAllCommands(String mimeType) {
            return delegate.getAllCommands(mimeType);
        }

        @Override
        public CommandInfo getCommand(String mimeType, String cmdName) {
            return delegate.getCommand(mimeType, cmdName);
        }
    }
}
