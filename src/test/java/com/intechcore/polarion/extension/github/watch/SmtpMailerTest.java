package com.intechcore.polarion.extension.github.watch;

import jakarta.activation.CommandInfo;
import jakarta.activation.CommandMap;
import jakarta.activation.DataContentHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpMailerTest {

    private final CommandMap original = CommandMap.getDefaultCommandMap();

    @AfterEach
    void restore() {
        CommandMap.setDefaultCommandMap(original);
    }

    private static Properties polarion(String... keysAndValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }

    /** The mails go through the SMTP server of Polarion's own notifications, and need a sender. */
    @Test
    void namesWhatTheMailLacks() {
        assertThat(new SmtpMailer(polarion(), null).problem()).contains("announcer.smtp.host");
        assertThat(new SmtpMailer(polarion("announcer.smtp.host", "smtp.example"), null).problem()).contains("No sender");
        assertThat(new SmtpMailer(polarion("announcer.smtp.host", "smtp.example", "announcer.smtp.user", "polarion"), null).problem())
                .contains("No sender");
        assertThat(new SmtpMailer(polarion("announcer.smtp.host", "smtp.example", "announcer.smtp.user", "bot@example.com"), null).problem())
                .isNull();
        assertThat(new SmtpMailer(polarion("announcer.smtp.host", "smtp.example"), " github@example.com ").problem()).isNull();
    }

    @Test
    void refusesToSendWithoutAServer() {
        SmtpMailer mailer = new SmtpMailer(polarion(), "github@example.com");

        assertThatThrownBy(() -> mailer.send(List.of("alice@example.com"), "Subject", "<p>Body</p>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("announcer.smtp.host");
    }

    /** A server that cannot be reached fails the mail with its reason, which the job reports for the repository. */
    @Test
    void reportsAMailThatCouldNotGo() {
        SmtpMailer mailer = new SmtpMailer(polarion("announcer.smtp.host", "127.0.0.1", "announcer.smtp.port", "1",
                "announcer.smtp.connectiontimeout", "1000", "announcer.smtp.user", "bot@example.com", "announcer.smtp.password", "secret"), null);

        assertThatThrownBy(() -> mailer.send(List.of("alice@example.com"), "Subject", "<p>Body</p>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("alice@example.com");
    }

    /** Polarion 2606 has no activation provider: the map supplies the text handlers and leaves the rest to the one it wraps. */
    @Test
    void suppliesTheTextHandlersAndKeepsTheRest() {
        CommandMap wrapped = mock(CommandMap.class);
        DataContentHandler other = mock(DataContentHandler.class);
        CommandInfo[] commands = new CommandInfo[0];
        when(wrapped.createDataContentHandler("image/png")).thenReturn(other);
        when(wrapped.getPreferredCommands("image/png")).thenReturn(commands);
        when(wrapped.getAllCommands("image/png")).thenReturn(commands);
        CommandMap.setDefaultCommandMap(wrapped);

        SmtpMailer.TextHandlers.register();
        SmtpMailer.TextHandlers.register();
        CommandMap map = CommandMap.getDefaultCommandMap();

        assertThat(map).isInstanceOf(SmtpMailer.TextHandlers.class);
        assertThat(map.createDataContentHandler("text/html; charset=UTF-8")).isNotNull();
        assertThat(map.createDataContentHandler("TEXT/PLAIN")).isNotNull();
        assertThat(map.createDataContentHandler("image/png")).isSameAs(other);
        assertThat(map.getPreferredCommands("image/png")).isSameAs(commands);
        assertThat(map.getAllCommands("image/png")).isSameAs(commands);
        assertThat(map.getCommand("image/png", "view")).isNull();
    }
}
