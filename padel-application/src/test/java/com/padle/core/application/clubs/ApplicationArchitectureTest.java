package com.padle.core.application.clubs;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ApplicationArchitectureTest {
    @Test
    void productionUsesOnlyOwnTypesJdkAndAgreedSpringAnnotations() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.padle.core.application");

        classes().should().onlyDependOnClassesThat(resideInAnyPackage(
                "java..", "com.padle.core.application..", "org.springframework.transaction.annotation..")
                .or(equivalentTo(Service.class))).check(production);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .check(production);
    }
}
