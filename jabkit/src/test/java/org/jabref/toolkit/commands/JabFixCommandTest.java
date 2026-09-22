package org.jabref.toolkit.commands;

import java.util.List;

import org.jabref.logic.bibtex.FieldPreferences;
import org.jabref.toolkit.exception.CliExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class JabFixCommandTest extends AbstractJabKitTest {

    private String inputFile;

    @BeforeEach
    void setUpCommand() {
        // The launcher installs this; without it a usage error surfaces as a stack trace and exit code 70.
        commandLine.setExecutionExceptionHandler(new CliExceptionHandler(commandLine.getExecutionExceptionHandler()));
        when(preferences.getFieldPreferences()).thenReturn(new FieldPreferences(true, List.of(), List.of()));
        inputFile = getClassResourceAsFullyQualifiedString("jabfix-sloppy.bib");
    }

    @Test
    void everyRuleRunsByDefault() {
        assertEquals(CommandLine.ExitCode.OK, commandLine.executeToLog("jabfix", inputFile));

        String formatted = commandLine.getStandardOutput();
        assertTrue(formatted.contains("author = {Knuth, Donald E.},"), formatted);
    }

    @Test
    void disableSwitchesARuleOff() {
        assertEquals(CommandLine.ExitCode.OK,
                commandLine.executeToLog("jabfix", "--disable", "surrounding-whitespace", inputFile));

        String formatted = commandLine.getStandardOutput();
        assertTrue(formatted.contains("author = { Knuth, Donald E. },"), formatted);
    }

    /// Split into two ids, of which only the misspelled one is unknown.
    @Test
    void disableTakesACommaSeparatedList() {
        assertEquals(CommandLine.ExitCode.USAGE,
                commandLine.executeToLog("jabfix", "--disable", "surrounding-whitespace,surounding-whitespace", inputFile));

        String errors = commandLine.getErrorOutput();
        assertTrue(errors.contains("Unknown rule: surounding-whitespace."), errors);
    }

    /// A typo has to be reported, not passed over: otherwise the user believes a rule is off while
    /// it goes on running.
    @Test
    void anIdThatNamesNoRuleIsAUsageError() {
        assertEquals(CommandLine.ExitCode.USAGE,
                commandLine.executeToLog("jabfix", "--disable", "surounding-whitespace", inputFile));

        String errors = commandLine.getErrorOutput();
        assertTrue(errors.contains("surounding-whitespace"), errors);
        assertTrue(errors.contains("surrounding-whitespace"), errors);
    }

    @Test
    void checkNamesTheRuleBehindEveryFinding() {
        assertEquals(1, commandLine.executeToLog("jabfix", "--check", "-p", inputFile));

        String findings = commandLine.getStandardOutput();
        assertTrue(findings.contains("[surrounding-whitespace]"), findings);
    }

    @Test
    void inPlaceAndCheckCannotBeCombined() {
        assertEquals(CommandLine.ExitCode.USAGE,
                commandLine.executeToLog("jabfix", "--in-place", "--check", inputFile));
    }
}
