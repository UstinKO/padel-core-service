package com.padle.core.application.clubs;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import java.util.List;
import com.tngtech.archunit.lang.ArchRule;
import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine;
import com.padle.core.padelcoreservice.service.americano.BoundaryLegacy;
import com.padle.core.padelcoreservice.service.americano.RoundRobinPointsStandingsEngineBackdoor;
import com.padle.core.padelcoreservice.dto.BoundaryDto;
import com.padle.core.padelcoreservice.model.BoundaryEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ApplicationArchitectureTest {
    static final Set<String> ENGINE_TYPES = Set.of(
            "RoundRobinSetStandingsEngine", "RoundRobinSetStandingsEngine$Team",
            "RoundRobinSetStandingsEngine$Match", "RoundRobinSetStandingsEngine$Ranked",
            "RoundRobinPointsStandingsEngine", "RoundRobinPointsStandingsEngine$Team",
            "RoundRobinPointsStandingsEngine$Ranked", "RoundRobinFinalOrderEngine",
            "RoundRobinFinalOrderEngine$Standing", "RoundRobinFinalOrderEngine$Match",
            "RoundRobinFinalOrderEngine$Ranked");

    static final DescribedPredicate<JavaClass> ENGINE = new DescribedPredicate<>("точные публичные типы engines") {
        @Override
        public boolean test(JavaClass type) {
            String name = type.getBaseComponentType().getName();
            String prefix = "com.padle.core.padelcoreservice.service.americano.";
            return name.startsWith(prefix) && ENGINE_TYPES.contains(name.substring(prefix.length()));
        }
    };
    @Test
    void productionUsesOnlyOwnTypesJdkAndAgreedSpringAnnotations() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.padle.core.application");

        allowedDependencies().check(production);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .check(production);
    }

    private ArchRule allowedDependencies() {
        return classes().should().onlyDependOnClassesThat(resideInAnyPackage(
                "java..", "com.padle.core.application..", "org.springframework.transaction.annotation..")
                .or(equivalentTo(Service.class)).or(ENGINE));
    }

    @Test
    void legacyNeighborSuffixAndGenericArrayRemainForbidden() {
        for (var fixture : List.of(LegacyReference.class, SuffixReference.class, GenericArrayReference.class, EntityArrayReference.class)) {
            assertThatThrownBy(() -> allowedDependencies().check(new ClassFileImporter().importClasses(fixture)))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void exactPublicEngineTypesAreAllowedButPrivateMeetingsAreNot() {
        var types = new ClassFileImporter().importPackages("com.padle.core.padelcoreservice.service.americano");
        for (var type : ENGINE_TYPES) {
            assertThat(ENGINE.test(types.get("com.padle.core.padelcoreservice.service.americano." + type))).isTrue();
        }
        assertThat(ENGINE.test(types.get(RoundRobinSetStandingsEngine.class.getName() + "$Meetings"))).isFalse();
    }

    @Test
    void onlyTheSharedCalculationPreparesCircleEngineInputs() {
        var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.padle.core.application");
        var standings = new DescribedPredicate<JavaClass>("типы SETS/POINTS engines") {
            @Override public boolean test(JavaClass type) {
                String name = type.getBaseComponentType().getName();
                return ENGINE.test(type) && (name.contains("RoundRobinSetStandingsEngine")
                        || name.contains("RoundRobinPointsStandingsEngine"));
            }
        };
        noClasses().that().resideInAnyPackage("com.padle.core.application.roundrobin..")
                .and().doNotHaveSimpleName("RoundRobinStandingsCalculation")
                .should().dependOnClassesThat(standings).check(production);
    }

    static class LegacyReference { BoundaryLegacy legacy; }
    static class SuffixReference { RoundRobinPointsStandingsEngineBackdoor backdoor; }
    static class GenericArrayReference { List<BoundaryDto[]> dtos; }
    static class EntityArrayReference { BoundaryEntity[] entities; }
}
