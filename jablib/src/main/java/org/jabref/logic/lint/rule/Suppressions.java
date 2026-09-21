package org.jabref.logic.lint.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import org.jspecify.annotations.NullMarked;

/// The rules the magic comments of one entry switch off.
///
/// A magic comment is a BibTeX comment directly above the entry, which JabRef keeps with it as its
/// user comments:
///
/// ```bibtex
/// % jabfix-disable surrounding-whitespace author:page-ranges
/// @Article{key,
///   author = {Doe, Jane},
/// }
/// ```
///
/// A token without a colon switches its rule off for the whole entry, `field:rule` only for that
/// one field. A comment with no token at all switches every rule off for the entry. Several such
/// comments above one entry add up, and every other comment line is left to whoever wrote it.
///
/// Rule ids are not checked here against the rules that exist, because the parser does not know
/// them -- [RuleSet#without] does that for the ids given on the command line. A token whose field
/// or rule half is empty (`author:`, `:page-ranges`) is therefore kept as one rule id, so that it
/// surfaces as an unknown rule once ids are checked, rather than being dropped here.
///
/// @param wholeEntry   whether every rule is switched off for the entry
/// @param rules        the rule ids switched off for the whole entry
/// @param rulesByField the rule ids switched off for one field each
@NullMarked
public record Suppressions(boolean wholeEntry, Set<String> rules, SetMultimap<Field, String> rulesByField) {
    /// Nothing switched off, the state of an entry without magic comments.
    public static final Suppressions NONE = new Suppressions(false, Set.of(), ImmutableSetMultimap.of());

    private static final Pattern DISABLE_COMMENT = Pattern.compile("^\\s*%+\\s*jabfix-disable(?:\\s+(?<rules>.*?))?\\s*$");
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("\\s+");
    private static final char FIELD_SEPARATOR = ':';

    public Suppressions {
        rules = Set.copyOf(rules);
        rulesByField = ImmutableSetMultimap.copyOf(rulesByField);
    }

    /// Reads the magic comments above `entry`.
    public static Suppressions in(BibEntry entry) {
        return parse(entry.getUserComments());
    }

    /// Reads the magic comments out of `comments`, the text above an entry.
    public static Suppressions parse(String comments) {
        boolean wholeEntry = false;
        Set<String> rules = new HashSet<>();
        SetMultimap<Field, String> rulesByField = HashMultimap.create();

        for (String line : comments.lines().toList()) {
            Matcher comment = DISABLE_COMMENT.matcher(line);
            if (!comment.matches()) {
                continue;
            }
            List<String> tokens = tokensOf(comment);
            if (tokens.isEmpty()) {
                wholeEntry = true;
                continue;
            }
            for (String token : tokens) {
                int separator = token.indexOf(FIELD_SEPARATOR);
                if ((separator <= 0) || (separator == (token.length() - 1))) {
                    rules.add(token);
                } else {
                    rulesByField.put(FieldFactory.parseField(token.substring(0, separator)), token.substring(separator + 1));
                }
            }
        }
        return new Suppressions(wholeEntry, rules, rulesByField);
    }

    /// Whether `ruleId` is switched off for the finding at hand.
    ///
    /// @param field the field the finding concerns, empty for a finding about the entry as a whole
    public boolean suppresses(String ruleId, Optional<Field> field) {
        if (wholeEntry || rules.contains(ruleId)) {
            return true;
        }
        return field.map(rulesByField::get)
                    .orElse(Set.of())
                    .contains(ruleId);
    }

    private static List<String> tokensOf(Matcher comment) {
        return Optional.ofNullable(comment.group("rules"))
                       .map(String::strip)
                       .filter(rules -> !rules.isEmpty())
                       .map(rules -> List.of(TOKEN_SEPARATOR.split(rules)))
                       .orElse(List.of());
    }
}
