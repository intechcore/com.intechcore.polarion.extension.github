package com.intechcore.polarion.extension.github.watch;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Sends an HTML mail. */
public interface Mailer {

    void send(@NotNull List<String> recipients, @NotNull String subject, @NotNull String html);
}
