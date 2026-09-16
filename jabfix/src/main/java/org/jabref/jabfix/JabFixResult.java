package org.jabref.jabfix;

import java.util.List;

import org.jabref.jabfix.rule.Finding;

/// What a [JabFix] run produced.
///
/// @param findings  everything the rules reported, in the order they were reported
/// @param formatted the contents the `.bib` file has after the run
public record JabFixResult(List<Finding> findings, String formatted) {
    public JabFixResult {
        findings = List.copyOf(findings);
    }
}
