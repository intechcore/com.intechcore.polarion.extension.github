package com.intechcore.polarion.extension.github.service;

import com.polarion.alm.projects.model.IUser;
import com.polarion.core.util.types.Currency;
import com.polarion.core.util.types.DateOnly;
import com.polarion.core.util.types.Text;
import com.polarion.core.util.types.TimeOnly;
import com.polarion.platform.persistence.IEnumOption;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The value of a setting against the value a work item holds, read as the type of that value. */
class FieldValuesTest {

    @Test
    void comparesAnOptionByItsKeyOrName() {
        IEnumOption inProgress = option("inprogress", "In Progress");

        // Generic reads an option back by its name; the page stores its key.
        assertThat(FieldValues.same("inprogress", inProgress)).isTrue();
        assertThat(FieldValues.same("In Progress", inProgress)).isTrue();
        assertThat(FieldValues.same("open", inProgress)).isFalse();
    }

    @Test
    void comparesSeveralValuesAsASet() {
        List<IEnumOption> categories = List.of(option("plugin", "External/Plugin"), option("core", "Core"));

        assertThat(FieldValues.same("core, plugin", categories)).isTrue();
        assertThat(FieldValues.same("plugin", categories)).isFalse();
        assertThat(FieldValues.same("plugin,core,docs", categories)).isFalse();
        assertThat(FieldValues.same("plugin,plugin", categories)).isFalse();
    }

    @Test
    void comparesTheAssigneesByIdLoginOrName() {
        IUser bob = mock(IUser.class);
        when(bob.getId()).thenReturn("bob");
        when(bob.getName()).thenReturn("Bob Builder");

        assertThat(FieldValues.same("bob", List.of(bob))).isTrue();
        assertThat(FieldValues.same("Bob Builder", List.of(bob))).isTrue();
        assertThat(FieldValues.same("alice", List.of(bob))).isFalse();
    }

    @Test
    void comparesTheCategoriesByIdOrName() {
        com.polarion.alm.tracker.model.ICategory plugin = mock(com.polarion.alm.tracker.model.ICategory.class);
        when(plugin.getId()).thenReturn("plugin");
        when(plugin.getName()).thenReturn("External/Plugin");

        assertThat(FieldValues.same("plugin", List.of(plugin))).isTrue();
        assertThat(FieldValues.same("external/plugin", List.of(plugin))).isTrue();
        assertThat(FieldValues.same("core", List.of(plugin))).isFalse();
    }

    @Test
    void takesAnEmptyValueForNoValue() {
        assertThat(FieldValues.same(null, null)).isTrue();
        assertThat(FieldValues.same(" ", List.of())).isTrue();
        assertThat(FieldValues.same("", Text.plain(""))).isTrue();
        assertThat(FieldValues.same("major", null)).isFalse();
        assertThat(FieldValues.same(null, "major")).isFalse();
    }

    @Test
    void comparesNumbersBooleansAndText() {
        assertThat(FieldValues.same("3", 3)).isTrue();
        assertThat(FieldValues.same("3.0", 3)).isTrue();
        assertThat(FieldValues.same("1.5", 1.5f)).isTrue();
        assertThat(FieldValues.same("1.25", new Currency(new BigDecimal("1.25")))).isTrue();
        assertThat(FieldValues.same("2", 3)).isFalse();
        assertThat(FieldValues.same("true", Boolean.TRUE)).isTrue();
        assertThat(FieldValues.same("false", Boolean.TRUE)).isFalse();
        assertThat(FieldValues.same("<p>Note</p>", Text.html("<p>Note</p>"))).isTrue();
        assertThat(FieldValues.same("core", "core")).isTrue();
        // A string the type cannot read differs, so an update writes it again.
        assertThat(FieldValues.same("many", 3)).isFalse();
    }

    @Test
    void comparesDatesAndTimesInTheFormsGenericWrites() {
        Date moment = Date.from(LocalDateTime.of(2026, 10, 9, 14, 30, 5).atZone(ZoneId.systemDefault()).toInstant());

        assertThat(FieldValues.same("2026-10-09", new DateOnly(moment))).isTrue();
        assertThat(FieldValues.same("2026-10-08", new DateOnly(moment))).isFalse();
        assertThat(FieldValues.same("14:30:05", new TimeOnly(moment))).isTrue();
        assertThat(FieldValues.same("14:30", new TimeOnly(Date.from(LocalDateTime.of(2026, 10, 9, 14, 30).atZone(ZoneId.systemDefault()).toInstant())))).isTrue();
        // Generic writes a date and time with the "T" and reads it back without it.
        assertThat(FieldValues.same("2026-10-09T14:30:05", moment)).isTrue();
        assertThat(FieldValues.same("2026-10-09 14:30:05", moment)).isTrue();
        assertThat(FieldValues.same("2026-10-09T14:30:00", moment)).isFalse();
    }

    private static IEnumOption option(String id, String name) {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getId()).thenReturn(id);
        when(option.getName()).thenReturn(name);
        return option;
    }
}
