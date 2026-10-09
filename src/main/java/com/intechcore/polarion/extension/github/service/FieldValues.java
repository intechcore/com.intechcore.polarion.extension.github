package com.intechcore.polarion.extension.github.service;

import ch.sbb.polarion.extension.generic.util.EnumUtils;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.ICategory;
import com.polarion.core.util.types.Currency;
import com.polarion.core.util.types.DateOnly;
import com.polarion.core.util.types.Text;
import com.polarion.core.util.types.TimeOnly;
import com.polarion.core.util.types.duration.DurationTime;
import com.polarion.platform.persistence.IEnumOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Compares the value a setting gives a field, one string as generic writes it, with the value a work
 * item holds. Generic reads a value back in another form than it takes it: an option by its name, a
 * date and time without the "T". The comparison therefore reads the string as the type of the value.
 */
final class FieldValues {

    private FieldValues() {
    }

    /** Whether the work item holds what the setting gives. A value that cannot be read differs. */
    static boolean same(@Nullable String expected, @Nullable Object actual) {
        String text = expected == null ? "" : expected.trim();
        if (isEmpty(actual)) {
            return text.isEmpty();
        }
        try {
            return switch (actual) {
                case Collection<?> values -> sameValues(text, values);
                case IEnumOption option -> names(option).anyMatch(text::equalsIgnoreCase);
                case IUser user -> names(user).anyMatch(text::equals);
                case ICategory category -> text.equals(category.getId()) || text.equalsIgnoreCase(category.getName());
                case Text richText -> text.equals(Objects.toString(richText.getContent(), "").trim());
                case Boolean flag -> flag == Boolean.parseBoolean(text);
                case Number number -> new BigDecimal(text).compareTo(new BigDecimal(number.toString())) == 0;
                case Currency currency -> new BigDecimal(text.replace(',', '.')).compareTo(currency.getValue()) == 0;
                case DurationTime duration -> DurationTime.fromString(text).getHours() == duration.getHours();
                case DateOnly date -> LocalDate.parse(text).equals(local(date.getDate()).toLocalDate());
                case TimeOnly time -> LocalTime.parse(text).equals(local(time.getDate()).toLocalTime().truncatedTo(ChronoUnit.SECONDS));
                case Date date -> LocalDateTime.parse(text.replace(' ', 'T')).equals(local(date).truncatedTo(ChronoUnit.SECONDS));
                default -> text.equals(actual.toString().trim());
            };
        } catch (RuntimeException e) {
            // A string the type cannot read: an update writes it again, and generic then reports it.
            return false;
        }
    }

    private static boolean isEmpty(@Nullable Object actual) {
        return actual == null
                || actual instanceof Collection<?> values && values.isEmpty()
                || actual instanceof Text richText && (richText.getContent() == null || richText.getContent().isBlank())
                || actual instanceof String string && string.isBlank();
    }

    /** Several values compare as sets: each one of the setting names one value, and no value stays over. */
    private static boolean sameValues(String text, Collection<?> values) {
        List<String> parts = Arrays.stream(text.split(",")).map(String::trim).filter(part -> !part.isEmpty()).distinct().toList();
        List<Object> left = new ArrayList<>(values.stream().distinct().toList());
        if (parts.size() != left.size()) {
            return false;
        }
        for (String part : parts) {
            Object match = left.stream().filter(value -> same(part, value)).findFirst().orElse(null);
            if (match == null) {
                return false;
            }
            left.remove(match);
        }
        return true;
    }

    /** What the setting may name an option by: its key, which the page stores, or its name, as generic accepts it. */
    private static Stream<String> names(IEnumOption option) {
        return Stream.of(EnumUtils.getEnumId(option), option.getId(), option.getName()).filter(Objects::nonNull);
    }

    /** What generic finds an assignee by. */
    private static Stream<String> names(IUser user) {
        return Stream.of(user.getId(), user.getLoginName(), user.getName()).filter(Objects::nonNull);
    }

    private static @NotNull LocalDateTime local(@NotNull Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
