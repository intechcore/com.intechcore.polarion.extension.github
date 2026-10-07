package com.intechcore.polarion.extension.github.watch;

import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ItemKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tells the items a repository did not have at the last check. It keeps what it saw in memory only:
 * the watch job writes nothing into Polarion. After a start of Polarion it knows nothing yet, and
 * takes an item as new only when it appeared within the last check interval, so the first check
 * sends no flood of old items.
 */
public class NewItemsWatcher {

    private final Map<String, Set<String>> known = new ConcurrentHashMap<>();

    /**
     * @param key        the repository setting, for example {@code elibrary/tool}
     * @param candidates the items that call for a notification now
     * @param now        the time of the check
     * @param interval   how often the job runs: the window of the first check
     * @return the candidates not seen at the last check
     */
    public @NotNull List<ImportEntry> news(@NotNull String key, @NotNull List<ImportEntry> candidates, @NotNull Instant now,
                                           @NotNull Duration interval) {
        Set<String> urls = candidates.stream().map(ImportEntry::getUrl).collect(Collectors.toSet());
        Set<String> before = known.put(key, urls);
        if (before == null) {
            Instant since = now.minus(interval);
            return candidates.stream().filter(entry -> appearedAfter(entry, since)).toList();
        }
        // An item that left, for example by getting a work item, and comes back is new again.
        return candidates.stream().filter(entry -> !before.contains(entry.getUrl())).toList();
    }

    /** An issue or discussion appears when it is opened, a failed pull request when its checks fail, at its last change. */
    private static boolean appearedAfter(ImportEntry entry, Instant since) {
        Instant appeared = instant(entry.getKind() == ItemKind.PULL_REQUEST ? entry.getUpdatedAt() : entry.getCreatedAt());
        return appeared != null && appeared.isAfter(since);
    }

    private static @Nullable Instant instant(@Nullable String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
