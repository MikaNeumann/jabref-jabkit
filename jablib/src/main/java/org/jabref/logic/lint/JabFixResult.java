package org.jabref.logic.lint;

import java.util.List;

import org.jabref.logic.lint.rule.Finding;

import org.jspecify.annotations.NullMarked;

/// What a [JabFix] run produced.
///
/// @param findings  everything the rules reported, in the order they were reported
/// @param formatted the contents the `.bib` file has after the run
@NullMarked
public record JabFixResult(List<Finding> findings, String formatted) {
    public JabFixResult {
        findings = List.copyOf(findings);
    }
}
