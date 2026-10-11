package com.intechcore.polarion.extension.github.watch;

import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ItemKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewItemsWatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Duration INTERVAL = Duration.ofMinutes(15);

    private static ImportEntry item(long number, ItemKind kind, String created, String updated) {
        return ImportEntry.builder().number(number).kind(kind).url("https://github.com/acme/tool/x/" + number)
                .createdAt(created).updatedAt(updated).build();
    }

    /** After a start of Polarion only what appeared within the interval is new: no flood of old items. */
    @Test
    void takesOnlyTheItemsOfTheLastIntervalAtTheFirstCheck() {
        List<ImportEntry> candidates = List.of(
                item(1, ItemKind.ISSUE, "2026-10-07T11:50:00Z", null),
                item(2, ItemKind.ISSUE, "2026-10-01T09:00:00Z", "2026-10-07T11:55:00Z"),
                item(3, ItemKind.PULL_REQUEST, "2026-10-01T09:00:00Z", "2026-10-07T11:58:00Z"),
                item(4, ItemKind.DISCUSSION, null, null),
                item(5, ItemKind.ISSUE, "not a time", null));

        assertThat(new NewItemsWatcher().news("elibrary/tool", candidates, NOW, INTERVAL))
                .extracting(ImportEntry::getNumber).containsExactly(1L, 3L);
    }

    @Test
    void reportsWhatWasNotThereAtTheLastCheckAndWhatComesBack() {
        NewItemsWatcher watcher = new NewItemsWatcher();
        ImportEntry old = item(1, ItemKind.ISSUE, "2026-10-01T09:00:00Z", null);
        ImportEntry fresh = item(2, ItemKind.ISSUE, "2026-10-01T09:00:00Z", null);
        // Each check is remembered once its news went out, as the watch job does.
        watcher.remember("elibrary/tool", List.of(old), List.of());

        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW.plus(INTERVAL), INTERVAL)).containsExactly(fresh);
        watcher.remember("elibrary/tool", List.of(old, fresh), List.of());
        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW.plus(INTERVAL.multipliedBy(2)), INTERVAL)).isEmpty();
        // #2 got a work item and left; when it is back without one, it is new again.
        watcher.remember("elibrary/tool", List.of(old), List.of());
        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW, INTERVAL)).containsExactly(fresh);
        // Another repository has its own memory.
        assertThat(watcher.news("elibrary/docs", List.of(old), NOW, INTERVAL)).isEmpty();
    }

    /** What could not be sent is left out of what the watcher remembers, so it is new again at the next check. */
    @Test
    void keepsTheUnsentItemsNew() {
        ImportEntry old = item(1, ItemKind.ISSUE, "2026-10-07T10:45:00Z", "2026-10-07T10:45:00Z");
        ImportEntry fresh = item(2, ItemKind.ISSUE, "2026-10-07T11:59:00Z", "2026-10-07T11:59:00Z");
        NewItemsWatcher watcher = new NewItemsWatcher();

        // The first check: the fresh item is new, and its mail fails.
        List<ImportEntry> news = watcher.news("elibrary/tool", List.of(old, fresh), NOW, INTERVAL);
        assertThat(news).containsExactly(fresh);
        watcher.remember("elibrary/tool", List.of(old, fresh), news);

        // Long after the window of the first check, the item is still new; once sent, it is not.
        List<ImportEntry> again = watcher.news("elibrary/tool", List.of(old, fresh), NOW.plus(INTERVAL.multipliedBy(3)), INTERVAL);
        assertThat(again).containsExactly(fresh);
        watcher.remember("elibrary/tool", List.of(old, fresh), List.of());
        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW.plus(INTERVAL.multipliedBy(4)), INTERVAL)).isEmpty();
    }
}
