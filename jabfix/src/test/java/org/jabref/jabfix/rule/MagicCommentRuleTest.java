package org.jabref.jabfix.rule;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.entry.types.StandardEntryType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MagicCommentRuleTest {

    private final MagicCommentRule rule = new MagicCommentRule(Set.of("surrounding-whitespace"));

    @Test
    void aKnownIdIsNotReported() {
        assertEquals(List.of(), rule.scan(entryWithComment("% jabfix-disable surrounding-whitespace")));
    }

    @Test
    void anIdOfNoRuleOfTheRunIsReported() {
        List<Finding> findings = rule.scan(entryWithComment("% jabfix-disable surounding-whitespace"));

        assertEquals(1, findings.size());
        assertEquals(Optional.empty(), findings.getFirst().field());
        assertEquals("the magic comment switches off \"surounding-whitespace\", which is no rule of this run",
                findings.getFirst().message());
    }

    /// The person who wrote the comment is the only one who knows what was meant.
    @Test
    void theFindingCarriesNoFix() {
        assertFalse(rule.scan(entryWithComment("% jabfix-disable surounding-whitespace")).getFirst().isFixable());
    }

    @Test
    void aFieldScopedIdIsReportedOnThatField() {
        List<Finding> findings = rule.scan(entryWithComment("% jabfix-disable author:surounding-whitespace"));

        assertEquals(1, findings.size());
        assertEquals(Optional.of(StandardField.AUTHOR), findings.getFirst().field());
    }

    /// A token the parser could not take apart stays one rule id, and so names no rule either.
    @Test
    void aTokenWithSeveralColonsIsReported() {
        List<Finding> findings = rule.scan(entryWithComment("% jabfix-disable author:title:surrounding-whitespace"));

        assertEquals(1, findings.size());
        assertEquals("the magic comment switches off \"title:surrounding-whitespace\", which is no rule of this run",
                findings.getFirst().message());
    }

    /// A name JabRef does not know, on an entry that does not carry it, switches nothing off.
    @Test
    void aFieldNeitherKnownNorPresentIsReported() {
        List<Finding> findings = rule.scan(entryWithComment("% jabfix-disable autor:surrounding-whitespace"));

        assertEquals(1, findings.size());
        assertEquals("the magic comment names the field \"autor\", which is neither a BibTeX field nor one this entry has",
                findings.getFirst().message());
    }

    /// It may well be meant for what is added to the entry later.
    @Test
    void aBibTeXFieldTheEntryDoesNotHaveIsLeftAlone() {
        assertEquals(List.of(), rule.scan(entryWithComment("% jabfix-disable title:surrounding-whitespace")));
    }

    @Test
    void aFieldTheEntryCarriesIsLeftAlone() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(new UnknownField("mynote"), "a note")
                .withUserComments("% jabfix-disable mynote:surrounding-whitespace");

        assertEquals(List.of(), rule.scan(entry));
    }

    /// So that a comment meant as it is written can be left alone.
    @Test
    void itsOwnIdCountsAsKnown() {
        assertEquals(List.of(), rule.scan(entryWithComment("% jabfix-disable magic-comment")));
    }

    private static BibEntry entryWithComment(String comment) {
        return new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, Jane")
                .withUserComments(comment);
    }
}
