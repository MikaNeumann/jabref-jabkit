package org.jabref.jabfix.rule;

import org.jabref.model.entry.BibEntry;

/// The repair belonging to a [Finding].
///
/// Applied only when JabFix is actually formatting; `--check` collects findings and never calls
/// this. A fix must repair exactly what its finding reported and nothing else, so that switching a
/// rule off removes precisely that change from the output.
@FunctionalInterface
public interface Fix {

    /// @param entry the entry the finding was reported on
    void applyTo(BibEntry entry);
}
