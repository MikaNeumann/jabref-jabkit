plugins {
    id("org.jabref.gradle.module")
    id("java-library")
}

testModuleInfo {
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
}
