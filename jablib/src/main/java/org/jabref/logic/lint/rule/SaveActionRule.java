package org.jabref.logic.lint.rule;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jabref.logic.cleanup.FieldFormatterCleanup;
import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.FieldChange;
import org.jabref.model.entry.BibEntry;

import org.jspecify.annotations.NullMarked;

/// Runs one of JabRef's Save Actions as a [Rule].
///
/// Everything the Save Action already decides is left to it: which fields it applies to, and what
/// their values become. It is run on a copy of the entry, since [#scan] must not change the entry,
/// and each [FieldChange] it reports becomes a [Finding]. The finding's fix sets just that one field,
/// so a Save Action on all fields still yields one repair per field. Idempotency, which [Rule]
/// requires, is up to the wrapped formatter.
///
/// @param saveAction a formatter applied to a field, as configured in JabRef's Save Actions
@NullMarked
public record SaveActionRule(FieldFormatterCleanup saveAction) implements Rule {
    private static final int MAX_REPORTED_VALUE_LENGTH = 60;

    /// The formatter's key, e.g. `normalize-page-numbers`.
    ///
    /// Which fields the Save Action covers is its own business and is left out of the id, so that
    /// the same formatter on several fields is one rule to switch off. A single field is narrowed
    /// down where every other rule is too, by `field:rule` in a magic comment (see [Suppressions]).
    @Override
    public String id() {
        return saveAction.getFormatter().getKey().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    @Override
    public String description() {
        return saveAction.getFormatter().getDescription();
    }

    @Override
    public List<Finding> scan(BibEntry entry) {
        return saveAction.cleanup(new BibEntry(entry)).stream()
                         .map(change -> Finding.of(this, entry, change.field(), message(change), target -> apply(change, target)))
                         .toList();
    }

    /// The value as it stands and what the Save Action makes of it, both cut short: a field value
    /// can be as long as an abstract, while a finding is one line.
    private static String message(FieldChange change) {
        String oldValue = StringUtil.limitStringLength(change.oldValue(), MAX_REPORTED_VALUE_LENGTH);
        return Optional.ofNullable(change.newValue())
                       .map(newValue -> "\"%s\", should be \"%s\"".formatted(oldValue, StringUtil.limitStringLength(newValue, MAX_REPORTED_VALUE_LENGTH)))
                       .orElse("\"%s\", should be removed".formatted(oldValue));
    }

    /// A Save Action removes a field whose value it formats to nothing, reported as a `null` new value.
    private static void apply(FieldChange change, BibEntry target) {
        Optional.ofNullable(change.newValue()).ifPresentOrElse(
                newValue -> target.setField(change.field(), newValue),
                () -> target.clearField(change.field()));
    }
}
