package com.padle.core.padelcoreservice.service.americano;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineArchitectureTest {

    @Test
    void engineProductionClassesDependOnlyOnJdkAndTheirOwnModule() {
        var production = productionClasses();
        boundary(production).check(production);
    }

    @Test
    void dependencyOnLegacyClassInTheSamePackageIsRejected() {
        var forbidden = new ClassFileImporter().importClasses(ForbiddenDependency.class);

        assertThatThrownBy(() -> boundary(productionClasses()).check(forbidden))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(LegacyServiceProbe.class.getName());
    }

    private JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.padle.core.padelcoreservice");
    }

    private ArchRule boundary(JavaClasses production) {
        return classes().should().onlyDependOnClassesThat(new DescribedPredicate<>(
                "принадлежат JDK или production-классам библиотеки движков") {
            @Override
            public boolean test(JavaClass target) {
                while (target.isArray()) {
                    target = target.getComponentType();
                }
                return target.getPackageName().startsWith("java.") || production.contain(target.getName());
            }
        });
    }

    private static class ForbiddenDependency {
        private LegacyServiceProbe legacy;
    }

    private static class LegacyServiceProbe {
    }
}
