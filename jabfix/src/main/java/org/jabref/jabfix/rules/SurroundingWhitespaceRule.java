package org.jabref.jabfix.rules;

import org.jabref.jabfix.rule.FieldValueRule;

import org.jspecify.annotations.NullMarked;

/// Strips whitespace from both ends of a field value.
///
/// BibTeX ignores it, so it carries no meaning; it only makes values compare unequal that are in
/// fact the same, which matters as soon as entries are deduplicated, sorted or diffed. Whitespace
/// *inside* a value is left untouched -- there it can be deliberate.
///
/// Also the smallest example of a rule: a [FieldValueRule] only states what a value should be.
@NullMarked
public class SurroundingWhitespaceRule extends FieldValueRule {

    @Override
    public String id() {
        return "surrounding-whitespace";
    }

    @Override
    public String description() {
        return "Removes whitespace at the start and end of a field value.";
    }

    @Override
    protected String normalize(String value) {
        return value.strip();
    }

    @Override
    protected String message(String value, String normalized) {
        return "value has leading or trailing whitespace";
    }
}
