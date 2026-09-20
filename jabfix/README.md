# JabFix

JabFix is a linter and formatter for BibTeX libraries.
Each check is a rule with a stable id that reports findings and, where a safe repair exists, attaches it.
`--check` and formatting therefore run the same code and cannot disagree.
Defaults will be based on a study of `.bib` files on GitHub.

## Goal

JabRef has four features that judge or change how a library is written, each configured differently:

| Feature           | Reports | Fixes | Configured in      |
|-------------------|---------|-------|--------------------|
| Cleanup entries   | no      | yes   | user preferences   |
| Save actions      | no      | yes   | library metadata   |
| Check integrity   | yes     | no    | fixed checker set  |
| Check consistency | yes     | no    | not configurable   |

The goal is to turn all four into JabFix rules, configured with the library and applied the same way by the GUI, JabKit and CI: a comment above an entry already switches rules off for it, and the settings of a whole library are planned to live in its metadata.
Integrity checkers become report-only rules, cleanup jobs and Save Actions formatters become rules with fixes, and the consistency check becomes a library-level rule.
Save Actions can already run as rules through `SaveActionRule`.

Still missing:

- built-in rules beyond one example,
- library-level rules (`Rule#scan` sees one entry),
- context for rules (file directories, abbreviation list, key patterns),
- configuration beyond `--disable` and the comments above an entry, including rule parameters,
- GUI integration,
- running the library's configured Save Actions as rules; `BibDatabaseWriter` still applies them on its own and without findings, as it does key generation if enabled. Its whitespace normalization runs on every entry a rule changed, so no comment can switch that off either,
- leaving out metadata JabRef only inferred (database type, keyword separator); writing it back changes libraries that are otherwise clean.

## Structure

```text
jabfix/src/main/java/org/jabref/jabfix/
├── JabFix.java          runs a RuleSet over a library, serializes with BibDatabaseWriter
├── JabFixResult.java    findings + formatted library
├── rule/                API: Rule, Finding, Fix, RuleSet, FieldValueRule, SaveActionRule, Suppressions
└── rules/               one example rule: surrounding-whitespace
```

A `FieldValueRule` only states what a field value should be; a `SaveActionRule` runs one of JabRef's Save Actions as a rule, under the formatter's key as its id, e.g. `normalize-page-numbers`.
A comment above an entry switches rules off for it, for one field or for all of them:

```bibtex
% jabfix-disable surrounding-whitespace author:page-ranges
@Article{knuth1984,
  ...
}
```

The CLI lives in JabKit: `jabkit jabfix [--check | --in-place] [--disable RULE,...] FILE`.
Rules run once each, in `RuleSet` order, and must be idempotent.
Layout is normalized by `BibDatabaseWriter`, so a library JabFix has already formatted produces no diff.

## Consolidation into JabFix

Options of integrating JabFix into JabRef.

### Context

The four features overlap, are configured separately, and split reporting from fixing.
A team cannot define its conventions once and have them applied everywhere.

### Options

1. Add JabFix as a fifth feature.
2. Keep the implementations, but read settings from one shared file.
3. Re-implement all four as JabFix rules (proposed).

Options 1 and 2 are smaller, but keep reporting and fixing in separate code.

### Pros

- One configuration, versioned with the project.
- Every rule can report and fix: Check integrity gains fixes, Save Actions gain a check mode.
- Checks can be disabled individually by id.
- Existing formatters, checkers and output writers can be wrapped rather than rewritten, as `SaveActionRule` does for Save Actions.

### Cons

- Large change across jablib, jabgui and jabkit (about 40 checkers, over 20 cleanup jobs); needs several pull requests.
- The rule API needs library-level scope and injected context.
- Some cleanup jobs are not about style (moving linked files, XMP metadata, biblatex conversion). Either the scope grows or the consolidation stays partial.
- Settings already stored in libraries and preferences need a precedence and a migration.
- One rule model has to serve an on-demand dialog, a silent step on save, and a list of messages.
- `jabkit check integrity` and `jabkit check consistency` need a deprecation path.
