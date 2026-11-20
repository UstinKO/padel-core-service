package com.padle.core.application.clubs;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ApplicationArchitectureTest {
    @Test
    void productionUsesOnlyOwnTypesJdkAndAgreedSpringAnnotations() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.padle.core.application");

        classes().should().onlyDependOnClassesThat().resideInAnyPackage(
                "java..", "com.padle.core.application..", "org.springframework.stereotype..",
                "org.springframework.transaction.annotation..").check(production);
    }
}
