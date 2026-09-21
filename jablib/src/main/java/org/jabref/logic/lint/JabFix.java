package org.jabref.logic.lint;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jabref.logic.bibtex.FieldPreferences;
import org.jabref.logic.citationkeypattern.CitationKeyPatternPreferences;
import org.jabref.logic.exporter.BibDatabaseWriter;
import org.jabref.logic.exporter.BibWriter;
import org.jabref.logic.exporter.SelfContainedSaveConfiguration;
import org.jabref.logic.lint.rule.Finding;
import org.jabref.logic.lint.rule.MagicCommentRule;
import org.jabref.logic.lint.rule.Rule;
import org.jabref.logic.lint.rule.RuleSet;
import org.jabref.logic.lint.rule.Suppressions;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;

import org.jspecify.annotations.NullMarked;

/// Engine behind the `jabkit jabfix` command: applies a [RuleSet] to a library and writes it out.
///
/// The two halves do different jobs. The rules decide questions the `.bib` format leaves open but
/// that survive parsing -- such as whether a value may be padded with whitespace. Serialization is
/// left to JabRef's own [BibDatabaseWriter], which settles everything that does *not* survive
/// parsing: entry type capitalization, whether values are braced or quoted, field order, indentation.
/// Whatever conventions the input happened to use, that writer's output is the normal form.
///
/// What the writer does not touch is preserved -- entry order
/// ([org.jabref.model.metadata.SaveOrder.OrderType#ORIGINAL]), `@Comment` and `@String` blocks, and
/// the library's own line separator -- so a run over an already-clean library produces no diff.
///
/// Preference objects are passed in individually rather than as a whole
/// [org.jabref.logic.preferences.CliPreferences], so the engine stays usable outside the CLI.
@NullMarked
public class JabFix {

    private final RuleSet ruleSet;
    private final FieldPreferences fieldPreferences;
    private final CitationKeyPatternPreferences keyPatternPreferences;
    private final BibEntryTypesManager entryTypesManager;

    public JabFix(RuleSet ruleSet,
                  FieldPreferences fieldPreferences,
                  CitationKeyPatternPreferences keyPatternPreferences,
                  BibEntryTypesManager entryTypesManager) {
        this.ruleSet = ruleSet;
        this.fieldPreferences = fieldPreferences;
        this.keyPatternPreferences = keyPatternPreferences;
        this.entryTypesManager = entryTypesManager;
    }

    /// Runs every rule over `databaseContext`, applies the repairs they offer, and serializes the
    /// result.
    ///
    /// The entries in `databaseContext` are modified in place; the formatted text is only returned,
    /// never written anywhere, so a caller that merely wants to know whether a library is clean can
    /// run this and compare instead of saving.
    ///
    /// Rules are applied as a pipeline: each rule sees what the rules before it left behind. A rule
    /// is therefore only ever run once over an entry -- see [Rule] for the contract that makes a
    /// second pass unnecessary.
    ///
    /// What the magic comments above an entry switch off ([Suppressions]) is dropped before the
    /// repairs are applied, so a suppressed finding is neither reported nor repaired. A rule that
    /// is switched off for one field only still runs on the rest of the entry.
    ///
    /// A [MagicCommentRule] runs ahead of the rule set, since a comment that names no rule of this
    /// run switches nothing off and would otherwise go unnoticed.
    ///
    /// @return the findings and the resulting `.bib` content
    public JabFixResult run(BibDatabaseContext databaseContext) throws IOException {
        List<Rule> rules = Stream.concat(
                Stream.of(new MagicCommentRule(Set.copyOf(ruleSet.ids()))),
                ruleSet.rules().stream()).toList();

        List<Finding> findings = new ArrayList<>();
        for (BibEntry entry : databaseContext.getEntries()) {
            Suppressions suppressions = Suppressions.in(entry);
            for (Rule rule : rules) {
                List<Finding> reported = rule.scan(entry).stream()
                                             .filter(finding -> !suppressions.suppresses(rule.id(), finding.field()))
                                             .toList();
                for (Finding finding : reported) {
                    finding.fix().ifPresent(fix -> fix.applyTo(entry));
                }
                findings.addAll(reported);
            }
        }
        return new JabFixResult(findings, serialize(databaseContext));
    }

    private String serialize(BibDatabaseContext databaseContext) throws IOException {
        StringWriter stringWriter = new StringWriter();
        BibWriter bibWriter = new BibWriter(stringWriter, databaseContext.getDatabase().getNewLineSeparator());

        // Reformatting on save rewrites every entry. Without it the writer would keep the
        // serialization each entry had in the input file, which is exactly what is to be replaced.
        SelfContainedSaveConfiguration saveConfiguration =
                (SelfContainedSaveConfiguration) new SelfContainedSaveConfiguration().withReformatOnSave(true);

        new BibDatabaseWriter(
                bibWriter,
                saveConfiguration,
                fieldPreferences,
                keyPatternPreferences,
                entryTypesManager)
                .writeDatabase(databaseContext);

        return stringWriter.toString();
    }
}
