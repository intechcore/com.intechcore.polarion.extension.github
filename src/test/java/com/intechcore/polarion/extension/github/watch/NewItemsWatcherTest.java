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
        watcher.news("elibrary/tool", List.of(old), NOW, INTERVAL);

        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW.plus(INTERVAL), INTERVAL)).containsExactly(fresh);
        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW.plus(INTERVAL.multipliedBy(2)), INTERVAL)).isEmpty();
        // #2 got a work item and left; when it is back without one, it is new again.
        watcher.news("elibrary/tool", List.of(old), NOW, INTERVAL);
        assertThat(watcher.news("elibrary/tool", List.of(old, fresh), NOW, INTERVAL)).containsExactly(fresh);
        // Another repository has its own memory.
        assertThat(watcher.news("elibrary/docs", List.of(old), NOW, INTERVAL)).isEmpty();
    }
}
