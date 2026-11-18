package com.padle.core.padelcoreservice.service.americano;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class EngineArchitectureTest {

    @Test
    void engineProductionClassesDependOnlyOnJdkAndTheirOwnModule() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.padle.core.padelcoreservice");

        classes().should().onlyDependOnClassesThat().resideInAnyPackage(
                "java..", "com.padle.core.padelcoreservice.service.americano..")
                .check(production);
    }
}
