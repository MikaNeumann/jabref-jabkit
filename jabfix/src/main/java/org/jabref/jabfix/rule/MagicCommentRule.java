package org.jabref.jabfix.rule;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import org.jspecify.annotations.NullMarked;

/// Reports a magic comment that names no rule.
///
/// A misspelled id in `% jabfix-disable` switches nothing off, which is the one thing a user of it
/// never notices: the entry is repaired as if the comment were not there. The id is therefore held
/// against the rules of the run, and one that names none of them is reported like any other
/// finding -- with no fix, since only the person who wrote the comment knows what was meant.
///
/// A token the parser could not take apart, such as `author:` or `author:title:page-ranges`, is
/// kept as one rule id by [Suppressions] and so lands here as well.
///
/// The rule is not one of [RuleSet#all]: it needs the ids of the run it belongs to, which
/// [org.jabref.jabfix.JabFix] hands it. Its own id counts as known, so `% jabfix-disable
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
                                               .map(ruleId -> finding(entry, Optional.empty(), ruleId));
        Stream<Finding> forFields = suppressions.rulesByField().entries().stream()
                                                .filter(suppressed -> !knownRuleIds.contains(suppressed.getValue()))
                                                .map(suppressed -> finding(entry, Optional.of(suppressed.getKey()), suppressed.getValue()));

        return Stream.concat(forEntry, forFields).toList();
    }

    private Finding finding(BibEntry entry, Optional<Field> field, String ruleId) {
        return new Finding(this, entry, field,
                "the magic comment switches off \"%s\", which is no rule of this run".formatted(ruleId),
                Optional.empty());
    }
}
