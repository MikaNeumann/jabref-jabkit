package org.jabref.logic.lint;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.logic.bibtex.FieldPreferences;
import org.jabref.logic.citationkeypattern.CitationKeyPatternPreferences;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.logic.lint.rule.Finding;
import org.jabref.logic.lint.rule.Rule;
import org.jabref.logic.lint.rule.RuleSet;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.InternalField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JabFixTest {

    /// Reports every field of an entry, so that a magic comment naming one field can be told apart
    /// from one naming the rule as a whole.
    private static final Rule EVERY_FIELD = new Rule() {
        @Override
        public String id() {
            return "every-field";
        }

        @Override
        public String description() {
            return "Reports every field of an entry.";
        }

        @Override
        public List<Finding> scan(BibEntry entry) {
            return entry.getFields().stream()
                        .filter(field -> field != InternalField.KEY_FIELD)
                        .map(field -> new Finding(this, entry, Optional.of(field), "reported", Optional.empty()))
                        .toList();
        }
    };

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

    /// A magic comment above an entry switches the rule off for it, so nothing is reported and
    /// nothing repaired, while the writer still normalizes the serialization.
    @Test
    void aMagicCommentSwitchesARuleOffForItsEntry() throws IOException {
        JabFixResult result = run(RuleSet.all(), """
                % jabfix-disable surrounding-whitespace
                @ARTICLE{key,
                author = " Doe, Jane ",
                    YEAR="2024"
                }
                """);

        assertEquals("""
                % jabfix-disable surrounding-whitespace
                @Article{key,
                  author = { Doe, Jane },
                  year   = {2024},
                }
                """, result.formatted());
        assertEquals(List.of(), result.findings());
    }

    /// `field:rule` covers that one field, and leaves the rest of the entry to the rule.
    @Test
    void aFieldScopedMagicCommentLeavesTheOtherFieldsToTheRule() throws IOException {
        JabFixResult result = run(RuleSet.of(EVERY_FIELD), """
                % jabfix-disable author:every-field
                @ARTICLE{key,
                author = " Doe, Jane ",
                title = " A Title "
                }
                """);

        assertEquals(Set.of("title"),
                result.findings().stream().map(finding -> finding.field().orElseThrow().getName()).collect(Collectors.toSet()));
    }

    /// The writer trims every field of an entry that a rule changed, which a magic comment cannot
    /// switch off: only what the rules themselves do is suppressed.
    @Test
    void theWriterTrimsEvenASuppressedField() throws IOException {
        JabFixResult result = run(RuleSet.all(), """
                % jabfix-disable author:surrounding-whitespace
                @ARTICLE{key,
                author = " Doe, Jane ",
                title = " A Title "
                }
                """);

        assertEquals("""
                % jabfix-disable author:surrounding-whitespace
                @Article{key,
                  author = {Doe, Jane},
                  title  = {A Title},
                }
                """, result.formatted());
        assertEquals(List.of("title"),
                result.findings().stream().map(finding -> finding.field().orElseThrow().getName()).toList());
    }

    /// A comment naming no rule of the run switches nothing off, which is reported rather than
    /// passed over: the entry is repaired as if the comment were not there.
    @Test
    void aMagicCommentThatNamesNoRuleIsReported() throws IOException {
        JabFixResult result = run(RuleSet.all(), """
                % jabfix-disable surounding-whitespace
                @ARTICLE{key,
                author = " Doe, Jane ",
                    YEAR="2024"
                }
                """);

        assertEquals(List.of("magic-comment", "surrounding-whitespace"),
                result.findings().stream().map(finding -> finding.rule().id()).toList());
        assertTrue(result.formatted().contains("author = {Doe, Jane},"), result.formatted());
    }

    @Test
    void aMagicCommentCanSwitchOffTheReportAboutItself() throws IOException {
        JabFixResult result = run(RuleSet.all(), """
                % jabfix-disable surounding-whitespace magic-comment
                @ARTICLE{key,
                author = " Doe, Jane ",
                    YEAR="2024"
                }
                """);

        assertEquals(List.of("surrounding-whitespace"),
                result.findings().stream().map(finding -> finding.rule().id()).toList());
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
