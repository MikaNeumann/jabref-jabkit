module org.jabref.jabfix {
    exports org.jabref.jabfix;
    exports org.jabref.jabfix.rule;
    exports org.jabref.jabfix.rules;

    requires org.jabref.jablib;

    // Suppressions hands out a SetMultimap, so every reader of it needs Guava as well
    requires transitive com.google.common;
}
