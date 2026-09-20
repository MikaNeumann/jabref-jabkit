package org.jabref.jabfix.rule;

import java.util.Optional;
import java.util.Set;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.entry.types.StandardEntryType;

import com.google.common.collect.ImmutableSetMultimap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuppressionsTest {

    @Test
    void anEntryWithoutCommentsSuppressesNothing() {
        assertEquals(Suppressions.NONE, Suppressions.in(new BibEntry(StandardEntryType.Article)));
    }

    @Test
    void readsTheCommentsOfAnEntry() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withUserComments("% jabfix-disable surrounding-whitespace");

        assertEquals(new Suppressions(false, Set.of("surrounding-whitespace"), ImmutableSetMultimap.of()), Suppressions.in(entry));
    }

    @Test
    void anOrdinaryCommentSuppressesNothing() {
        assertEquals(Suppressions.NONE, Suppressions.parse("% Knuth wrote this one by hand, leave it be"));
    }

    @Test
    void aRuleWithoutAFieldCountsForTheWholeEntry() {
        assertEquals(new Suppressions(false, Set.of("page-ranges"), ImmutableSetMultimap.of()),
                Suppressions.parse("% jabfix-disable page-ranges"));
    }

    @Test
    void aRuleBehindAFieldCountsForThatFieldOnly() {
        assertEquals(new Suppressions(false, Set.of(), ImmutableSetMultimap.of(StandardField.AUTHOR, "page-ranges")),
                Suppressions.parse("% jabfix-disable author:page-ranges"));
    }

    @Test
    void oneCommentTakesSeveralTokens() {
        assertEquals(new Suppressions(false,
                        Set.of("surrounding-whitespace"),
                        ImmutableSetMultimap.of(StandardField.AUTHOR, "page-ranges", StandardField.TITLE, "page-ranges")),
                Suppressions.parse("% jabfix-disable surrounding-whitespace author:page-ranges title:page-ranges"));
    }

    @Test
    void severalCommentsAboveOneEntryAddUp() {
        assertEquals(new Suppressions(false,
                        Set.of("surrounding-whitespace"),
                        ImmutableSetMultimap.of(StandardField.AUTHOR, "page-ranges", StandardField.AUTHOR, "author-et-al")),
                Suppressions.parse("""
                        % jabfix-disable author:page-ranges
                        % a note in between
                        % jabfix-disable surrounding-whitespace author:author-et-al
                        """));
    }

    /// The `.bib` format does not care about the case of a field name, so neither does this.
    @Test
    void theCaseOfAFieldNameDoesNotMatter() {
        assertEquals(new Suppressions(false, Set.of(), ImmutableSetMultimap.of(StandardField.AUTHOR, "page-ranges")),
                Suppressions.parse("% jabfix-disable Author:page-ranges"));
    }

    @Test
    void aFieldJabRefDoesNotKnowIsKeptAsWritten() {
        assertEquals(new Suppressions(false, Set.of(), ImmutableSetMultimap.of(new UnknownField("mynote"), "page-ranges")),
                Suppressions.parse("% jabfix-disable mynote:page-ranges"));
    }

    /// Rather than dropping it, so that it is reported once ids are checked against the rules.
    @Test
    void aTokenWithAnEmptyHalfStaysOneRuleId() {
        assertEquals(new Suppressions(false, Set.of("author:", ":page-ranges"), ImmutableSetMultimap.of()),
                Suppressions.parse("% jabfix-disable author: :page-ranges"));
    }

    @Test
    void aCommentWithoutTokensSwitchesEveryRuleOff() {
        Suppressions suppressions = Suppressions.parse("% jabfix-disable");

        assertEquals(new Suppressions(true, Set.of(), ImmutableSetMultimap.of()), suppressions);
        assertTrue(suppressions.suppresses("page-ranges", Optional.of(StandardField.TITLE)));
    }

    @Test
    void aLongerWordIsNotTheMagicComment() {
        assertEquals(Suppressions.NONE, Suppressions.parse("% jabfix-disabled page-ranges"));
    }

    @Test
    void aFieldScopedRuleSuppressesThatFieldOnly() {
        Suppressions suppressions = Suppressions.parse("% jabfix-disable author:page-ranges");

        assertTrue(suppressions.suppresses("page-ranges", Optional.of(StandardField.AUTHOR)));
        assertFalse(suppressions.suppresses("page-ranges", Optional.of(StandardField.TITLE)));
        assertFalse(suppressions.suppresses("page-ranges", Optional.empty()));
    }

    @Test
    void aRuleOfTheWholeEntrySuppressesEveryField() {
        Suppressions suppressions = Suppressions.parse("% jabfix-disable page-ranges");

        assertTrue(suppressions.suppresses("page-ranges", Optional.of(StandardField.AUTHOR)));
        assertTrue(suppressions.suppresses("page-ranges", Optional.empty()));
        assertFalse(suppressions.suppresses("surrounding-whitespace", Optional.of(StandardField.AUTHOR)));
    }
}
