package org.jabref.logic.lint;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import org.jabref.logic.bibtex.FieldPreferences;
import org.jabref.logic.citationkeypattern.CitationKeyPatternPreferences;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.logic.lint.rule.Finding;
import org.jabref.logic.lint.rule.RuleSet;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntryTypesManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JabFixTest {

    private static final String SLOPPY = """
            @ARTICLE{key,
            author = " Doe, Jane ",
                YEAR="2024"
            }
            """;

    private FieldPreferences fieldPreferences;
    private ImportFormatPreferences importFormatPreferences;

    @BeforeEach
    void setUp() {
        fieldPreferences = new FieldPreferences(true, List.of(), List.of());
        importFormatPreferences = mock(ImportFormatPreferences.class, Answers.RETURNS_DEEP_STUBS);
        when(importFormatPreferences.fieldPreferences()).thenReturn(fieldPreferences);
    }

    @Test
    void runNormalizesSerializationAndAppliesRules() throws IOException {
        assertEquals("""
                @Article{key,
                  author = {Doe, Jane},
                  year   = {2024},
                }
                """, run(RuleSet.all(), SLOPPY).formatted());
    }

    @Test
    void runIsIdempotent() throws IOException {
        String once = run(RuleSet.all(), SLOPPY).formatted();
        assertEquals(once, run(RuleSet.all(), once).formatted());
    }

    @Test
    void runReportsWhatItRepaired() throws IOException {
        List<String> ruleIds = run(RuleSet.all(), SLOPPY).findings().stream()
                                                         .map(finding -> finding.rule().id())
                                                         .toList();

        assertEquals(List.of("surrounding-whitespace"), ruleIds);
    }

    @Test
    void everyFindingCarriesTheEntryAndFieldItConcerns() throws IOException {
        Finding finding = run(RuleSet.all(), SLOPPY).findings().getFirst();

        assertEquals("key", finding.citationKey());
        assertEquals("author", finding.field().orElseThrow().getName());
        assertTrue(finding.isFixable());
    }

    /// Without any rules JabFix still normalizes what serialization decides -- entry type case and
    /// value delimiters -- but touches no value.
    @Test
    void emptyRuleSetOnlyReformats() throws IOException {
        assertEquals("""
                @Article{key,
                  author = { Doe, Jane },
                  year   = {2024},
                }
                """, run(RuleSet.of(), SLOPPY).formatted());
    }

    /// Parses `bibtex` and runs JabFix over it, normalizing the line separator so that the expected
    /// values can be written as text blocks no matter which platform the test runs on.
    private JabFixResult run(RuleSet ruleSet, String bibtex) throws IOException {
        BibDatabaseContext databaseContext = new BibtexParser(importFormatPreferences)
                .parse(Reader.of(bibtex))
                .getDatabaseContext();

        JabFixResult result = new JabFix(
                ruleSet,
                fieldPreferences,
                mock(CitationKeyPatternPreferences.class, Answers.RETURNS_DEEP_STUBS),
                new BibEntryTypesManager())
                .run(databaseContext);

        return new JabFixResult(result.findings(), result.formatted().replace("\r\n", "\n"));
    }
}
