package org.jabref.logic.lint.rule;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.UnknownField;

import org.jspecify.annotations.NullMarked;

/// Reports a magic comment that switches nothing off.
///
/// A misspelled id in `% jabfix-disable` switches nothing off, which is the one thing a user of it
/// never notices: the entry is repaired as if the comment were not there. The id is therefore held
/// against the rules of the run, and one that names none of them is reported like any other
/// finding -- with no fix, since only the person who wrote the comment knows what was meant.
///
/// A token the parser could not take apart, such as `author:` or `author:title:page-ranges`, is
/// kept as one rule id by [Suppressions] and so lands here as well.
///
/// The field of `field:rule` is reported on the same grounds, but only when JabRef does not know it
/// *and* the entry does not carry it: any name is a valid BibTeX field, so a standard field the
/// entry happens to lack may well be meant for what is added to it later, while `autor:` on an
/// entry that has no such field is a typo in all but name.
///
/// The rule is not one of [RuleSet#all]: it needs the ids of the run it belongs to, which
/// [org.jabref.logic.lint.JabFix] hands it. Its own id counts as known, so `% jabfix-disable
/// magic-comment` silences it where a comment is meant as it is written.
@NullMarked
public class MagicCommentRule implements Rule {
    private static final String ID = "magic-comment";

    private final Set<String> knownRuleIds;

    /// @param knownRuleIds the ids of the rules the run applies
    public MagicCommentRule(Set<String> knownRuleIds) {
        this.knownRuleIds = Stream.concat(knownRuleIds.stream(), Stream.of(ID)).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Reports a magic comment that names no rule.";
    }

    @Override
    public List<Finding> scan(BibEntry entry) {
        Suppressions suppressions = Suppressions.in(entry);

        Stream<Finding> forEntry = suppressions.rules().stream()
                                               .filter(ruleId -> !knownRuleIds.contains(ruleId))
                                               .map(ruleId -> finding(entry, Optional.empty(),
                                                       "switches off \"%s\", which is no rule of this run".formatted(ruleId)));
        Stream<Finding> forFields = suppressions.rulesByField().entries().stream()
                                                .filter(suppressed -> !knownRuleIds.contains(suppressed.getValue()))
                                                .map(suppressed -> finding(entry, Optional.of(suppressed.getKey()),
                                                        "switches off \"%s\", which is no rule of this run".formatted(suppressed.getValue())));

        Stream<Finding> forMissingFields = suppressions.rulesByField().keySet().stream()
                                                       .filter(field -> field instanceof UnknownField)
                                                       .filter(field -> !entry.hasField(field))
                                                       .map(field -> finding(entry, Optional.of(field),
                                                               "names the field \"%s\", which is neither a BibTeX field nor one this entry has".formatted(field.getName())));

        return Stream.concat(forEntry, Stream.concat(forFields, forMissingFields)).toList();
    }

    private Finding finding(BibEntry entry, Optional<Field> field, String what) {
        return new Finding(this, entry, field, "the magic comment " + what, Optional.empty());
    }
}
